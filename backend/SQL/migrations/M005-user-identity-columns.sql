-- ============================================================================
-- M005 账号身份列调整（2026-11 用户批准）
-- ----------------------------------------------------------------------------
-- 适用：已执行 M004 的数据库。执行后结构与 02-04 全新建库一致。
-- 执行：先备份，在数据库副本上验证；用 mysql -D <库名> 选择目标库。可重复执行。
--
-- 改动内容：
--   1. users.student_id 改为 NOT NULL：学号注册必填，且是登录凭证（JWT）的主体。
--      早期账号学号为空（或空串）的，回填占位学号 legacy<用户ID>，保证唯一且一眼可辨。
--      这些账号原本就无法正常通过鉴权；回填后可用手机号 + 验证码登录。
--      若占位学号恰好与已有学号重复，唯一约束会让本脚本失败，交由人工核对。
--      执行前可用下面的语句查看将被回填的账号：
--        SELECT id, email FROM users WHERE student_id IS NULL OR student_id = '';
--   2. users.name 加宽为 VARCHAR(255)：真实姓名改为 AES-GCM 密文入库，20 个汉字的密文约 124 字符。
--      本脚本不改写已有数据：已有行保留明文，应用按“不带 gcm: 前缀即历史明文”读取。
--   3. users 新增 college（学院院系，可空）。
--   4. users.nickname 仅更新列注释（注册时改为必填，不再自动生成），结构不变。
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS m005_add_column$$
CREATE PROCEDURE m005_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', definition);
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------- 1. 学号非空
UPDATE users SET student_id = CONCAT('legacy', id) WHERE student_id IS NULL OR student_id = '';

-- ---------------------------------------------------------------- 2. 姓名加宽（连同 1、4 的列定义一并修改）
ALTER TABLE users
  MODIFY student_id VARCHAR(20)  NOT NULL COMMENT '学号：密码登录账号，注册时必填并绑定，之后不可更改',
  MODIFY name       VARCHAR(255) NOT NULL COMMENT '真实姓名密文：gcm: 前缀 + AES-GCM 密文的 Base64，明文最长 20 字符；不带前缀的是加密上线前的历史明文；管理员固定为“管理员”',
  MODIFY nickname   VARCHAR(20)  NULL     COMMENT '昵称：最长 10 个汉字或 20 个英文字符（汉字计 2，英文数字计 1）；注册时必填，历史账号可能为空，为空时显示真实姓名';

-- ---------------------------------------------------------------- 3. 学院
CALL m005_add_column('users', 'college', 'VARCHAR(50) NULL COMMENT ''学院院系：注册时可选填，可在个人资料中修改'' AFTER avatar');

DROP PROCEDURE m005_add_column;
