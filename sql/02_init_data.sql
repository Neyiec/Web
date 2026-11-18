-- 执行 01_create_schema.sql 后运行。本脚本可重复执行。
USE campus_market;

INSERT INTO roles (code, name)
VALUES ('STUDENT', '学生'), ('ADMIN', '平台管理员')
ON DUPLICATE KEY UPDATE code = roles.code;

-- 后端固定权限映射：
-- STUDENT 发布和管理本人商品、参与私聊、一口价预约、拍卖出价与面交确认。
-- ADMIN 查看待审商品、通过或驳回商品。兼任学生时可同时分配两个角色。
-- 注册在同一事务创建 users 与 user_roles，默认分配 STUDENT。
-- 注册时规范化手机号并调用 BCryptPasswordEncoder.encode，登录时调用 matches。
-- 管理员通过已注册账号分配角色。初始化脚本不写入账号、密码或私聊样例。
