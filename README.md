# 校园失物招领与AI智能问答平台

校园场景下的失物招领服务平台，支持用户注册登录、物品发布/搜索/认领、AI 智能问答与物品匹配、图片上传。

**技术栈：** Java 17 + Spring Boot 3.3 + MyBatis-Plus 3.5 + MySQL 8 + JWT + DeepSeek API

---

## 功能

- 用户注册 / 登录（JWT 认证 + BCrypt 密码加密）
- 物品发布、分页搜索（按类型/关键词/时间排序）、详情查看
- 物品状态修改（失物→已认领 / 招领→已领取），仅发布者可操作
- AI 智能问答（接入 DeepSeek 大模型）
- AI 物品匹配（用户描述失物 → 自动匹配招领信息）
- 图片上传（格式校验 + 安全防护）

## 快速启动

### 1. 环境要求

- JDK 17+
- MySQL 8.x
- Maven 3.6+

### 2. 配置数据库

```bash
# 创建数据库（应用首次启动会自动建表，也可以手动执行 sql/schema.sql）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS lost_found CHARACTER SET utf8mb4"
```

修改 `src/main/resources/application.yml` 中的数据库用户名和密码。

### 3. 配置 AI（可选）

```bash
# 不使用 AI 功能可以跳过，应用正常运行
export DEEPSEEK_API_KEY="your-api-key"
```

### 4. 启动

```bash
mvn spring-boot:run
```

应用启动在 `http://localhost:8080`。

## 接口列表

| 方法 | 路径 | 需登录 | 说明 |
|------|------|:---:|------|
| POST | /api/user/register | — | 注册 |
| POST | /api/user/login | — | 登录，返回 JWT token |
| POST | /api/item | ✅ | 发布物品 |
| GET | /api/item | — | 物品列表（分页+筛选+排序） |
| GET | /api/item/{id} | — | 物品详情 |
| PUT | /api/item/{id}/status | ✅ | 修改物品状态（仅发布者） |
| POST | /api/ai/chat | — | AI 问答 |
| POST | /api/ai/query | — | AI 物品匹配 |
| POST | /api/file/upload | ✅ | 上传图片 |

**认证方式：** 登录后拿到 token，请求头加 `Authorization: Bearer <token>`。

## 项目结构

```
src/main/java/com/lostfound/
├── LostFoundApplication.java      # 启动入口
├── common/                         # 公共组件
│   ├── Result.java                # 统一返回体 {code, message, data}
│   ├── ResultCode.java            # 状态码常量
│   ├── BusinessException.java     # 业务异常
│   └── GlobalExceptionHandler.java # 全局异常拦截
├── config/                         # 配置
│   ├── JwtConfig.java             # JWT 配置（密钥、过期时间）
│   ├── JwtUtil.java               # JWT 工具（创建/解析/校验 token）
│   ├── WebConfig.java             # CORS + 拦截器注册
│   ├── DeepSeekConfig.java        # DeepSeek API 配置
│   ├── RateLimitInterceptor.java  # 限流拦截器
│   ├── MybatisPlusConfig.java     # 分页插件
│   └── MyMetaObjectHandler.java   # 时间字段自动填充
├── controller/                     # 接口层
│   ├── UserController.java        # 注册/登录
│   ├── ItemController.java        # 物品 CRUD
│   └── AiController.java          # AI 问答/匹配
├── service/                        # 业务逻辑层
│   ├── UserService.java + impl
│   ├── ItemService.java + impl
│   ├── AiService.java + impl
│   └── FileService.java + impl
├── mapper/                         # 数据库访问层
│   ├── UserMapper.java
│   └── ItemMapper.java
├── entity/                         # 数据库表映射
│   ├── User.java
│   └── Item.java
├── dto/                            # 请求/响应对象
│   ├── RegisterRequest.java
│   ├── LoginRequest.java / LoginResponse.java
│   ├── ItemCreateRequest.java
│   ├── ItemPageQuery.java
│   └── ChatRequest.java
└── interceptor/
    └── JwtInterceptor.java         # JWT 鉴权拦截器
```

**调用链：** Controller → Service → Mapper → DB，拦截器在 Controller 之前执行鉴权。

## 数据库

### user 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| username | VARCHAR(50) | 唯一，登录用 |
| password | VARCHAR(255) | BCrypt 加密存储 |
| nickname | VARCHAR(50) | 显示昵称 |
| phone | VARCHAR(20) | 手机号 |
| role | VARCHAR(20) | USER / ADMIN |

### item 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| user_id | BIGINT FK | 发布者 ID |
| title | VARCHAR(100) | 物品标题 |
| type | VARCHAR(10) | LOST（寻物）/ FOUND（招领） |
| category | VARCHAR(30) | 分类 |
| location | VARCHAR(200) | 地点 |
| description | TEXT | 详细描述 |
| image_url | VARCHAR(500) | 图片路径 |
| contact | VARCHAR(100) | 联系方式 |
| status | VARCHAR(20) | UNCLAIMED / CLAIMED |

## 设计决策

几个关键的技术选择及理由：

- **为什么不用 Spring Security？** 项目只需 JWT 登录认证，不需要角色权限、OAuth2 等。只引入加密库 + 手写拦截器，代码量少、好理解
- **为什么用单体架构？** 校园场景用户量有限，单体足够。过早微服务会引入分布式事务、服务发现等额外复杂度
- **为什么统一返回体？** 前端只需判断 code === 200，异常不靠 HTTP 500 返回，由全局异常处理器统一拦截转换
- **为什么 API Key 放环境变量？** 防止密钥泄露到 Git 仓库，生产环境可平滑迁移到配置中心

更多面试相关的内容见 [CLAUDE.md](./CLAUDE.md) 和 [面试问答文档](./docs/interview-qa.md)。

## 开发日志

| 日期 | 内容 |
|------|------|
| 08-04 | 项目骨架 + JWT 认证 + 用户注册/登录 + CRUD 接口 |
| 08-05 | 分页查询 + 条件筛选 + 排序 + 权限两层防护 |
| 08-06 | 面试复习 + 排序功能改造 + 面试手册 |
| 08-07 | Git & GitHub 推送 + 简历制作 |
| 08-08 | DeepSeek API 接入 + 限流拦截器 + 降级处理 |
| 08-09 | AI 物品匹配 + 模拟面试 |
| 08-10 | 图片上传（MultipartFile + UUID + 白名单校验） |
