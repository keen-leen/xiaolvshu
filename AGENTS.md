# Repository Guidelines

## 项目结构与模块组织

本仓库是小旅书旅游交流社区应用。后端位于 `java-project/`，使用 Java 21、Spring Boot 3.3.5、MyBatis-Plus、JWT、Redis、RabbitMQ、MySQL 和 Elasticsearch 8.x（全文与向量检索）。后端源码在 `java-project/src/main/java/com/xiaolvshu/`，按 `controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`config/`、`utils/` 分层组织；运行配置位于 `java-project/src/main/resources/`。

前端位于 `vue3-project/`，是 Vue 3 + Vite 应用。源码在 `vue3-project/src/`，包括 `api/`、`components/`、`views/`、`stores/`、`router/`、`utils/`、`composables/` 和 `assets/`。数据库初始化与辅助脚本在 `scripts/`，项目文档在 `doc/`。

## 构建、测试与开发命令

- `cd java-project && mvn spring-boot:run`：启动后端服务。
- `cd java-project && mvn test`：运行后端测试。
- `cd java-project && mvn clean package -DskipTests`：构建后端 JAR。
- `cd vue3-project && npm install`：安装前端依赖。
- `cd vue3-project && npm run dev`：启动 Vite 开发服务，`/api` 会代理到后端。
- `cd vue3-project && npm run build`：构建前端产物到 `dist/`。
- `mysql -u root -p xiaolvshu < scripts/init-database.sql`：初始化 MySQL 表结构。

## 代码风格与命名约定

优先沿用现有格式。Java 代码使用 4 空格缩进，包名为 `com.xiaolvshu`，在已有模式下使用 Lombok；分层命名示例：`PostController`、`PostService`、`PostMapper`、`PostResponse`。接口响应统一使用 `Result<T>`，后台管理相关代码放在 `controller/admin/` 和 `dto/admin/`。

前端使用 ES modules、2 空格缩进和单引号。Vue 单文件组件使用 PascalCase，Pinia store 按业务域命名，如 `auth.js`、`notification.js`。API 封装放在 `src/api/`，共享逻辑放在 `utils/` 或 `composables/`。

## 测试指南

当前仓库没有已提交的测试文件。新增后端测试时放在 `java-project/src/test/java/`，命名示例：`PostServiceTest`、`AuthControllerTest`。前端尚未配置测试运行器；在引入前，请至少执行 `npm run build` 并进行有针对性的手动验证。

## 提交与 Pull Request 规范

近期提交信息较短，且中英文混用，例如 `fix`、`RAG旅游攻略生成助手`。建议使用简洁且能说明范围的提交信息，例如 `修复登录状态刷新` 或 `frontend: fix upload preview`。PR 应包含变更摘要、测试或构建结果、关联 issue、UI 截图，以及数据库或配置变更说明。

## 安全与配置提示

不要提交真实密钥。前端环境变量以 `vue3-project/.env.example` 为模板；后端凭据应保存在本地或部署环境专用的 Spring 配置中。本地开发需要 MySQL 8、Redis、RabbitMQ、带 SmartCN 插件的 Elasticsearch 8.x，以及 JDK 21。
