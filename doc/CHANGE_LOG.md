# 重大变更记录

本文件只记录影响总体架构、开发环境或主要功能边界的变更。普通缺陷修复、页面调整和日常依赖升级不在此记录。

## 2026-07

### 增加腾讯云单机部署与 GitHub CI/CD

- 增加面向 2 核 4G 腾讯云服务器的生产 Compose，并为后端、Elasticsearch、MySQL、
  RabbitMQ、Redis 和前端设置明确的内存边界。
- 前后端改用多阶段生产镜像，Elasticsearch SmartCN 插件在 CI 构建阶段预装；
  服务器运行时所需镜像统一从腾讯云 TCR 拉取。
- GitHub Actions 增加手动发布、固定提交部署、SSH 部署、健康检查、自动回滚和基础镜像
  同步流程。
- 宿主机 Nginx、Docker、Certbot 和首次证书签发保持人工初始化；证书续期由服务器自带
  timer 完成，不向 CI/CD 暴露服务器 root 权限或证书私钥。

### 升级到 Spring Boot 4 与 Spring AI 2

- 后端升级到 Spring Boot 4.1.0、Spring AI 2.0.0、MyBatis-Plus 3.5.17 和 Jackson 3。
- 旅行 Agent 重构为 `ChatClient + MessageChatMemoryAdvisor + ToolCallingAdvisor + @Tool` 标准流程，
  删除手工工具循环和重复协议 DTO。
- 新增 MySQL JDBC ChatMemory、后端会话 ID、身份隔离、消息恢复与清空接口。
- SSE 升级到 v4，Controller 直接返回 Flux，由 Spring MVC 负责订阅、编码和断连取消；
  删除无消费方的事件 ID，同时保留安全进度、结构化终态、心跳和总超时。
- 弹窗与完整页面共用一个 Pinia store，并以 40ms 窗口合并 token，减少重复状态和 Markdown 重绘。
- 完整兼容性、部署检查和回滚说明见 [SPRING_AI_2_UPGRADE_REPORT.md](SPRING_AI_2_UPGRADE_REPORT.md)。

### 搜索与 RAG 统一迁移到 Elasticsearch

- MySQL 保持业务事实来源，Elasticsearch 负责全文搜索和 RAG 向量检索。
- 全文搜索使用独立帖子索引，RAG 使用独立的帖子分块向量索引。
- 移除以 pgvector 作为当前检索后端的旧架构说明。

### 拆分 Search 与 RAG 职责

- `SearchIndexService` 独立负责帖子全文索引、检索、删除和增量同步。
- `RagIndexService` 独立负责分块索引、Embedding 写入和混合检索。
- 两套能力可分别同步、排查和重建，避免共用索引状态。

### 索引状态写入 MySQL

- `posts.is_indexed/indexed_at` 记录全文索引同步状态。
- `posts.is_vectorized/vectorized_at` 记录 RAG 向量化状态。
- 以 `updated_at` 与最近同步时间判断内容是否需要重新同步。

### 增加管理员索引同步入口

- 增加 `POST /api/admin/search/sync`。
- 增加 `POST /api/admin/rag/sync`。
- 管理端可分别触发全文索引和 RAG 索引增量同步。

### 统一开发基础设施

- 开发环境统一由 `docker/dev/docker-compose.yml` 管理 MySQL、Redis、RabbitMQ 和 Elasticsearch。
- Elasticsearch 开发环境直接使用官方镜像，并在首次创建容器时安装匹配版本的 SmartCN 插件。
- MySQL 初始化脚本收敛为 `schema.sql` 和 `data.sql` 两份。
- 提交 `.env.example` 示例配置，真实开发密钥继续由本地忽略文件提供。

### 统一 Spring 配置

- 后端配置收敛到 `application.yml`。
- 环境差异改由环境变量和启动脚本注入，不再维护多份环境 Profile 配置。
