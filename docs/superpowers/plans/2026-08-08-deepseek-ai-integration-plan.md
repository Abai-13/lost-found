# DeepSeek AI 接入 — 实现计划

> **For agentic workers:** 使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐个实现。步骤使用 `- [ ]` 复选框跟踪。

**目标：** 将 AI 问答接口从返回占位文字升级为真正调用 DeepSeek API，加上每人每分钟 5 次的限流保护。

**架构：** 新增 DeepSeekConfig（配置 RestTemplate 超时）和 RateLimitInterceptor（内存计数器限流），重写 AiServiceImpl（调用 API + 解析 JSON + 异常降级），修改 WebConfig（注册限流拦截器、AI 接口改为需登录）。不引入新依赖，全部使用 Spring 内置 + Hutool。

**技术栈：** Java 17, Spring Boot 3.3.3, RestTemplate, Hutool JSONUtil, ConcurrentHashMap

---

### Task 1: 添加 DeepSeek 配置到 application.yml

**文件：**
- 修改：`src/main/resources/application.yml`（在文件末尾追加）

- [ ] **Step 1: 在 application.yml 末尾追加 DeepSeek 配置**

```yaml
# DeepSeek AI 配置
deepseek:
  api-url: https://api.deepseek.com/v1/chat/completions
  api-key: ${DEEPSEEK_API_KEY:}
  model: deepseek-chat
  connect-timeout: 3
  read-timeout: 30
  max-tokens: 500
```

- [ ] **Step 2: 验证 — 确认 YAML 语法正确**

```bash
cd /d/workspace/lost-found && mvn validate
```

---

### Task 2: 创建 DeepSeekConfig 配置类

**文件：**
- 创建：`src/main/java/com/lostfound/config/DeepSeekConfig.java`

- [ ] **Step 1: 写出完整类**

```java
package com.lostfound.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * DeepSeek API 配置 — 绑定 application.yml 中的 deepseek 配置块，
 * 并创建带超时设置的 RestTemplate Bean。面试可讲：为什么自定义超时。
 */
@Configuration
@ConfigurationProperties(prefix = "deepseek")
@Data
public class DeepSeekConfig {

    /** DeepSeek API 地址 */
    private String apiUrl;

    /** API Key，从环境变量 DEEPSEEK_API_KEY 注入 */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 连接超时（秒） */
    private int connectTimeout;

    /** 读取超时（秒） */
    private int readTimeout;

    /** AI 回答最大 token 数 */
    private int maxTokens;

    /** 创建配置了超时的 RestTemplate */
    @Bean
    public RestTemplate deepseekRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        return new RestTemplate(factory);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /d/workspace/lost-found && mvn compile
```

**预期：** BUILD SUCCESS — DeepSeekConfig 编译通过，@ConfigurationProperties 自动绑定 yml 配置。

---

### Task 3: 重写 AiServiceImpl — 真正调用 DeepSeek API

**文件：**
- 修改：`src/main/java/com/lostfound/service/impl/AiServiceImpl.java`（完整重写）

- [ ] **Step 1: 写出完整实现**

```java
package com.lostfound.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lostfound.config.DeepSeekConfig;
import com.lostfound.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AI 服务实现 — 调用 DeepSeek API 进行真实问答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final DeepSeekConfig deepSeekConfig;
    private final RestTemplate deepseekRestTemplate;

    /** 系统提示：限制 AI 只回答失物招领相关问题 */
    private static final String SYSTEM_PROMPT =
            "你是校园失物招领助手，只回答校园失物招领、物品挂失、物品寻找相关的问题。" +
            "如果用户问不相关的问题，请礼貌地表示你只能回答失物招领相关问题。" +
            "回答尽量简洁，不超过 200 字。";

    @Override
    public String chat(String question) {
        log.info("AI 问答请求: {}", question);

        // ① 构建请求体 JSON
        JSONObject requestBody = buildRequestBody(question);

        // ② 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekConfig.getApiKey());

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);

        // ③ 发送请求 + 异常降级
        try {
            ResponseEntity<String> response = deepseekRestTemplate.postForEntity(
                    deepSeekConfig.getApiUrl(),
                    requestEntity,
                    String.class
            );

            // ④ 检查 HTTP 状态码
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("DeepSeek API 返回异常状态码: {}", response.getStatusCode());
                return "AI 服务异常，请联系管理员";
            }

            // ⑤ 解析返回 JSON，提取 answer
            return extractAnswer(response.getBody());

        } catch (RestClientException e) {
            // 连接超时、读取超时、网络异常统一在这里处理
            log.error("调用 DeepSeek API 失败", e);
            return "AI 服务繁忙，请稍后重试";
        }
    }

    /** 构建 DeepSeek API 请求体 */
    private JSONObject buildRequestBody(String question) {
        JSONObject body = new JSONObject();
        body.set("model", deepSeekConfig.getModel());
        body.set("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", question)
        ));
        body.set("temperature", 0.7);
        body.set("max_tokens", deepSeekConfig.getMaxTokens());
        return body;
    }

    /** 从 DeepSeek 返回的 JSON 中提取回答内容 */
    private String extractAnswer(String responseBody) {
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            // 路径: choices[0].message.content
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");
        } catch (Exception e) {
            log.error("解析 DeepSeek 返回 JSON 失败: {}", responseBody, e);
            return "AI 返回数据解析失败，请稍后重试";
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /d/workspace/lost-found && mvn compile
```

