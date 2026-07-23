# 开发基础设施

本目录包含可提交、可复现的开发环境定义。MySQL、Redis、RabbitMQ 和
Elasticsearch 由同一个 Compose 工程管理。Compose 直接拉取官方
Elasticsearch 镜像，并在首次创建容器时安装与版本一致的 SmartCN 插件。

## 启动

```bash
cp docker/dev/.env.example docker/dev/.env
# 编辑 .env，替换所有 replace_with_* 占位值
docker compose -f docker/dev/docker-compose.yml pull
docker compose -f docker/dev/docker-compose.yml up -d
```

后端使用独立的本地环境文件：

```bash
cp java-project/.env.example java-project/.env.dev
# 编辑 .env.dev，确保 MySQL、RabbitMQ、Elasticsearch 密码与 docker/dev/.env 一致
cd java-project
./scripts/start-dev.sh
```

`docker/dev/.env` 和 `java-project/.env.dev` 均不会提交到 Git。

## MySQL 初始化

仅维护以下两份 SQL：

- `mysql/init/schema.sql`：完整数据库结构，包含全文索引和 RAG 同步状态字段。
- `mysql/init/data.sql`：开发模拟数据，默认账号密码为 `123456`，密码散列使用 BCrypt。

初始化脚本只会在 MySQL 命名卷首次创建时执行。修改 SQL 后如需重建数据库，
请由开发者自行确认数据备份和命名卷清理操作。
