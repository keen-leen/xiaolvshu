-- 现有数据库升级：为全文索引增加独立同步状态。
-- 新字段默认为未同步，升级后由 POST /admin/search/sync 首次补齐。
ALTER TABLE `posts`
  ADD COLUMN `is_indexed` tinyint(1) NOT NULL DEFAULT 0
    COMMENT '是否已建立全文索引：0-未索引，1-已索引',
  ADD COLUMN `indexed_at` timestamp NULL DEFAULT NULL
    COMMENT '最近一次全文索引同步成功时间';
