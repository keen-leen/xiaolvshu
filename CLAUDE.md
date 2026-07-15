# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

小旅书 (Xiaolvshu) 是一个旅游交流社区应用，基于原始项目"小石榴 (XiaoShiLiu)"重构而来，将 Express 后端替换为 Java Spring Boot。

## 构建与运行命令

### 后端 (java-project/)

```bash
# 开发环境启动
cd java-project && mvn spring-boot:run

# 打包 (跳过测试)
mvn clean package -DskipTests

# 运行 JAR (版本号见 pom.xml)
java -jar target/xiaolvshu-backend-2.0.1.jar
```

**要求**: JDK 21, MySQL 8.0, Redis, RabbitMQ, Elasticsearch 8.x (SmartCN 插件)

### 前端 (vue3-project/)

```bash
cd vue3-project
npm install
npm run dev        # 开发服务器, 默认代理 /api → localhost:8080
npm run build      # 生产构建到 dist/
```

**要求**: Node.js 18+

### 数据库初始化

```bash
mysql -u root -p xiaolvshu < scripts/init-database.sql
mysql -u root -p xiaolvshu < scripts/data.sql   # 可选: 测试数据
```

## 技术栈与架构

**后端**: Java 21, Spring Boot 3.3.5, MyBatis-Plus 3.5.7, Spring Security (JWT 无状态认证), Spring AI (RAG)

**前端**: Vue 3 (Composition API), Vite 5, Pinia, Vue Router 4, Axios

**基础设施**: MySQL 8.0, Redis (缓存/限流/分布式锁), RabbitMQ (异步消息), Elasticsearch 8.x (全文与向量检索), 腾讯云 COS (文件存储)

### 后端分层架构

```
controller/ → service/ → mapper/ (MyBatis-Plus)
     ↓            ↓
   dto/        entity/
```

- **controller/** — REST API, 分 `controller/admin/` (后台管理) 和顶层 (前台接口)
- **service/** — 业务逻辑接口, `service/impl/` 为实现
- **mapper/** — MyBatis-Plus BaseMapper 接口, 无 XML 映射文件
- **dto/** — 请求/响应 DTO (Java records 或 POJO), `dto/admin/` 为后台 DTO

### 认证与鉴权

- JWT 无状态认证: `JwtAuthenticationFilter` 在 `SecurityConfig` 过滤器链中
- 用户上下文: `UserContext.getUserId()` 通过 `SecurityContextHolder` 获取当前用户
- 公开接口 (无需登录): GET 类型的 `/auth/**`, `/posts/**`, `/users/**`, `/categories/**`, `/search`, POST `/ai/travel/**`
- 所有其他接口需要 `Authorization: Bearer <token>` 头
- BCrypt(12) 密码编码

### API 响应规范

统一通过 `Result<T>` 包装: `{ "code": 200, "message": "操作成功", "data": {} }`

全局异常处理: `GlobalExceptionHandler` (`@RestControllerAdvice`)

### 缓存与基础设施层

- **Redis**: `RedisService` (底层操作), `CacheService` (业务缓存 + 防穿透/击穿), Spring Cache 注解支持
- **限流**: `@RateLimit` 注解, 通过 `RateLimitAspect` AOP 切面实现, 支持 IP/USER/GLOBAL 三种粒度
- **分布式锁**: `@DistributedLock` 注解, `DistributedLockAspect` 切面
- **Redis Key 规范**: `xiaolvshu:模块:业务:标识`, 使用 `RedisKeyUtil` 构建, 过期时间用 `RedisExpireConstant`

### 消息队列

RabbitMQ 用于异步处理点赞: `LikeMessageConsumer` 消费点赞消息

### RAG 旅游 AI (`TravelAiService`)

- 启动时自动将 MySQL `posts` 表内容增量同步到 Elasticsearch 全文与 RAG chunk 索引
- 支持同步 (`chat()`) 和流式 SSE (`chatStream()`) 两种对话模式
- `posts` 表有 `is_vectorized` 和 `vectorized_at` 字段追踪向量化状态
- Controller: `TravelAiController`, 无需鉴权 (`/ai/travel/**`)

### 前端路由结构

- `/explore` — 瀑布流内容发现 (含频道子路由)
- `/publish` — 发布笔记
- `/post` — 笔记详情
- `/notification` — 消息通知
- `/user`, `/user/:userId` — 个人中心/用户主页
- `/search_result/:tab` — 搜索结果
- `/post-management`, `/draft-box` — 内容管理/草稿箱
- `/admin/login`, `/admin/*` — 后台管理系统 (独立布局)

### 前端状态管理

Pinia stores 位于 `stores/`, 每个业务域独立 store (auth, user, like, follow, comment, notification 等), 通过 `utils/eventBus.js` 进行跨组件事件通信。

## 重要约定

- 数据库无物理外键约束, 通过逻辑关联维护关系
- 数据库字符集 `utf8mb4`, 排序规则 `utf8mb4_unicode_ci`
- 前端开发时 Vite 配置了 `/api` 代理到 `localhost:8080`, 后端必须启动
