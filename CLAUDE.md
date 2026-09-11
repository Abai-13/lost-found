# CLAUDE.md — 校园失物招领 + AI 问答平台

## 项目简介

一个 Spring Boot 3 单体应用，技术栈务实、代码量可控。每个技术选型的取舍理由都记录在下方「设计决策」一节。

## 技术栈

| 组件 | 版本 | 为什么选它 |
|------|------|-----------|
| Java | 17 | LTS 版本，Spring Boot 3 最低要求 |
| Spring Boot | 3.3.3 | 最新稳定 3.x，2026 年主流选择 |
| MyBatis-Plus | 3.5.7 | 零 SQL 做 CRUD，复杂查询可写 SQL |
| MySQL | 8.x | 最通用的关系型数据库 |
| jjwt | 0.12.6 | JWT 新 API，修复了旧版安全漏洞 |
| BCrypt | Spring Security Crypto | 密码单向加密，不引入完整 Security 框架 |
| Hutool | 5.8.32 | 常用工具（分页、字符串、日期等后备） |
| Lombok | 最新 | 减少样板代码 |

## 构建命令

```bash
mvn clean compile                          # 编译
mvn clean package -DskipTests              # 打包（跳过测试）
mvn spring-boot:run                        # 启动（需要先启动 MySQL）
mvn test                                   # 运行测试
```

从项目根目录 `lost-found/` 执行。

---


## 🧭 关于本仓库的协作规范

这个仓库从骨架到部署全程由 Claude Code 协作完成，本文件是协作规范的一部分：定义模块边界、技术选型理由，以及不可破坏的约定。

> 📌 学习过程中的进度、复习与练习记录在**本地文件** `docs/learning-log.md`（已 gitignore）。它不属于协作规范，只服务于本地学习。

---

## 项目结构

```
lost-found/
├── pom.xml
├── CLAUDE.md                          ← 本文件
├── sql/
│   └── schema.sql                     ← 数据库建表脚本
└── src/main/java/com/lostfound/
    ├── LostFoundApplication.java      ← Spring Boot 启动入口
    ├── config/
    │   ├── JwtConfig.java             ← JWT 配置（从 yml 读取 secret/过期时间）
    │   ├── JwtUtil.java               ← JWT 工具（创建/解析/校验 token，Spring Bean）
    │   ├── WebConfig.java             ← CORS + 拦截器注册
    │   └── MyMetaObjectHandler.java   ← MyBatis-Plus 自动填充时间字段
    ├── common/
    │   ├── Result.java                ← 统一返回体 {code, message, data, timestamp}
    │   ├── ResultCode.java            ← HTTP 状态码常量
    │   ├── BusinessException.java     ← 业务异常（service 抛出，handler 拦截）
    │   └── GlobalExceptionHandler.java← @RestControllerAdvice 全局异常处理
    ├── entity/
    │   ├── User.java                  ← 用户实体 (对应 user 表)
    │   └── Item.java                  ← 物品实体 (对应 item 表)
    ├── dto/
    │   ├── RegisterRequest.java       ← 注册请求 (含 @Valid 校验)
    │   ├── LoginRequest.java          ← 登录请求
    │   ├── LoginResponse.java         ← 登录返回 {token, userId, username, nickname}
    │   ├── ItemCreateRequest.java     ← 发布物品请求
    │   ├── ItemPageQuery.java         ← 物品分页查询参数
    │   └── ChatRequest.java           ← AI 问答请求
    ├── mapper/
    │   ├── UserMapper.java            ← MyBatis-Plus BaseMapper
    │   └── ItemMapper.java            ← MyBatis-Plus BaseMapper
    ├── service/
    │   ├── UserService.java
    │   ├── ItemService.java
    │   ├── AiService.java
    │   └── impl/
    │       ├── UserServiceImpl.java   ← 注册/登录/密码加密
    │       ├── ItemServiceImpl.java   ← 发布/分页查询/状态更新
    │       └── AiServiceImpl.java     ← 暂时返回占位内容
    ├── controller/
    │   ├── UserController.java        ← /api/user/register, /api/user/login
    │   ├── ItemController.java        ← /api/item CRUD (需登录)
    │   └── AiController.java          ← /api/ai/chat
    └── interceptor/
        └── JwtInterceptor.java        ← 解析 Authorization: Bearer xxx，放入 request
```

## 分层调用链

```
Controller (接收请求、参数校验)
  → Service (业务逻辑、事务)
    → Mapper (数据库操作)
      → Entity (表映射)
```

拦截器链: `[JwtInterceptor]` → Controller (除 /api/user/register 和 /api/user/login 外都拦截)

## API 接口

| 方法 | 路径 | 是否需登录 | 说明 |
|------|------|:---:|------|
| POST | /api/user/register | ❌ | 注册 |
| POST | /api/user/login | ❌ | 登录，返回 JWT token |
| POST | /api/item | ✅ | 发布物品 |
| GET | /api/item?page=1&size=10&type=LOST&keyword=手机 | ❌ | 物品列表（分页+筛选） |
| GET | /api/item/{id} | ❌ | 物品详情 |
| PUT | /api/item/{id}/status?status=CLAIMED | ✅ | 修改物品状态（仅发布者） |
| POST | /api/ai/chat | ❌ | AI 问答（暂未接入大模型） |

