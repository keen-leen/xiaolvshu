# 小旅书图文社区部署指南

## 项目简介

小旅书图文社区是一个基于 **Spring Boot 3** (后端) + **Vue 3** (前端) 的前后端分离项目。

## 运行环境要求

- **JDK**: Java 17 或更高版本
- **Node.js**: 18.x 或更高版本 (用于前端构建)
- **Database**: MySQL 5.7 或 8.0+
- **Build Tools**: Maven 3.8+, npm/yarn

---

## 本地开发运行

### 1. 数据库准备

1. 创建数据库 `xiaolvshu`。
2. 运行 `scripts/init-database.sql` 初始化表结构。
3. (可选) 运行 `scripts/data.sql` 导入测试数据。
4. 修改配置文件 `java-project/src/main/resources/application.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/xiaolvshu
       username: root
       password: your_password
   ```

### 2. 后端启动 (Spring Boot)

```bash
cd java-project
# 使用 Maven 编译并运行
mvn spring-boot:run
```
服务默认启动在端口 `8080`。

### 3. 前端启动 (Vue 3)

```bash
cd vue3-project
# 安装依赖
npm install
# 启动开发服务器
npm run dev
```
前端默认启动在端口 `5173` 或其他随机端口。
**注意**: `vite.config.js` 中配置了 `/api` 代理指向 `http://localhost:8080`，请确保后端正常启动。

---

## 生产环境部署

### 1. 后端打包

```bash
cd java-project
mvn clean package -DskipTests
```
构建完成后，在 `target/` 目录下会生成 `xiaolvshu-backend-1.0.1.jar`。

运行 jar 包：
```bash
java -jar target/xiaolvshu-backend-1.0.1.jar
```

### 2. 前端打包

```bash
cd vue3-project
npm run build
```
构建产物在 `dist/` 目录下。需配合 Nginx 进行部署。

### 3. Nginx 配置示例

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # 前端静态资源
    location / {
        root /path/to/vue3-project/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://localhost:8080/; # 注意路径及斜杠
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## Docker 部署

项目包含 `Dockerfile`，支持容器化部署。

### 后端 Docker 构建
```bash
cd java-project
docker build -t xiaolvshu-backend .
docker run -d -p 8080:8080 --name xiaolvshu-backend xiaolvshu-backend
```

### 前端 Docker 构建
```bash
cd vue3-project
docker build -t xiaolvshu-frontend .
docker run -d -p 80:80 --name xiaolvshu-frontend xiaolvshu-frontend
```

建议使用 `docker-compose` (如果根目录提供 `docker-compose.yml`) 进行一键编排启动。
