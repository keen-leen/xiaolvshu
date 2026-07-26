# 开发指南

## 环境要求

- Docker 与 Docker Compose
- JDK 21
- Maven 3.9 或可用的 Maven Wrapper
- Node.js 与 npm

开发 Compose 提供 MySQL 8、Redis 7、RabbitMQ 和安装 SmartCN 插件的 Elasticsearch 9.4.2。

## 本地配置

仓库只提交示例配置：

```bash
cp docker/dev/.env.example docker/dev/.env
cp java-project/.env.example java-project/.env.dev
cp vue3-project/.env.example vue3-project/.env
```

编辑本地文件并替换全部 `replace_with_*` 占位值。以下值必须保持一致：

- `docker/dev/.env` 的 `MYSQL_PASSWORD` 与后端的 `DB_PASSWORD`
- 两边的 RabbitMQ 密码
- 两边的 Elasticsearch 密码

后端还需要 JWT、AI 服务和 COS 的真实开发凭据。`.env`、`.env.dev` 等真实配置不得提交。

## 启动基础设施

```bash
docker compose -f docker/dev/docker-compose.yml up -d --build
```

默认只绑定本机回环地址：

| 服务 | 地址 |
| --- | --- |
| MySQL | `127.0.0.1:13306` |
| Redis | `127.0.0.1:16379` |
| RabbitMQ AMQP | `127.0.0.1:15672` |
| RabbitMQ 管理界面 | `127.0.0.1:25672` |
| Elasticsearch | `127.0.0.1:19200` |

端口可在 `docker/dev/.env` 中调整。

MySQL 命名卷首次创建时按顺序执行：

1. `docker/dev/mysql/init/schema.sql`
2. `docker/dev/mysql/init/data.sql`

脚本后续修改不会自动作用于已有数据卷。需要重建时应先自行备份数据，再明确处理对应命名卷。

常用命令：

```bash
docker compose -f docker/dev/docker-compose.yml ps
docker compose -f docker/dev/docker-compose.yml logs -f
docker compose -f docker/dev/docker-compose.yml down
```

## 启动后端

`application.yml` 是唯一 Spring 运行配置，所有环境差异通过环境变量注入：

```bash
cd java-project
./scripts/start-dev.sh
```

脚本加载 `.env.dev`，检查关键变量后执行 `mvn spring-boot:run`。后端默认监听 `http://localhost:8080/api`。

其他命令：

```bash
mvn clean test
mvn clean package -DskipTests
```

## 启动前端

```bash
cd vue3-project
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，API 地址由 `VITE_API_BASE_URL` 控制。

构建命令：

```bash
npm run lint
npm run test
npm run build
```

`main` 分支和 Pull Request 会由 `.github/workflows/ci.yml` 自动执行以上后端与前端检查。

管理员登录后可在 `/admin/api-docs` 查看由后端实时生成的 OpenAPI 接口清单。
后端规范地址为 `/v3/api-docs`，该地址与其他管理接口一样要求 `ROLE_ADMIN`；
生产环境可设置 `API_DOCS_ENABLED=false` 完全关闭文档生成。

## 项目目录

```text
.
├── docker/dev/                  # 可复现的开发基础设施
├── java-project/                # Spring Boot 后端
├── vue3-project/                # Vue 3 前端
├── doc/                         # 正式项目文档
└── scripts/                     # 仓库级辅助脚本
```

## 配置和数据变更

- 新配置项加入 `application.yml` 时，应提供环境变量名和合理的非敏感默认值。
- 需要密钥的配置必须同步补充到 `.env.example`，但不能写入真实值。
- 数据库完整结构只维护在 `schema.sql`，初始化数据只维护在 `data.sql`。
- 搜索映射变更应同步更新 [SEARCH_RAG.md](SEARCH_RAG.md) 和相应重建说明。