## 数据库

数据库名: `lost_found`（首次启动 application.yml 中 `createDatabaseIfNotExist=true` 自动创建）

### user 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| username | VARCHAR(50) UNIQUE | 登录用户名 |
| password | VARCHAR(255) | BCrypt 加密 |
| nickname | VARCHAR(50) | 显示昵称 |
| phone | VARCHAR(20) | 手机号 |
| avatar_url | VARCHAR(500) | 头像 |
| role | VARCHAR(20) | USER/ADMIN |
| created_at/updated_at | DATETIME | 自动填充 |

### item 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| user_id | BIGINT FK | 发布者 |
| title | VARCHAR(100) | 物品标题 |
| type | VARCHAR(10) | LOST(寻物)/FOUND(招领) |
| category | VARCHAR(30) | 电子产品/证件/衣物/书籍/其他 |
| location | VARCHAR(200) | 丢失或拾获地点 |
| description | TEXT | 详细描述 |
| image_url | VARCHAR(500) | 图片 |
| contact | VARCHAR(100) | 联系方式 |
| status | VARCHAR(20) | UNCLAIMED/CLAIMED |
| created_at/updated_at | DATETIME | 自动填充 |

## 开发阶段计划

### ✅ 第一阶段（已完成 — 2024-08-04）
基础架构搭建：
- Spring Boot 3.3.3 + Java 17 项目骨架
- 公共组件：统一返回体(Result)、全局异常处理(GlobalExceptionHandler)、业务异常(BusinessException)
- JWT 认证：配置化密钥、拦截器、登录/注册接口
- 用户 + 物品 CRUD：注册、登录、发布物品、分页查询、状态修改
- 数据库 schema 设计
- AI 接口预留（返回占位内容）

### ✅ 第二阶段（已完成 — 2024-08-09）
AI 功能集成：
- DeepSeek API 接入 + RestTemplate + 超时降级
- AI 问答（/api/ai/chat）
- AI 物品匹配（/api/ai/query）：根据失物描述自动匹配招领信息
- API Key 环境变量保护 + 限流拦截器
- 调用方式：同步调用 + 30条候选限制 + 倒序排序

### 📋 第三阶段（当前 — 2024-08-09 起）
3 天冲刺计划：图片上传 → 个人中心 → 测试 + README + GitHub push

| 天数 | 内容 | 状态 |
|:---:|------|:---:|
| Day 1 | 图片上传（本地存储 + 静态资源映射） | ✅ 已完成 |
| Day 2 | 个人中心（我的发布列表 + 个人信息） | 🔄 进行中 |
| Day 3 | 接口测试 + README + GitHub push | ✅ README完成，测试待做 |

功能完善（继续）：
- 图片上传（本地存储优先，后续可演进到 OSS）
- 个人中心（我的发布列表）
- ~~消息通知~~（时间不够先砍）
- ~~管理后台~~（时间不够先砍）

### 💡 后续可扩展方向

在不增加太多复杂度的前提下，可以逐步加入：
1. **乐观锁**：item 表加 version 字段，防并发认领
2. **Redis 缓存**：热点物品数据缓存，减少 DB 压力
3. **接口限流**：用拦截器 + 计数器防止恶意调用
4. **Docker 部署**：写 Dockerfile + docker-compose

**原则**：每次只加一个亮点，确保能讲清楚"为什么加"和"怎么实现"。

## 设计决策

### Q: 为什么不用 Spring Security？
A: 项目只需要 JWT 登录认证，不需要角色权限管理、OAuth2 等复杂功能。只引入 `spring-security-crypto` 做 BCrypt 加密，配合手写 JWT 拦截器，代码量少、好理解、够用。

### Q: 为什么是单体项目而不是微服务？
A: 校园场景用户量有限（几千到几万），单体 + 水平扩展足够。过早微服务会带来分布式事务、服务发现、网络开销等额外复杂度，ROI 不高。项目做好了未来可以按业务边界拆分。

### Q: 为什么要统一返回体 Result？
A: 前端只需要判断 code === 200，然后取 data。异常不靠 HTTP 500 返回给前端，而是由 GlobalExceptionHandler 统一拦截转为 {code: 500, message: "xxx"}，前端处理一致。

### Q: MyBatis-Plus 和 MyBatis 有什么区别？
A: MyBatis-Plus 是 MyBatis 的增强工具，自带通用 CRUD（BaseMapper）、分页插件、条件构造器（LambdaQueryWrapper），不用写 XML 就能做简单查询。复杂 SQL 仍然可以手写 XML。

## 启动前准备

1. 安装 MySQL 8.x，创建数据库（或让应用自动创建）
2. 修改 `application.yml` 中的数据库连接信息（用户名/密码）
3. 运行 `sql/schema.sql` 或让 MyBatis-Plus 自动建表
4. `mvn spring-boot:run`
5. 用 Postman 测试: `POST http://localhost:8080/api/user/register`
