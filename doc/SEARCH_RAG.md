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

默认向量维度为 1024，相似度使用 cosine。检索分别执行 SmartCN BM25 与 kNN 向量召回，再在应用层使用 Reciprocal Rank Fusion（RRF）按名次融合，避免直接相加不同量纲的原始分数。BM25 中标题和标签具有更高权重；kNN 使用独立的 cosine 最低相似度门槛。

短内容直接形成一个分块；较长内容按目标长度切分并保留重叠，避免跨分块语义断裂。RRF 排序后默认每篇笔记最多保留两个 chunk，防止同一篇笔记占满上下文；前端引用仍按 `postId` 去重。

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

同步会将多篇帖子的 chunk 按配置上限合并成批量 Embedding 请求，减少连续单条请求的次数。每批所有向量生成并写入 Elasticsearch 成功后，才更新该批帖子的向量化状态；批次失败时不标记已向量化，下次增量同步会自动重试。

同步单篇帖子时先生成该帖子的全部新向量，再删除旧分块并写入新分块，成功后更新向量化状态。草稿、空内容或已删除帖子应移除对应索引文档。

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
| `RAG_EMBEDDING_BATCH_SIZE` | 单次 Embedding API 合并的文本数，默认 10，实际限制为 1～10 |
| `RAG_NUM_CANDIDATES` | kNN 候选数量 |
| `RAG_CANDIDATE_COUNT` | BM25 和 kNN 各自召回的融合候选数 |
| `RAG_RRF_RANK_CONSTANT` | RRF 排名融合常量 |
| `RAG_MAX_CHUNKS_PER_POST` | 同一篇笔记最多进入上下文的 chunk 数 |
| `RAG_SIMILARITY_THRESHOLD` | kNN 召回的原始 cosine 最低相似度 |
| `RAG_TOP_K` | 默认召回数量 |
| `AI_EMBEDDING_MODEL` / `AI_EMBEDDING_DIMENSIONS` | Embedding 模型与输出维度 |

`RAG_VECTOR_DIMENSIONS` 必须与 Embedding 模型实际输出维度一致，否则写入会失败。

应用启动时不扫描 MySQL，也不自动批量同步全文索引或 RAG 向量索引。历史数据的增量补偿只能由管理员同步接口显式触发；帖子发布、修改和删除后的单篇投影更新仍在 MySQL 事务提交后执行。`ensureIndex()` 只在首次使用时创建空索引和 mapping，不会同步业务数据。

## 召回评测

日常 `mvn test` 会验证 RRF、双路候选合并、稳定排序和同笔记 chunk 限制，不依赖外部服务。

需要对开发环境的真实 Elasticsearch 索引进行评测时，先完成 RAG 增量同步并配置模型凭据，然后执行：

```bash
cd java-project
mvn -DrunRagEvaluation=true -Dtest=RagRetrievalEvaluationTest test
```

评测开关是 Maven 系统属性，不应写入 `.env.dev` 或部署环境；普通 `mvn test` 即使看到同名环境变量也不会执行真实评测。真实评测只加载 RAG、Elasticsearch 和 Embedding 所需的最小 Spring 上下文，不依赖 JWT、MySQL、Redis、RabbitMQ 或 COS 配置。

评测使用 50 条基于开发种子数据的查询。启动后先批量预生成查询向量（默认为 5 次 API 请求），旧混合查询与当前 RRF 复用同一份向量。输出会单独显示 Embedding 准备耗时；Recall@5、MRR@5、nDCG@5、无答案拒绝率与 P95 中的 P95 只统计 Elasticsearch 检索和应用层排序。

## 运维原则

- 修改索引 mapping 时使用新版本索引名并重建，不依赖在线修改不兼容字段。
- SmartCN 插件版本必须与 Elasticsearch 镜像版本一致。
- 先检查 MySQL 同步状态，再判断索引遗漏，不使用 Elasticsearch 内部字段替代业务同步状态。
- 搜索异常与 RAG 异常分开排查，避免一个索引故障错误地重置另一套状态。
