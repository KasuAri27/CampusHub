-- ============================================================================
-- M006 需求被驳回状态（2026-11-20 用户批准，TODO §1.1）
-- ----------------------------------------------------------------------------
-- 适用：已执行 M005 的数据库。执行后结构与 03 全新建库一致。
-- 执行：先备份，在数据库副本上验证；用 mysql -D <库名> 选择目标库。可重复执行。
--
-- 背景：此前管理员驳回待审需求时直接置为终态 cancelled，发布者只能重新录入全部表单。
--       现新增独立状态 rejected，保留驳回理由，发布者修改后可重新提交审核。
-- 改动内容：
--   1. tasks.status 枚举追加 rejected（追加在末尾，已有取值与数据不受影响）。
--   2. tasks 新增 reject_reason（最近一次驳回理由，最长 200 字）、rejected_at（驳回时间），均可空。
-- 不转换历史数据：此前被驳回而置为 cancelled 的需求保持 cancelled，
--       因为库里没有区分“驳回取消”与“发布者取消、过期取消”的依据，也没有保存当时的驳回理由。
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS m006_add_column$$
CREATE PROCEDURE m006_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', definition);
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------- 1. 状态枚举追加 rejected
ALTER TABLE tasks
  MODIFY status ENUM('pending_review','published','in_progress','completed','cancelled','rejected')
                NOT NULL DEFAULT 'pending_review' COMMENT '需求状态，流转见表注释';

-- ---------------------------------------------------------------- 2. 驳回理由与时间
CALL m006_add_column('tasks', 'reject_reason',
  'VARCHAR(200) DEFAULT NULL COMMENT ''最近一次驳回理由；仅 rejected 状态有值，重新提交后清空''');
CALL m006_add_column('tasks', 'rejected_at',
  'DATETIME(6) DEFAULT NULL COMMENT ''最近一次被驳回的时间；重新提交后清空''');

DROP PROCEDURE m006_add_column;
