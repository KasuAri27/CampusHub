-- ============================================================================
-- M004 合并普通用户角色（2026-11 用户批准）
-- ----------------------------------------------------------------------------
-- 适用：已执行 M003 的数据库。执行完后继续执行后续编号的升级脚本。
-- 执行：先备份，在数据库副本上验证；用 mysql -D <库名> 选择目标库。可重复执行。
--
-- 背景：原设计让普通用户在“需求方 requester / 服务方 provider”之间手动切换，
--       需求方才能发布、服务方才能接单。现改为同一账号既能发布也能接单（仍不能接自己的单），
--       不再需要切换，角色只保留普通用户 user 和管理员 admin。
-- 改动：users.role 的 requester、provider 全部改为 user，然后把 ENUM 收窄为 ('user','admin')。
--       管理员账号保持 admin 不变。
-- ============================================================================

-- 先放宽枚举，让新旧取值能同时存在，再改数据，最后收窄。
ALTER TABLE users
  MODIFY role ENUM('requester','provider','admin','user') NOT NULL COMMENT '角色（迁移中）';

UPDATE users SET role = 'user' WHERE role IN ('requester', 'provider');

ALTER TABLE users
  MODIFY role ENUM('user','admin') NOT NULL COMMENT '角色：user=普通用户（可发布需求也可接单，但不能接自己的单），admin=管理员（只做审核与监管，不发布不接单）';
