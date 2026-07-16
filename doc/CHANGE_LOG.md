# 重大变更记录

本文件只记录影响总体架构、开发环境或主要功能边界的变更。普通缺陷修复、页面调整和日常依赖升级不在此记录。

## 2026-07

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
- Elasticsearch 开发镜像构建时安装匹配版本的 SmartCN 插件。
- MySQL 初始化脚本收敛为 `schema.sql` 和 `data.sql` 两份。
- 提交 `.env.example` 示例配置，真实开发密钥继续由本地忽略文件提供。

### 统一 Spring 配置

- 后端配置收敛到 `application.yml`。
- 环境差异改由环境变量和启动脚本注入，不再维护多份环境 Profile 配置。
