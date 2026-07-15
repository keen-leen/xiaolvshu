# 小旅书 (Xiaolvshu)

小旅书是一款专注于旅游交流的社区应用，旨在为旅行爱好者提供一个分享旅行见闻、攻略和心得的平台。

## 项目简介

小旅书致力于打造一个纯粹的旅游分享社区，用户可以：
- 发布图文/视频游记
- 浏览热门旅游目的地攻略
- 关注感兴趣的旅行博主
- 互动交流（点赞、评论、收藏）

## 技术栈

### 后端
- **Java**: Spring Boot 3.3.5, MyBatis-Plus 3.5.7
- **数据库与搜索**: MySQL 8.0、Elasticsearch 8.x（全文检索与 RAG 向量检索）
- **缓存/工具**: Hutool, JWT

### 前端
- **Vue 3**: Vite, Pinia, Vue Router
- **UI**: Vant UI / Element Plus

## 许可证

本项目基于 [GNU Affero General Public License v3 (AGPLv3)](LICENSE) 许可证开源。

## 致谢与来源

本项目修改自开源项目 **小石榴 (Xiaoshiliu) https://github.com/ZTMYO/XiaoShiLiu**
- 感谢原作者的开源。
- 本项目在原项目基础上进行了重构与功能扩展，主要修改包括：
    - 将项目名称重品牌化为 "小旅书 (Xiaolvshu)"。
    - 使用Java后端替换原express后端。
    - 调整了部分业务逻辑以适应新的需求。

