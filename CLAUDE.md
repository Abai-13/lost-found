# CLAUDE.md — 校园失物招领与 AI 智能匹配平台

> **本文件是协作规范**：定义项目全貌、技术选型理由，以及**改代码时不可破坏的约定**。
>
> - 📖 项目做了什么、跑出什么数字 → [README.md](./README.md)
> - 📐 模块级设计与取舍的完整记录 → [docs/technical-notes.md](./docs/technical-notes.md)
> - 🧪 可复现的评测 / 压测材料 → [eval/](./eval/) · [bench/REPORT.md](./bench/REPORT.md)

## 项目简介

校园失物招领平台：Spring Boot 3 单体应用 + Vue 3 前后端分离。
核心亮点是 **AI 失物匹配** —— 用户用自然语言描述丢失的东西（「我的黑色苹果手机不见了」），
系统从招领池里召回候选、交给大模型精排，返回结构化结果与匹配理由。

后端、前端、评测脚本、压测脚本均为独立完成。本仓库从骨架到压测、评测全程有 Claude Code 协作参与，
本文件即这套协作规范的一部分。

> ⚠️ **以下文件仅存在于本地，已在 `.gitignore`，不随仓库发布**：
> `docs/learning-log.md`（学习进度与练习记录）、`.run/`（本机 IDEA 运行配置）、
> `application-local.yml`（数据库凭据）、`bench/*.jtl`（压测原始日志，单轮 100MB+）。
> **提交前先扫一眼 `git status`。**

## 技术栈

| 组件 | 版本 | 为什么选它 |
|------|------|-----------|
| Java | 17 | LTS 版本，Spring Boot 3 的最低要求 |
| Spring Boot | 3.3.3 | 主流稳定 3.x，自动配置降低样板 |
| MyBatis-Plus | 3.5.7 | 通用 CRUD 不写 SQL，复杂查询仍可手写；`LambdaQueryWrapper` 有编译期字段检查 |
| MySQL | 8.x | 最通用的关系型数据库 |
| Redis + Spring Cache | — | 热点读缓存；注解式接入，带故障降级 |
| jjwt | 0.12.6 | JWT 新 API，修复了旧版安全漏洞 |
| BCrypt | spring-security-crypto | 只要密码单向加密，不引入完整的 Spring Security |
| Hutool | 5.8.32 | 常用工具类 |
| Lombok | — | 减少样板代码 |
| 大模型 API | OpenAI 兼容接口 | 供应商通过配置切换，不写死在代码里 |
| Vue 3 + Element Plus + Vite | — | 前端，组合式 API |
| JMeter | — | 压测（非 GUI 模式） |

**两个被明确拒绝的方案**：

- **不用 Spring Security** —— 项目只需要 JWT 登录认证，不需要角色权限 / OAuth2 / RememberMe。
  只引 `spring-security-crypto` 做 BCrypt，认证交给手写拦截器，代码量少、链路透明。
- **不上微服务** —— 校园场景用户量在千~万级，单体 + 水平扩展足够。过早微服务会引入分布式事务、
  服务发现、网络开销，ROI 不成立。

## 构建与运行

```bash
# 后端（需先启动 MySQL；Redis 可选 —— 连不上会自动降级直查数据库）
mvn clean compile                  # 编译
mvn test                           # 运行测试
mvn spring-boot:run                # 启动 → http://localhost:8080
mvn clean package -DskipTests      # 打包

# 前端
cd frontend && npm install && npm run dev    # → http://localhost:5173
```

数据库凭据通过环境变量 `MYSQL_USERNAME` / `MYSQL_PASSWORD`，或本地文件 `application-local.yml`（已 gitignore）注入。
大模型密钥走 `DEEPSEEK_API_KEY` 环境变量。**完整启动步骤见 [README.md](./README.md)。**

> ⚠️ **后端正在运行的时候不要 `mvn clean`** —— 会把 JVM 赖以加载的 `target/classes` 抽走。

## 项目结构

