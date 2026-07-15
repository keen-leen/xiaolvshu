# 迭代记录
### 2026-03
- **RAG旅行攻略问答**：使用 Elasticsearch 8.x 统一承载中文全文检索和 RAG 向量检索。启动时增量同步 MySQL 笔记，发布、修改、转草稿和删除后会在事务提交后刷新搜索投影。
- **数据库结构调整**：在`posts`表中新增了`is_vectorized`和`vectorized_at`字段，用于标记文章是否已向量化以及记录最近一次向量化的时间。
- 线上数据库变更SQL：
```sql
ALTER TABLE `posts`
  ADD COLUMN `is_vectorized` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已向量化：0-未向量化，1-已向量化',
  ADD COLUMN `vectorized_at` timestamp NULL DEFAULT NULL COMMENT '最近一次向量化时间',
  ADD COLUMN `is_indexed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已建立全文索引：0-未索引，1-已索引',
  ADD COLUMN `indexed_at` timestamp NULL DEFAULT NULL COMMENT '最近一次全文索引同步成功时间';
```
- **Elasticsearch部署**：使用 `docker/dev/docker-compose.yml` 与 MySQL、Redis、RabbitMQ 一起启动带 SmartCN 中文分词插件的开发实例，迁移步骤见 `doc/ELASTICSEARCH_MIGRATION.md`。