**预期：** BUILD SUCCESS — AiServiceImpl 编译通过。

---

### Task 4: 创建限流拦截器 RateLimitInterceptor

**文件：**
- 创建：`src/main/java/com/lostfound/config/RateLimitInterceptor.java`

- [ ] **Step 1: 写出完整类**

```java
package com.lostfound.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单限流拦截器 — 基于内存计数器。
 * <p>
 * 每个用户每分钟最多 5 次请求。
 * 如果未来需要分布式部署，可将 ConcurrentHashMap 替换为 Redis incr + expire。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 每个用户每分钟最大请求数 */
    private static final int MAX_REQUESTS_PER_MINUTE = 5;

    /** 限流窗口（毫秒） */
    private static final long WINDOW_MS = 60_000;

    /** userId → 请求时间列表 */
    private final ConcurrentHashMap<Long, List<Long>> requestLog = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 从 JWT 拦截器设置的 attribute 中取出 userId
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            // 理论上不会发生（JWT 拦截器已校验），兜底放行
            return true;
        }

        Long userId = (Long) userIdAttr;
        long now = System.currentTimeMillis();

        // 原子操作：获取或创建该用户的记录列表
        List<Long> timestamps = requestLog.computeIfAbsent(userId, k -> new ArrayList<>());

        synchronized (timestamps) {
            // ① 清理超过 1 分钟的旧记录
            timestamps.removeIf(t -> now - t > WINDOW_MS);

            // ② 判断是否超过限制
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                log.warn("用户 {} 触发限流，1 分钟内请求 {} 次", userId, timestamps.size());
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(429); // Too Many Requests
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null,\"timestamp\":" + now + "}"
                );
                return false;
            }

            // ③ 记录本次请求时间
            timestamps.add(now);
        }

        return true;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /d/workspace/lost-found && mvn compile
```

**预期：** BUILD SUCCESS — RateLimitInterceptor 编译通过。

---

### Task 5: 修改 WebConfig — 注册限流拦截器 + AI 接口需登录

**文件：**
- 修改：`src/main/java/com/lostfound/config/WebConfig.java`

**修改内容：**
1. 注入 `RateLimitInterceptor`
2. 注册限流拦截器，仅拦截 `/api/ai/**`
3. 从 JWT 排除列表中移除 `/api/ai/chat`（现在需要登录才能用 AI）

- [ ] **Step 1: 用 Edit 完成修改**

**改动一：在 JWT 排除列表中删除 `/api/ai/chat`**

```java
// 改前：
.excludePathPatterns(
        "/api/user/register",
        "/api/user/login",
        "/api/ai/chat"      // AI 问答
);

// 改后：
.excludePathPatterns(
        "/api/user/register",
        "/api/user/login"
);
```

**改动二：在类的声明处增加 RateLimitInterceptor 注入，在 addInterceptors 方法末尾增加限流拦截器注册**

最终 WebConfig.java 如下：

```java
package com.lostfound.config;

import com.lostfound.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置 — CORS + 拦截器注册。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    /** CORS — 开发阶段允许所有来源 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /** 注册拦截器 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 拦截器 — 除注册和登录外都拦截
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/register",
                        "/api/user/login"
                );

        // 限流拦截器 — 仅拦截 AI 接口（在 JWT 之后执行）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/ai/**");
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /d/workspace/lost-found && mvn compile
```

**预期：** BUILD SUCCESS。

---

### Task 6: 修改 AiController — 添加用户信息日志

**文件：**
- 修改：`src/main/java/com/lostfound/controller/AiController.java`

- [ ] **Step 1: 添加日志打印 userId**

```java
package com.lostfound.controller;

import com.lostfound.common.Result;
import com.lostfound.dto.ChatRequest;
import com.lostfound.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 问答控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** AI 问答（需登录） */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@Valid @RequestBody ChatRequest request,
                                             HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("用户 {} 发起 AI 问答: {}", userId, request.getQuestion());
        String answer = aiService.chat(request.getQuestion());
        return Result.ok(Map.of("answer", answer));
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /d/workspace/lost-found && mvn compile
```

**预期：** BUILD SUCCESS。

---

### Task 7: 全量编译 + 打包验证

- [ ] **Step 1: 完整编译打包**

```bash
cd /d/workspace/lost-found && mvn clean package -DskipTests
```

**预期：** BUILD SUCCESS — target 目录生成 `lost-found-1.0-SNAPSHOT.jar`。