```
lost-found/
├── src/main/java/com/lostfound/
│   ├── common/                          # Result · ResultCode · BusinessException
│   │                                    # GlobalExceptionHandler · RetrySupport
│   ├── config/                          # JwtConfig / JwtUtil · WebConfig
│   │                                    # LlmConfig · EmbeddingConfig · RedisConfig
│   │                                    # MybatisPlusConfig · MyMetaObjectHandler
│   │                                    # GracefulCacheErrorHandler · RateLimitInterceptor
│   ├── controller/                      # UserController · ItemController · AiController
│   ├── service/                         # User · Item · Ai · File
│   │   ├── CandidateService             # 候选召回（2-gram 覆盖率打分）
│   │   ├── EmbeddingService             # 向量化抽象（已实现，未接入召回链路）
│   │   └── impl/                        # 各 Service 实现
│   ├── mapper/  entity/  dto/           # 数据访问 / 表映射 / 请求响应对象
│   └── interceptor/JwtInterceptor.java  # JWT 鉴权
├── src/test/                            # 24 个测试（10 个 MockMvc 接口测试 + 14 个单测）
├── frontend/                            # Vue 3：Login / ItemList / ItemDetail / Publish / AiChat / Profile
├── eval/                                # AI 匹配评测：146 条数据集 + 生成器 + 脚本 + 各阶段报告
├── bench/                               # JMeter 压测计划 + REPORT.md
├── docs/technical-notes.md              # 36 条项目技术笔记
└── sql/schema.sql                       # 建表脚本（含索引）
```

## 分层与调用链

```
浏览器（Vue 3 SPA）
  │  Axios 请求拦截器自动带 Authorization: Bearer <token>
  ▼
JwtInterceptor          ← 鉴权（规则见下节）
RateLimitInterceptor    ← 只拦 /api/ai/**，每用户每分钟默认 5 次
  ▼
Controller（接收请求、@Valid 参数校验）
  ▼
Service（业务逻辑、事务、缓存注解）
  ▼
Mapper（MyBatis-Plus BaseMapper / LambdaQueryWrapper）
  ▼
MySQL 8（Redis 作为缓存层旁挂在 Service 之上）
```

## API 接口

| 方法 | 路径 | 需登录 | 说明 |
|------|------|:---:|------|
| POST | `/api/user/register` | — | 注册 |
| POST | `/api/user/login` | — | 登录，返回 JWT token |
| GET | `/api/user/me` | ✅ | 当前用户信息 |
| POST | `/api/item` | ✅ | 发布物品（multipart：`data` + 可选 `image`） |
| GET | `/api/item` | — | 物品列表（分页 + 筛选 + 排序） |
| GET | `/api/item/{id}` | — | 物品详情 |
| GET | `/api/item/my` | ✅ | 我的发布 |
| PUT | `/api/item/{id}/status` | ✅ | 修改物品状态（仅发布者） |
| POST | `/api/ai/chat` | ✅ | AI 问答 |
| POST | `/api/ai/query` | ✅ | **AI 物品匹配**（2-gram 召回 + 大模型精排） |

> 📌 表里「需登录」是**语义**上的要求。`GET /api/user/me` 标 ✅ 是因为没有 token 就取不到 `userId`；
> 但从拦截器角度看，它和所有 GET 一样会被放行，由 Service 层决定拿不到用户时怎么处理。

**认证方式：** 登录后拿到 token，请求头加 `Authorization: Bearer <token>`。

## 鉴权规则（**按 HTTP 方法分，不按接口分**）

写在 `JwtInterceptor`：

- **GET 一律放行**，token 可选 —— 带了就解析出 `userId` 放进 request（供「我的发布」「当前用户」用），
  没带也放行：游客可以公开浏览。
- **POST / PUT / DELETE 必须有有效 token**，否则 401。
- 只有 `/api/user/register` 和 `/api/user/login` 被排除在拦截器之外。
- 副作用：`/api/ai/chat` 和 `/api/ai/query` 虽然是「查询语义」，但因为是 POST，**同样需要登录**。

**为什么这么设计**：「浏览公开、写操作必须认证」这条规则天然正确，且**新增接口时不会漏配导致越权** ——
逐个接口配置白名单，漏配一个就是一个越权漏洞。

**越权的第二层防护在 Service**：改物品状态时，查出物品的 `userId` 和当前登录用户比对，不一致抛 403。
`JwtInterceptor` 只回答「你是谁」，Service 才回答「这东西是不是你的」——
**只有拦截器就是任何人登录后都能改别人的物品**。

## 数据库

数据库名 `lost_found`（首次启动 `createDatabaseIfNotExist=true` 自动创建）。

