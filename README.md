# 小旅书（Xiaolvshu）

小旅书是一个旅游内容社区，支持图文/视频游记、用户关系、互动通知、全文搜索、RAG 检索和旅行规划助手。

## 技术栈

- 后端：Java 21、Spring Boot 4.1.0、Spring AI 2.0.0、MyBatis-Plus 3.5.17、JWT
- 前端：Vue 3、Vite、Pinia、Vue Router、Vant、Element Plus
- 数据与基础设施：MySQL 8、Redis 7、RabbitMQ、Elasticsearch 9.4.2

## 快速开始

准备 Docker、Docker Compose、JDK 21、Maven 和 Node.js，然后创建本地配置：

```bash
cp docker/dev/.env.example docker/dev/.env
cp java-project/.env.example java-project/.env.dev
cp vue3-project/.env.example vue3-project/.env
```

编辑上述文件，将占位值替换为本地开发凭据。真实密钥不得提交到仓库。

启动基础设施：

```bash
docker compose -f docker/dev/docker-compose.yml up -d --build
```

启动后端：

```bash
cd java-project
./scripts/start-dev.sh
```

启动前端：

```bash
cd vue3-project
npm install
npm run dev
```

默认地址：

- 前端：`http://localhost:5173`
- 后端 API：`http://localhost:8080/api`
- RabbitMQ 管理界面：`http://localhost:25672`
- Elasticsearch：`http://localhost:19200`

完整环境说明见 [开发指南](doc/DEVELOPMENT.md)。

## 文档

- [总体架构](doc/ARCHITECTURE.md)
- [开发指南](doc/DEVELOPMENT.md)
- [API 概览](doc/API.md)
- [数据库设计](doc/DATABASE.md)
- [社区功能](doc/COMMUNITY.md)
- [搜索与 RAG](doc/SEARCH_RAG.md)
- [旅行规划 Agent](doc/TRAVEL_AGENT.md)
- [Spring AI 2.0 升级报告](doc/SPRING_AI_2_UPGRADE_REPORT.md)
- [后台管理](doc/ADMIN.md)
- [Redis 模块](doc/REDIS.md)
- [重大变更记录](doc/CHANGE_LOG.md)

文档维护约定见 [doc/README.md](doc/README.md)。

## 许可证与来源

本项目基于 [GNU Affero General Public License v3](LICENSE) 开源，修改自
[小石榴（XiaoShiLiu）](https://github.com/ZTMYO/XiaoShiLiu)。本项目已使用 Java 后端替换原 Express 后端，并围绕旅游社区、搜索和 AI 能力持续扩展。
