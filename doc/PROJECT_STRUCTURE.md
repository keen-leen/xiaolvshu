# 项目结构

本文档详细介绍了小旅书图文社区项目的目录结构和文件组织。

## 总体结构

```
xiaolvshu/
├── java-project/           # 后端项目 (Spring Boot)
├── vue3-project/           # 前端项目 (Vue 3 + Vite)
├── scripts/                # 数据库脚本与工具
├── doc/                    # 项目文档
├── README.md               # 项目主文档
└── LICENSE                 # 许可证
```

## 后端项目结构 (java-project/)

该项目基于 **Spring Boot 3.3.5** 构建，使用 Maven 进行依赖管理。

```
java-project/
├── src/
│   └── main/
│       ├── java/com/xiaolvshu/
│       │   ├── common/           # 通用类（统一响应、常量等）
│       │   ├── config/           # 配置类（MyBatis, Security, WebMvc等）
│       │   ├── context/          # 上下文相关（如当前用户ID获取）
│       │   ├── controller/       # 控制器层（API 接口定义）
│       │   ├── dto/              # 数据传输对象 (Request/Response Records)
│       │   ├── entity/           # 实体类（数据库表映射）
│       │   ├── exception/        # 全局异常处理
│       │   ├── mapper/           # MyBatis Mapper 接口
│       │   ├── service/          # 业务逻辑层接口
│       │   │   └── impl/         # 业务逻辑层实现
│       │   ├── utils/            # 工具类（Jwt, File, Date等）
│       │   └── XiaolvshuApplication.java # 启动类
│       └── resources/
│           ├── mapper/           # MyBatis XML 映射文件 (如有)
│           ├── application.yml   # 主配置文件
│           └── application-dev.yaml # 开发环境配置
├── Dockerfile                    # 后端 Docker 构建文件
├── pom.xml                       # Maven 依赖配置
└── target/                       # 编译输出目录
```

## 前端项目结构 (vue3-project/)

该项目基于 **Vue 3** 和 **Vite** 构建。

```
vue3-project/
├── public/                 # 静态资源目录 (manifest.json等)
├── src/                    # 源代码目录
│   ├── api/                # Axios 请求封装模组
│   │   ├── auth.js         # 认证相关接口
│   │   ├── posts.js        # 帖子相关接口
│   │   ├── user.js         # 用户相关接口
│   │   └── ...
│   ├── assets/             # 静态资源 (css, icons, imgs)
│   ├── components/         # 公共组件 (Button, Card, Modal, WaterfallFlow等)
│   ├── composables/        # 组合式函数 (Hooks)
│   ├── config/             # 全局配置 (api, constants)
│   ├── directives/         # 自定义指令
│   ├── router/             # Vue Router 路由配置
│   ├── stores/             # Pinia 状态管理
│   ├── utils/              # 工具函数
│   ├── views/              # 页面视图组件
│   ├── App.vue             # 根组件
│   └── main.js             # 入口文件
├── index.html              # HTML 模板
├── vite.config.js          # Vite 构建配置
├── package.json            # 依赖包配置
└── nginx.conf              # Nginx 部署配置
```

## 数据库与脚本 (scripts/)

```
scripts/
├── init-database.sql       # 数据库初始化 SQL (建表语句)
├── data.sql                # 初始数据/演示数据
├── generate_sql.py         # SQL 生成脚本
└── imgLinks/               # 图片链接资源
```

## 文档 (doc/)

```
doc/
├── API_DOCS.md             # API 接口文档
├── DATABASE_DESIGN.md      # 数据库设计文档
├── DEPLOYMENT.md           # 部署指南
└── PROJECT_STRUCTURE.md    # 项目结构说明
```
