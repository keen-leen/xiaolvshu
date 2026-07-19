# 项目文档

正式文档按“总体架构 + 功能模块”组织：

## 总体说明

- [ARCHITECTURE.md](ARCHITECTURE.md)：系统边界、分层、组件关系和数据流。
- [DEVELOPMENT.md](DEVELOPMENT.md)：本地配置、启动、构建和开发约定。
- [API.md](API.md)：接口地址、认证规则和端点索引。
- [DATABASE.md](DATABASE.md)：MySQL 表结构、关系和搜索同步状态。
- [CHANGE_LOG.md](CHANGE_LOG.md)：只记录影响整体架构或使用方式的重大变更。

## 功能模块

- [COMMUNITY.md](COMMUNITY.md)：账号、内容、互动、通知和上传。
- [SEARCH_RAG.md](SEARCH_RAG.md)：全文搜索、向量检索和索引同步。
- [RAG_EVALUATION_HISTORY.md](RAG_EVALUATION_HISTORY.md)：RAG 真实评测的参数、指标、调整和结论历史。
- [TRAVEL_AGENT.md](TRAVEL_AGENT.md)：旅行规划 Agent 与 SSE 协议。
- [ADMIN.md](ADMIN.md)：后台认证、数据管理、监控和索引运维。
- [REDIS.md](REDIS.md)：缓存、限流、分布式锁和 Key 规范。

## 维护规则

- 文档描述当前有效实现，不保留已废弃配置的操作指南。
- 新功能优先更新对应模块文档；跨模块变更同时更新总体架构。
- 普通修复不写入 `CHANGE_LOG.md`，只有重大架构或使用方式变更才记录。
- 临时分析、草稿和计划不加入本索引，也不作为正式实现依据。
