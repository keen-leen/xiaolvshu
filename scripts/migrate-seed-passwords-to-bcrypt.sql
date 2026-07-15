-- 将历史种子账号的 SHA-256 密码迁移为与 Spring BCryptPasswordEncoder(12) 一致的格式。
-- 下面的 BCrypt 哈希对应开发环境默认密码：123456。
-- 仅更新密码仍等于 SHA2('123456', 256) 的账号，不覆盖用户后来修改过的密码。

START TRANSACTION;

UPDATE `admin`
SET `password` = '$2a$12$81U/nCucOHrJRPeGpZXFRONN07x8wYndkqsZ7Hm5M6Xx3PbFr1kA6'
WHERE `password` = SHA2('123456', 256);

UPDATE `users`
SET `password` = '$2a$12$81U/nCucOHrJRPeGpZXFRONN07x8wYndkqsZ7Hm5M6Xx3PbFr1kA6'
WHERE `password` = SHA2('123456', 256);

COMMIT;
