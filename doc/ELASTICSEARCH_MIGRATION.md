# Elasticsearch 笔记搜索与 RAG 迁移

## 架构

MySQL 仍是业务事实源，Elasticsearch 只保存可随时重建的搜索投影：

- `xiaolvshu_posts_v1`：一篇已发布笔记一条文档，用于中文全文搜索、类型过滤、标签过滤和标签聚合。
- `xiaolvshu_post_chunks_v1`：一个正文 chunk 一条文档，保存 1024 维 embedding，用于旅行助手的 BM25 + kNN 混合召回。

项目不再依赖 PostgreSQL、pgvector 或 `RAG_DB_*` 环境变量。Embedding 仍由现有 OpenAI 兼容接口和 `text-embedding-v4` 提供。

## 本地启动

开发环境的 Elasticsearch 已与 MySQL、Redis、RabbitMQ 合并在统一编排中：

```bash
docker compose -f docker/dev/docker-compose.yml up -d --build
```

生产环境使用 `docker/release/docker-compose.yml`，同样由一个 Compose 工程管理全部基础设施和后端服务。启动前导出与 `backend.enc` 一致的 `ELASTICSEARCH_PASSWORD`，供 ES 容器初始化使用。

默认账号为 `elastic`，默认开发密码为 `xiaolvshu`。生产环境必须通过环境变量设置强密码，并限制 9200 端口的访问范围。

后端使用以下配置：

```bash
export ELASTICSEARCH_URIS=http://localhost:9200
export ELASTICSEARCH_USERNAME=elastic
export ELASTICSEARCH_PASSWORD=xiaolvshu
export RAG_INDEX_PREFIX=xiaolvshu
export RAG_VECTOR_DIMENSIONS=1024
export RAG_NUM_CANDIDATES=100
```

## 首次迁移

1. 启动 Elasticsearch，确认集群健康。
2. 先执行 `scripts/add-post-index-status.sql`，再启动后端；全文和 RAG 服务会分别初始化、增量同步各自的索引。
3. 使用管理员 JWT 补齐全文索引：

```bash
curl -X POST 'http://localhost:8080/api/admin/search/sync' \
  -H 'Authorization: Bearer <admin-access-token>'
```

4. 从 MySQL 全量重建 RAG chunk：

```bash
curl -X POST 'http://localhost:8080/api/ai/travel/sync?mode=full'
```

5. 验证 `/api/search`、`/api/posts/search` 和旅行助手引用结果。
6. 保留 pgvector 一个观察期；确认没有回滚需求后再停止并删除 PostgreSQL 实例。

RAG 全量同步只会清空 chunk 投影，不会影响全文索引。若中途失败，
成功项会单独标记，未完成项可在修复 embedding 或 ES 连接后通过增量同步继续补偿。

## 日常同步与补偿

发布、修改、转草稿和删除笔记会在 MySQL 事务提交后更新 ES。失败只记录错误，避免搜索基础设施故障回滚业务事务；可通过下列接口补偿：

```bash
curl -X POST 'http://localhost:8080/api/ai/travel/sync?mode=incremental'
```

全文索引可由管理员单独补偿：

```bash
curl -X POST 'http://localhost:8080/api/admin/search/sync' \
  -H 'Authorization: Bearer <admin-access-token>'
```

`is_indexed/indexed_at` 记录全文索引状态，`is_vectorized/vectorized_at` 记录 RAG chunk 状态。
两类索引均可独立从 MySQL 重建。

## 回滚

应用切回迁移前版本，并恢复原 `RAG_DB_*` 配置即可重新使用保留的 pgvector。ES 是派生数据，回滚期间无需反向写入 MySQL。