- [ ] **Step 2: Git 提交**

```bash
cd /d/workspace/lost-found && git add -A && git commit -m "feat: 接入 DeepSeek AI 大模型 + 限流保护"
```

---

### Task 8: 启动验证 — 手动测试

- [ ] **Step 1: 设置环境变量**

在终端中设置 DeepSeek API Key（需要先去 https://platform.deepseek.com 注册获取）：
```bash
export DEEPSEEK_API_KEY=sk-你的真实key
```

- [ ] **Step 2: 启动应用**

```bash
cd /d/workspace/lost-found && mvn spring-boot:run -DskipTests
```

- [ ] **Step 3: 测试 — 先登录获取 token**

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}'
```

```bash
curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}'
```

从返回的 JSON 中复制 `data.token` 的值，记为 `<TOKEN>`。

- [ ] **Step 4: 测试 — 调用 AI 问答**

```bash
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"question":"我的校园卡丢了怎么办"}'
```

**预期返回：**
```json
{"code":200,"message":"成功","data":{"answer":"如果你的校园卡丢失，建议..."},"timestamp":...}
```

- [ ] **Step 5: 测试 — 不带 token 应返回 401**

```bash
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"测试"}'
```

**预期：** HTTP 401。

- [ ] **Step 6: 测试 — 限流（连续发送 6 次请求，第 6 次应被拒绝）**

```bash
for i in 1 2 3 4 5 6; do
  echo "=== 第 $i 次 ==="
  curl -s -X POST http://localhost:8080/api/ai/chat \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <TOKEN>" \
    -d '{"question":"测试"}'
  echo ""
done
```

**预期：** 第 6 次返回 `{"code":429,"message":"请求过于频繁，请稍后重试",...}`。

---

### Task 9: 更新 CLAUDE.md 学习进度 + 面试手册

- [ ] **Step 1: 在 CLAUDE.md 学习进度表中更新**

将：
```
| — | AI 大模型接入 | ⬜ 待开发 | — |
```
改为：
```
| 08-08 | ✅ AI 大模型接入：DeepSeek API + RestTemplate + 限流拦截器 + 降级处理 | 🟢 完成 | ✅ 能讲 |
```

- [ ] **Step 2: 在 interview-qa.md 末尾追加本章面试题**

以下内容追加到 `docs/interview-qa.md`：

```markdown

## 十二、AI 大模型接入（08-08 完成）

### Q32: 项目里怎么接入大模型的？从前端发请求到拿到 AI 回答，数据怎么流的？

**答案：**
> "前端 POST 到 /api/ai/chat，经过 JWT 拦截器验身份、限流拦截器查次数，然后到 AiController。Controller 调用 AiService，Service 用 RestTemplate 发 POST 请求到 DeepSeek API，拿到返回 JSON 后从 choices[0].message.content 取出回答文本，返回给前端。异常时返回降级文案，不抛给用户。"

### Q33: 为什么要加超时控制？

**答案：**
> "大模型 API 响应可能很慢（几十秒），不设超时的话：一是请求线程一直阻塞，占用服务器资源；二是用户浏览器一直转圈，体验很差。我设了 3 秒连接超时和 30 秒读取超时，超时后返回'AI 服务繁忙，请稍后重试'。30 秒对 AI 生成来说足够，对用户来说不会干等太久。"

### Q34: 限流是怎么实现的？为什么用内存而不是 Redis？

**答案：**
> "用 ConcurrentHashMap 存每个用户最近 1 分钟的请求时间列表。每次请求先清理超过 60 秒的旧记录，再判断剩余条数是否达到 5 次上限。达到就返回 429，不调用 AI API。选内存的原因：校园场景用户量小，内存足够，不引入额外依赖。如果未来需要分布式部署，把 Map 换成 Redis 的 incr + expire，判断逻辑不变。"

### Q35: API Key 怎么保护？为什么不写在配置文件里？

**答案：**
> "用环境变量 `${DEEPSEEK_API_KEY}` 占位，真实 Key 在本地系统环境变量中。配置文件提交到 GitHub 时只有占位符，不会被扫到。两道防线：第一道是环境变量隔离，第二道是限流（即使 Key 泄露，每个用户每分钟最多 5 次，无法批量刷）。"

### Q36: 大模型接口挂了怎么办？

**答案：**
> "Service 层做了异常降级：连接超时、读超时、或者 API 返回非 200，都 catch 住返回友好提示，不抛给全局异常处理器。这样即使 DeepSeek 服务器挂了、网络不通了，用户看到的是'AI 服务繁忙，请稍后重试'，而不是 500 错误页。这是服务降级的基本思想。"
```

- [ ] **Step 3: 提交 CLAUDE.md 和面试手册的更新**

```bash
cd /d/workspace/lost-found && git add CLAUDE.md docs/interview-qa.md && git commit -m "docs: 更新学习进度 + AI 模块面试题"
```
