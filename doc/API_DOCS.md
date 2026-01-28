# 小旅书图文社区 API 接口文档

## 项目信息
- **项目名称**: 小旅书图文社区 (后端)
- **版本**: v1.0.1
- **基础URL**: `http://localhost:8080`
- **开发语言**: Java (Spring Boot)
- **数据库**: MySQL

## 通用说明

### 响应格式
所有API接口由于 `GlobalExceptionHandler` 和 `Result` 类处理，统一返回JSON格式：

```json
{
  "code": 200, // 状态码，200表示成功
  "message": "success", // 提示信息
  "data": {} // 业务数据
}
```

### 鉴权机制
项目使用 JWT (JSON Web Token) 进行身份验证。
- **Header**: `Authorization: Bearer <token>`
- 登录/注册/获取验证码等接口无需 Token。

---

## 核心接口模块

根据 Controller 定义，主要包含以下模块：

### 1. 认证模块 (`/auth`)
处理用户注册、登录及令牌刷新。

- `POST /auth/register`: 用户注册
- `POST /auth/login`: 用户登录
- `POST /auth/refresh-token`: 刷新 Token
- `POST /auth/send-code`: 发送验证码 (如适用) (需确认)
- `GET /auth/captcha`: 获取图形验证码

### 2. 用户模块 (`/user`)
处理用户信息、关注粉丝列表等。

- `GET /user/profile`: 获取当前用户信息
- `PUT /user/profile`: 更新用户信息
- `GET /user/{id}`: 获取指定用户信息
- `GET /user/{id}/posts`: 获取用户发布的笔记

### 3. 笔记模块 (`/post`)
发布、删除、查询笔记。

- `POST /post`: 发布笔记
- `GET /post/{id}`: 获取笔记详情
- `DELETE /post/{id}`: 删除笔记
- `GET /post/list`: 获取笔记列表 (分页)
- `GET /post/recommend`: 推荐流

### 4. 互动模块
- **评论 (`/comment`)**: 发表、回复、删除评论
- **点赞 (`/like`)**: 点赞笔记或评论
- **收藏 (`/collection`)**: 收藏或取消收藏笔记
- **关注 (`/follow` 或 `/user/follow`)**: 关注/取关用户 (需确认具体 Controller 路径)

### 5. 其他模块
- **分类 (`/category`)**: 获取全部分类
- **标签 (`/tag`)**: 标签查询与热门标签
- **文件上传 (`/upload`)**: 图片/视频上传 (通常支持本地、阿里云OSS等)
- **通知 (`/notification`)**: 获取用户消息通知
- **后台管理 (`/admin`)**: 管理员相关操作

> **注意**: 具体接口参数和路径请参考 `java-project` 下的 Controller 源码或 Swagger/OpenAPI 文档 (如果集成了的话)。
