# 迭代记录
### 2026-03
- **RAG旅行攻略问答**：实现了基于pgvector的RAG旅行攻略问答模块，启动时自动同步MySQL中的笔记内容到PostgreSQL的向量数据库，并提供基于向量检索的问答接口。新增、修改以及删除笔记时，都会触发增量同步更新向量数据库，确保问答系统能够及时反映最新的内容变化。
- **数据库结构调整**：在`posts`表中新增了`is_vectorized`和`vectorized_at`字段，用于标记文章是否已向量化以及记录最近一次向量化的时间。
- 线上数据库变更SQL：
```sql
ALTER TABLE `posts`
  ADD COLUMN `is_vectorized` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已向量化：0-未向量化，1-已向量化',
  ADD COLUMN `vectorized_at` timestamp NULL DEFAULT NULL COMMENT '最近一次向量化时间';
```
- **Docker配置更新**：更新了`docker-compose.yml`文件，新增了RAG模块所需的PostgreSQL服务