### user 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| username | VARCHAR(50) UNIQUE | 登录用户名 |
| password | VARCHAR(255) | BCrypt 加密 |
| nickname | VARCHAR(50) | 显示昵称 |
| phone | VARCHAR(20) | 手机号 |
| avatar_url | VARCHAR(500) | 头像 |
| role | VARCHAR(20) | USER / ADMIN |
| created_at / updated_at | DATETIME | 自动填充 |

### item 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| user_id | BIGINT | 发布者（**无物理外键**，见下） |
| title | VARCHAR(100) | 物品标题 |
| type | VARCHAR(10) | LOST（寻物）/ FOUND（招领） |
| category | VARCHAR(30) | 电子产品 / 证件 / 衣物 / 书籍 / 其他 |
| location | VARCHAR(200) | 丢失或拾获地点 |
| description | TEXT | 详细描述 |
| image_url | VARCHAR(500) | 图片路径 |
| contact | VARCHAR(100) | 联系方式 |
| status | VARCHAR(20) | UNCLAIMED / CLAIMED |
| **version** | INT | **乐观锁字段**（并发认领） |
| created_at / updated_at | DATETIME | 自动填充 |

**索引**：`idx_created_at`（分页默认按时间倒序）、`idx_type`、`idx_category`、`idx_status`、`idx_user_id`。

- **不做物理外键**：高并发下外键约束要额外检查、拖慢写入，分库分表时也会成为阻碍；
  靠 `idx_user_id` 加速查询、靠 Service 层保证逻辑一致性。
- `created_at` / `updated_at` 由 MyBatis-Plus 的 `MyMetaObjectHandler` 自动填充。

---

## 🚫 不可破坏的约定（**改代码前必读**）

**架构**

1. 鉴权按 HTTP 方法分，**不按接口分** —— 不要改成「新增接口时逐个配白名单」，那是在制造越权漏洞。
2. 越权检查在 Service 层；拦截器只负责「你是谁」。
3. 所有接口返回统一 `Result`，前端只判 `code === 200`。不要某个接口直接返回裸对象。
4. 异常分类处理，**客户端错不能报成 500**。客户端数据非法（如坏 JSON）应返回 400 + 明确提示。

**缓存**

5. 写操作必须**成对清缓存**（`@Caching(evict=...)`）。尤其 `foundPool`：它是 AI 召回的候选全集，
   **不清就会「发布了新物品但 AI 永远召不到」**。
6. Redis 挂了必须**静默降级直查数据库**（缓存是性能组件，不是正确性组件）。
7. **但静默 ≠ 不管**：被吞掉的异常必须可观测。加任何降级逻辑时，先想清楚「它坏了怎么知道」。

**AI 链路**

8. 召回用的文本字段必须 **= 喂给大模型的字段**，两边不一致等于白做。
9. 大模型返回值不可信：`itemId` 不在候选列表里的**直接丢弃**（幻觉防护）；
   `title / category / location` 一律用本地数据回填。**模型只负责判断「哪条最像」。**
10. **上游超时必须大于下游最坏耗时**（前端 30s / 后端 `read-timeout 30s × (1+2 次重试)` 就是反例）。
11. 失败必须可区分：降级响应带 `degradeReason`，**别让「调用失败」和「没匹配上」长得一样** ——
    混在一起会让基于这批数据的任何统计结论都失真。
12. 重试只对**超时 / 5xx / 429**；401（密钥错）、400（参数错）不重试。重试逻辑只有 `RetrySupport` 一个出处。

**数据库**

13. 深分页走**延迟关联**（先查 id 再回表）；`selectBatchIds` **不保证返回顺序，必须按原 id 顺序还原**。
14. `item.version` 是乐观锁字段，更新走 `@Version`，**别用 `updateById` 绕过去**。
15. 新增查询条件加进 `buildQueryWrapper`，**别在 page / myPage / list 三处各写一遍**。

**改动纪律**（最容易违反，也最贵）

16. **一次只改一个变量** —— 否则分不清是谁的功劳（prompt 瘦身和召回重构一起做，就永远说不清谁贡献了什么）。
17. **先量基线再优化**，没有基线的优化等于没有优化。
18. 改完用**同一套评测 / 压测复核，看命中率和降级率，不是看「接口没报错」**。
19. **换任何东西之前先验证** —— 连把一个「已弃用」的模型名换成新的，都能带出一个空结果的 bug。

---

## 设计决策

