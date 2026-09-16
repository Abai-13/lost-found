# 压测报告 —— GET /api/item 深分页优化

> 2026-09-16 · JMeter 5.6.3 · MySQL 8.0 · 数据量 128,064 行
>
> **一句话**：压测发现「深分页让优化器放弃索引改走全表扫描」，用**延迟关联**把 SQL 从 542ms 降到 5.41ms（100 倍），端到端平均响应从 3038ms 降到 299ms（10 倍）。

---

## 一、环境与工具

| 项 | 值 |
|---|---|
| 压测工具 | JMeter 5.6.3（非 GUI 模式） |
| 目标接口 | `GET /api/item`（列表分页，最核心的读接口） |
| 数据量 | item 表 128,064 行（`created_at` 随机撒在近 180 天） |
| 压测机 | 本机（**JMeter / 后端 / MySQL / Redis 全在同一台机器上**，会互相抢 CPU） |
| 计划文件 | `bench/plan-hot.jmx`（固定参数）/ `bench/plan-cold.jmx`（随机分页） |

---

## 二、基线（优化前）

### 2.1 两组场景

| 组 | 参数 | 压到哪一层 | QPS | 平均 | P99 |
|---|---|---|---:|---:|---:|
| **热缓存** | `page=1` 固定 | Redis | **15,427** | 3 ms | 5 ms |
| **冷缓存** | `page` 随机 | MySQL | **19.4** | 2,411 ms | 3,705 ms |

**Redis 侧复核**：热缓存组 926,061 个请求，`keyspace_hits` 增加 926,060、`keyspace_misses` 只增加 **1** —— 求证了「这组压的是 Redis，不是数据库」。

> ⚠️ **教训 1：随机参数 ≠ 冷缓存。**
> `page` 随机 1~2000 时，缓存 key 空间只有 2000 个，请求一旦超过这个数，后续全部命中缓存。
> **必须用 Redis 的 `keyspace_hits/misses` 来验证「到底压到了哪一层」，不能靠参数猜。**

### 2.2 瓶颈定位

慢查询（`performance_schema`，按总耗时排序）：

| SQL | 次数 | 平均耗时 | **平均扫描行数** |
|---|---:|---:|---:|
| 分页主查询 `SELECT id,user_id,title,...` | 955 | **608.6 ms** | **122,667** |
| `SELECT COUNT(*) AS total FROM item` | 958 | 43.5 ms | — |

**分页主查询平均扫 12.2 万行，而全表只有 12.8 万 —— 它在全表扫描。**

EXPLAIN 对比：

```sql
-- 浅分页 LIMIT 0,10
type: index    key: idx_created_at    rows: 10       Extra: Backward index scan   ✅

-- 深分页 LIMIT 10000,10
type: ALL      key: NULL              rows: 125571   Extra: Using filesort        ❌
```

**根因**：走 `idx_created_at` 需要回表 `offset + size` 次（offset 上万 = 上万次随机 IO），
而 `SELECT *` 必须回表才能拿到所有列。优化器算完成本，改选了「全表扫描 + filesort」。

---

## 三、优化 A：延迟关联（Deferred Join）

### 3.1 做法

把一条 SQL 拆成两步：

```java
// ① 只查 id —— idx_created_at 的叶子节点里就存着 id，是覆盖索引，不回表
wrapper.select(Item::getId);
Page<Item> idPage = itemMapper.selectPage(page, wrapper);

// ② 只对这 size 个 id 回表取完整行
List<Long> ids = idRecords.stream().map(Item::getId).toList();
itemMapper.selectBatchIds(ids);
```

**为什么能骗过优化器**：优化器拒绝索引，是因为「回表太贵」。而子查询只取 `id`，
`idx_created_at` 的叶子节点（created_at, id）本身就是覆盖索引，**不需要回表** ——
所以哪怕 `LIMIT 120000,10` 它也愿意走索引。

**回表次数：`offset + size` → `size`。**

代码位置：`ItemServiceImpl#selectPageByDeferredJoin`

### 3.2 SQL 层效果（EXPLAIN ANALYZE 实测）

| | 优化前 | 优化后 |
|---|---|---|
| 执行计划 | **Table scan + Sort** | **Covering index scan (reverse)** |
| 扫描行数 | **128,064** | **10,010** |
| 排序 | filesort **125,571 行** | **无**（索引天然有序） |
| cost | 13,094 | **123** |
| **实际耗时** | **542 ms** | **5.41 ms** |

**提速 100 倍**，且**排序整个消失**（覆盖索引本身就是按 created_at 排好的）。

### 3.3 端到端效果

**公平对比条件**：两轮都停用缓存效果（`page` 随机 1~12800 覆盖全部 12807 页），
**并用 Redis 命中率复核**，确认两轮都约 100% 打到数据库。

| 指标 | 优化前 | 优化后 | 提升 |
|---|---:|---:|---:|
| Redis 未命中率 | 100% | 94.6% | ✅ 可比 |
| **平均响应** | **3,038 ms** | **301 ms** | **10.1×** |
| **QPS** | **11.3** | **123.3** | **10.9×** |
| Max | 4,538 ms | 938 ms | 4.8× |
| 错误率 | 0% | 0% | — |

