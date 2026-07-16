# 搜索与 RAG

## 职责边界

全文搜索和 RAG 都使用 Elasticsearch，但属于两套独立能力：

| 能力 | 服务 | 索引 | 数据库状态 |
| --- | --- | --- | --- |
| 帖子搜索 | `SearchService`、`SearchIndexService` | `${SEARCH_INDEX_PREFIX}_posts_v1` | `is_indexed`、`indexed_at` |
| Agent 知识检索 | `RagService`、`RagIndexService` | `${RAG_INDEX_PREFIX}_post_chunks_v1` | `is_vectorized`、`vectorized_at` |

MySQL 始终是业务事实来源。Elasticsearch 索引可以从 MySQL 重建，不得承担帖子写入或同步状态的唯一记录。

## 全文搜索

全文帖子索引包含：

- `postId`
- `title`
- `content`
- `author`
- `authorUserId`
- `tags`
- `type`
- `createdAt`

中文文本使用 SmartCN 分词。搜索支持关键字相关性、标签和类型过滤、分页、创建时间排序及标签聚合。公开入口为：

```http
GET /api/search
```

`SearchService` 先由 Elasticsearch 得到帖子 ID、总数和聚合，再从业务层组装完整帖子响应。

## RAG 分块索引

RAG 索引按帖子分块，包含：

- `postId`、`chunkIndex`
- `text`、`title`
- `author`、`tags`
- `summary`、`link`
- `embedding`

默认向量维度为 1024，相似度使用 cosine。检索将 SmartCN 文本匹配与 kNN 向量召回放入同一混合查询，其中标题和标签具有更高文本权重。

短内容直接形成一个分块；较长内容按目标长度切分并保留重叠，避免跨分块语义断裂。检索结果按 `postId` 去重后可作为旅行 Agent 的社区笔记引用。

## 同步规则

### 全文索引

增量同步选择已发布、正文非空且满足任一条件的帖子：

- `is_indexed = 0`
- `indexed_at` 为空
- `updated_at > indexed_at`

Elasticsearch 写入成功后才设置 `is_indexed = 1` 并更新 `indexed_at`。若同步期间帖子再次更新，状态更新需要避免覆盖较新的修改。

### RAG 索引

增量同步选择已发布、正文非空且满足任一条件的帖子：

- `is_vectorized = 0`
- `vectorized_at` 为空
- `updated_at > vectorized_at`

同步单篇帖子时先删除旧分块，再写入新分块，成功后更新向量化状态。草稿、空内容或已删除帖子应移除对应索引文档。

## 管理端同步

管理员可以触发增量同步：

```http
POST /api/admin/search/sync
POST /api/admin/rag/sync
```

接口分别返回本次同步的帖子数和分块数：

```json
{"syncedCount": 12}
```

```json
{"indexedChunkCount": 37}
```

两个接口要求管理员角色。重复执行增量同步只处理未同步或内容已更新的数据。

## 配置

主要环境变量：

| 变量 | 说明 |
| --- | --- |
| `ELASTICSEARCH_URIS` | Elasticsearch 地址 |
| `ELASTICSEARCH_USERNAME` / `ELASTICSEARCH_PASSWORD` | 访问凭据 |
| `SEARCH_INDEX_PREFIX` | 全文索引前缀 |
| `RAG_INDEX_PREFIX` | RAG 索引前缀 |
| `RAG_VECTOR_DIMENSIONS` | 索引向量维度 |
| `RAG_NUM_CANDIDATES` | kNN 候选数量 |
| `RAG_TOP_K` | 默认召回数量 |
| `AI_EMBEDDING_MODEL` / `AI_EMBEDDING_DIMENSIONS` | Embedding 模型与输出维度 |

`RAG_VECTOR_DIMENSIONS` 必须与 Embedding 模型实际输出维度一致，否则写入会失败。
当前索引补偿通过管理员同步接口执行，不依赖应用启动时自动重建。

## 运维原则

- 修改索引 mapping 时使用新版本索引名并重建，不依赖在线修改不兼容字段。
- SmartCN 插件版本必须与 Elasticsearch 镜像版本一致。
- 先检查 MySQL 同步状态，再判断索引遗漏，不使用 Elasticsearch 内部字段替代业务同步状态。
- 搜索异常与 RAG 异常分开排查，避免一个索引故障错误地重置另一套状态。
