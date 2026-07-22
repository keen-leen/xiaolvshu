# 总体架构

## 系统边界

小旅书由 Vue 单页应用、Spring Boot API 和一组开发基础设施组成。浏览器只访问前端和 `/api`；后端负责认证、业务规则、数据持久化、搜索编排、AI 调用及对象存储上传。

```text
Vue 3 Web
    |
    | HTTP / SSE
    v
Spring Boot API
    |---- MySQL：业务事实数据
    |---- Redis：缓存、计数、限流、分布式锁
    |---- RabbitMQ：异步消息
    |---- Elasticsearch：全文索引、RAG 分块与向量索引
    |---- AI Provider：对话与向量模型
    `---- COS：图片和视频对象存储
```

## 代码分层

后端位于 `java-project/`：

- `controller/`：HTTP 接口、认证上下文和参数校验。
- `service/`：业务规则、事务、搜索/RAG 和 Agent 编排。
- `mapper/`：MyBatis-Plus 数据访问。
- `entity/`、`dto/`：数据库实体与接口数据结构。
- `config/`、`common/`、`utils/`：安全、基础设施和通用能力。
- `consumer/`：RabbitMQ 消息消费。

前端位于 `vue3-project/`：

- `views/`、`components/`：页面和复用组件。
- `api/`：后端接口封装。
- `stores/`：Pinia 状态。
- `router/`：路由与访问控制。
- `composables/`、`utils/`：共享逻辑。

## 数据职责

MySQL 是用户、帖子、互动关系和同步状态的唯一事实来源。Elasticsearch 是由 MySQL 派生的查询投影，不承载业务事实写入：

- 全文搜索索引保存帖子检索字段。
- RAG 索引保存帖子分块、元数据和向量。
- `posts.is_indexed/indexed_at` 记录全文索引状态。
- `posts.is_vectorized/vectorized_at` 记录 RAG 向量化状态。

索引丢失时应从 MySQL 重建，不能反向用 Elasticsearch 覆盖业务表。

## 主要数据流

### 社区内容

用户请求经过 JWT 过滤器进入 Controller，由 Service 执行业务规则并写入 MySQL。缓存、异步通知和搜索同步属于附属处理，不改变 MySQL 的事实来源地位。

### 搜索与 RAG

全文搜索由 `SearchService` 查询帖子索引；RAG 由 `RagService` 对查询生成向量并在分块索引中执行混合检索。两个索引和同步状态相互独立，详见 [SEARCH_RAG.md](SEARCH_RAG.md)。

### 旅行规划 Agent

旅行助手基于 Spring AI 2.0 原生 Tool Calling 执行有限步数的应用控制循环。模型负责提出工具调用意图，
应用负责白名单、参数、去重、调用预算和超时校验；当前只开放社区笔记检索，不提供实时天气、价格或票务工具。
执行过程和最终回答通过 SSE v2 流式返回，详见 [TRAVEL_AGENT.md](TRAVEL_AGENT.md)。

## 配置原则

后端只维护 `application.yml` 的统一配置结构，环境差异由环境变量提供。开发基础设施统一由 `docker/dev/docker-compose.yml` 管理，真实凭据只保存在被 Git 忽略的本地 `.env` 文件中。
