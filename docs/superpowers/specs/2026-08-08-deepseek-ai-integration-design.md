# DeepSeek 大模型接入 — 设计方案

> 日期：2026-08-08 | 状态：待实现

## 一、目标

将 AI 问答接口从"返回占位文字"升级为"真正调用 DeepSeek 大模型"，并加上安全保护。

## 二、数据流

```
用户浏览器
  │ POST /api/ai/chat  {"question": "我的校园卡丢了怎么办"}
  │ Header: Authorization: Bearer <jwt-token>    ← 现在需要登录
  ▼
JwtInterceptor
  │ 解析 token，取出 userId，放入 request.attribute
  ▼
RateLimitInterceptor（新建）
  │ 检查该 userId 最近 1 分钟请求次数
  │ ≥ 5 次 → 返回 "请求过于频繁，请稍后重试"（不调用 AI）
  │ < 5 次 → 放行
  ▼
AiController
  │ 接收 ChatRequest，调用 AiService.chat()
  ▼
AiServiceImpl
  │ ① 构建请求 JSON（system 提示 + user 问题）
  │ ② RestTemplate.postForObject() → DeepSeek API
  │ ③ 解析返回 JSON：choices[0].message.content
  │ ④ 超时/异常 → "AI 服务繁忙，请稍后重试"
  ▼
返回 Result { code: 200, data: { answer: "建议你..." } }
```

## 三、涉及文件

| 文件 | 动作 | 说明 |
|------|:---:|------|
| `application.yml` | 改 | 添加 deepseek 配置块（api-url, api-key, model, max-tokens, timeout） |
| `config/DeepSeekConfig.java` | 新建 | 创建带超时配置的 RestTemplate Bean |
| `config/RateLimitInterceptor.java` | 新建 | 内存计数器实现每人每分钟 5 次限流 |
| `service/impl/AiServiceImpl.java` | 重写 | 真正调用 DeepSeek API，解析返回 JSON |
| `controller/AiController.java` | 改 | 添加 `@RequestAttribute("userId")` 打印用户信息 |
| `config/WebConfig.java` | 改 | 注册限流拦截器、AI 聊天不再放行（改为需登录） |

## 四、DeepSeek API 调用细节

### 请求

```
POST https://api.deepseek.com/v1/chat/completions
Content-Type: application/json
Authorization: Bearer ${DEEPSEEK_API_KEY}
```

```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "system",
      "content": "你是校园失物招领助手，只回答校园失物招领、物品挂失、物品寻找相关的问题。如果用户问不相关的问题，请礼貌地表示你只能回答失物招领相关问题。回答尽量简洁，不超过 200 字。"
    },
    {
      "role": "user",
      "content": "用户的真实问题"
    }
  ],
  "temperature": 0.7,
  "max_tokens": 500
}
```

### 配置项设计

| 配置项 | 值 | 说明 |
|------|------|------|
| `deepseek.api-url` | `https://api.deepseek.com/v1/chat/completions` | DeepSeek API 地址 |
| `deepseek.api-key` | `${DEEPSEEK_API_KEY:}` | 从环境变量读取，默认空 |
| `deepseek.model` | `deepseek-chat` | 使用免费额度的模型 |
| `deepseek.connect-timeout` | 3 秒 | 连接超时 |
| `deepseek.read-timeout` | 30 秒 | 读取超时（AI 生成需要时间） |
| `deepseek.max-tokens` | 500 | 限制回答长度，省钱 |

### 响应解析

DeepSeek 返回的 JSON 结构：
```json
{
  "choices": [
    {
      "message": {
        "content": "如果校园卡丢失，建议你立即..."
      }
    }
  ]
}
```

解析方式：使用 Hutool 的 JSONUtil 或 Jackson ObjectMapper 从 JSON 中提取 `choices[0].message.content`。

## 五、限流拦截器

### 存储结构

内存 HashMap，键 = userId，值 = 该用户最近的请求时间列表。

### 判断逻辑

```
每次请求：
  ├─ 从列表里删除超过 60 秒的旧记录
  ├─ 剩余记录数 ≥ 5 → 拒绝，返回 "请求过于频繁"
  └─ 剩余记录数 < 5 → 放行，记录当前时间
```

### 设计理由

校园场景用户量小，内存足够。面试时可以说：如果未来需要分布式部署，把 Map 替换为 Redis 的 incr + expire，限流判断逻辑不变，只换存储层。

## 六、异常处理

| 异常场景 | 返回内容 |
|------|------|
| DeepSeek API 连接超时 | "AI 服务繁忙，请稍后重试" |
| DeepSeek API 读超时 | "AI 服务繁忙，请稍后重试" |
| DeepSeek 返回非 200 | "AI 服务异常，请联系管理员" |
| HTTP 客户端异常 | "AI 服务暂不可用" |

所有异常在 Service 层 catch，返回降级文案，不抛给 Controller。

## 七、安全防护清单

| 层级 | 措施 | 防什么 |
|------|------|------|
| 环境变量 | API Key 只在环境变量，不写在代码里 | GitHub 泄露 |
| JWT | /api/ai/chat 改为需登录 | 未注册用户刷 API |
| 限流 | 每人每分钟 5 次 | 已注册用户恶意刷 |
| 超时 | 连接 3s + 读取 30s | 请求卡死不释放 |
| 降级 | API 异常返回友好提示 | 依赖故障不雪崩 |

## 八、不做的

- 不做多轮对话（单轮问答够用，省钱）
- 不做 Redis 限流（当前内存方案够用）
- 不做用户级 token 配额（v1 不需要）
- 不做 WebClient 异步（同步够用，面试可讲）