> ⚠️ **教训 2：SQL 快了 ≠ 接口快了。**
> SQL 层是 100 倍，端到端只有 10 倍。差额来自 `COUNT(*)`（41ms）+ 回表 + 序列化 + 连接池等固定开销。
> **压测要分层看（SQL 层 / 接口层），只测一头会得出错误结论。**

---

## 四、优化 B：页码上限（工程止损）

```java
private static final int MAX_PAGE = 200;   // 越界页码直接夹到边界
```

**理由**：延迟关联是治标，**深分页这个场景本身就不该存在** ——
失物招领不可能有人翻到第 2000 页。提前夹住，避免极端参数打穿数据库。

**验证**：`page=5000` → 返回与 `page=200` 完全一致的记录；`page=0` → 等同 `page=1`。

---

## 五、试过但**无效**的改动（同样是结论）

### 连接池 10 → 50

| | QPS | 平均 |
|---|---:|---:|
| 连接池默认 10 | 124.5 | 299 ms |
| 连接池调到 50 | 124.3 | 299 ms |

**没有任何变化。**

**为什么**：50 并发下，单请求 SQL 合计 77ms，需要的连接数 = `124 QPS × 0.077s ≈ 9.5` ——
**10 个连接本来就够用**，它一直在满负荷工作，而不是把请求堵在门外。

（配置保留 `maximum-pool-size: 50`：并发梯度测试显示系统在 25 并发时能跑到 252 QPS，
那时需要约 19 个连接，10 个才会成为真瓶颈。但**「它当前不是瓶颈」这件事本身要写清楚**。）

> 💡 **这段比"我优化了连接池"更有价值** —— 它证明的是「先测量、再动手」，而不是照着经验帖调参。

---

## 六、并发梯度 —— 系统的最佳工作点

优化后，同一接口在不同并发下的表现：

| 并发 | 响应时间 | **QPS** | 说明 |
|---:|---:|---:|---|
| 1 | 27 ms | 35.3 | 单请求基线 |
| 10 | 39 ms | **195.8** | 接近线性扩展 |
| **25** | **74 ms** | **252.2** | ⭐ **吞吐峰值** |
| 50 | 299 ms | **124.3** | ⚠️ **过载，QPS 腰斩** |

**结论**：系统在 **25 并发**附近达到最大吞吐（**252 QPS**）；超过之后请求开始排队、
上下文切换开销上升，**50 并发时吞吐反而掉一半**。

> **注意**：JMeter、后端、MySQL、Redis 跑在同一台笔记本上，绝对数字受此限制。
> 但**优化前后的对比是有效的**（两轮条件一致）。

---

## 七、还没解决的

| 问题 | 数据 | 说明 |
|---|---|---|
| **`COUNT(*)` 平均 41.38 ms** | 占 SQL 总时间的 **54%** | MyBatis-Plus 分页插件为返回 `total` 每次都要数一遍 12.8 万行。可缓存 COUNT 结果，或前端改为「加载更多」不做总数 |
| 延迟关联第一步 35.53 ms | 平均扫 64,810 行 | 深分页本身的开销，靠方案 B 的页码上限兜住 |
| 缓存 TTL 固定 5 分钟 | — | 无随机抖动，理论上存在缓存雪崩风险（量小可接受） |

---

## 八、方法论备忘（本轮踩过的坑）

1. **随机参数不等于冷缓存** —— 必须用 Redis 的 `keyspace_hits/misses` 复核压到了哪一层
2. **停掉 Redis 来制造 100% 未命中是错的** —— Lettuce 会挂起等 `command timeout`（配置 3000ms，
   读+写两次 = 6 秒），**测出来的是 Redis 超时，不是数据库**
3. **对比必须同条件** —— 两轮的缓存命中率不同，QPS 差 656 倍也不能当结论
4. **分层看性能** —— SQL 层 100 倍 ≠ 端到端 100 倍
5. **试过没效果的改动也是结论** —— 连接池那条比"我优化了连接池"更有说服力

---

## 附：复现步骤

```bash
# ① 起 Redis + 后端
cd /d/tools/redis && ./redis-server.exe redis.conf &
cd /d/workspace/lost-found && RATE_LIMIT_AI_MAX_REQUESTS=1000 mvn spring-boot:run

# ② 压测（先看 Redis 命中率，确认压到了哪一层）
cd /d/workspace/lost-found/bench
/d/tools/redis/redis-cli.exe flushdb
/d/tools/apache-jmeter-5.6.3/bin/jmeter.bat -n -t plan-cold.jmx -l result.jtl -Jthreads=25 -Jduration=10

# ③ 看 SQL 层耗时分布
mysql -u root -p -e "SELECT LEFT(DIGEST_TEXT,50), COUNT_STAR,
  ROUND(AVG_TIMER_WAIT/1000000000,2) AS avg_ms FROM performance_schema.events_statements_summary_by_digest
  WHERE SCHEMA_NAME='lost_found' ORDER BY SUM_TIMER_WAIT DESC LIMIT 5;"
```