- **为什么统一返回体 `Result`？** 前端只需判断 `code === 200`；异常不靠 HTTP 500 返回给前端，
  由 `GlobalExceptionHandler` 统一拦截转换，前端处理一致。
- **为什么 API Key 放环境变量？** 防止密钥泄露进 Git；配置文件里只有占位符。
- **为什么 JWT 不用 Redis Session？** 前后端分离、不依赖 Redis 部署更简单、校园场景不需要实时踢人下线。
  需要主动失效时可在 Redis 维护黑名单补充。
- **为什么密码用 BCrypt 而不是 MD5？** 每次加密随机 22 位盐（同一密码两次密文不同，彩虹表作废）；
  算法故意设计得慢（单次约 0.1s），字典爆破代价高。盐嵌在密文里，不单独存。
- **为什么登录失败提示统一为「用户名或密码错误」？** 分开提示会被用来撞库筛选真实用户名。
- **为什么并发认领用乐观锁不用悲观锁？** 认领是低冲突场景。乐观锁不加锁、不阻塞；
  悲观锁（`SELECT ... FOR UPDATE`）会持有行锁，在低冲突场景纯属浪费。冲突则重试最多 3 次。
- **为什么分页用延迟关联，而不是再加一个索引？** 索引早就有（`idx_created_at`），
  问题是**优化器主动放弃了它** —— 根因是回表成本（`offset + size` 次随机 IO），
  所以解法是降低回表次数，而不是再建一个索引。
- **为什么缓存挂了不报错，而是静默降级直查 DB？** 见上方约定 6、7 ——
  **降级保证可用性是对的，但把失败伪装成正常结果是错的。**
- **为什么匹配用 2-gram 不用分词 / 不用向量？** 分词要引 HanLP/jieba 依赖，且对错别字和新词很脆；
  2-gram 零依赖、对新词鲁棒。向量能力已实现但**未接入召回链路**（2-gram 召回率已达 95.2%，
  且向量探针实测正确答案与错误答案只差 0.0245 —— 分不出阈值，只能做召回不能拍板）。

更完整的取舍记录见 [docs/technical-notes.md](./docs/technical-notes.md)。

---

## 已知边界

**以下这些是有意为之或已知局限，不要为了「看起来完整」而假装它们不存在。**

| # | 事实 |
|---|---|
| 1 | **Docker 配置已写好但未在真实环境验证过** —— `Dockerfile`（多阶段构建）+ `docker-compose.yml`（三服务 + healthcheck + 数据卷）确实存在且规范，但没有跑过容器，因此也就没有「镜像体积压到多少」这类数字 |
| 2 | **向量能力未接入召回链路** —— `EmbeddingService` 抽象 + 云端 `bge-m3` 实现已写好并有单测，但召回仍走 2-gram |
| 3 | **没有集成测试** —— 只有单测 + MockMvc（Mock 掉 Service/Mapper），没有真连数据库跑完整链路 |
| 4 | **限流器是单机内存实现** —— `ConcurrentHashMap` 的 key 从不清理，长时间运行会缓慢泄漏；多实例部署时各算各的 |
| 5 | **`COUNT(*)` 未解决** —— 平均 41ms、占 SQL 总时间 54%。可选方案：缓存计数结果，或前端改成「加载更多」不做总数 |
| 6 | **缓存 TTL 固定 5 分钟**，无随机抖动，理论上存在缓存雪崩风险（当前量级可接受） |
| 7 | **2-gram 在十万级会不够用** —— 现在每次请求对全量候选重切 2-gram（1000 条约几毫秒），量级上去需要预建索引或换检索引擎 |
| 8 | **本地文件存储不可水平扩展** —— 图片存本地磁盘，生产要换 OSS（代码里是 `FileService`，换实现即可） |
| 9 | **压测机是本机** —— JMeter / 后端 / MySQL / Redis 同一台笔记本，绝对数字受此限制；只有「优化前后对比」是有效的 |
| 10 | **评测集是合成的** —— 由大模型反向改写生成，有人工痕迹，能发现量级差异（19% vs 94%），不够区分 1~2 个点的小改动 |
| 11 | **没有微服务 / 分库分表 / 消息队列** —— 按当前量级有意不引入 |

**迭代时间线**（AI 匹配评测、压测、各阶段报告）见 [README.md](./README.md) 的「开发日志」。
