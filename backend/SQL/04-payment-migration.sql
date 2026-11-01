-- MySQL 8.x：先备份并在数据库副本验证；用 mysql -D 明确选择目标库。
-- 不自动把旧订单认作欠款，不丢弃旧收款码或二手子表数据。
-- DDL会隐式提交；这不是可以整体回滚的事务脚本。执行失败时停止发布并核查备份。

-- 旧Hibernate版本可能只有second_hand_task，先建立正式子表，再复制历史资料。
CREATE TABLE IF NOT EXISTS t_task_flea (
  task_id BIGINT NOT NULL PRIMARY KEY,
  trade_mode VARCHAR(10) DEFAULT 'SELL', item_price DECIMAL(10,2) NULL,
  requester_payment_code VARCHAR(255) NULL,
  CONSTRAINT fk_migrated_flea_task FOREIGN KEY(task_id) REFERENCES tasks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS campushub_add_column$$
CREATE PROCEDURE campushub_add_column(IN table_name_value VARCHAR(64), IN column_name_value VARCHAR(64), IN definition_value TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=table_name_value AND column_name=column_name_value) THEN
    SET @ddl=CONCAT('ALTER TABLE `',table_name_value,'` ADD COLUMN `',column_name_value,'` ',definition_value);
    PREPARE statement_value FROM @ddl; EXECUTE statement_value; DEALLOCATE PREPARE statement_value;
  END IF;
END$$
DROP PROCEDURE IF EXISTS campushub_copy_legacy_times$$
CREATE PROCEDURE campushub_copy_legacy_times()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='created_at') THEN
    UPDATE users SET create_time=created_at WHERE create_time IS NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='updated_at') THEN
    UPDATE users SET update_time=updated_at WHERE update_time IS NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='notifications' AND column_name='created_at') THEN
    UPDATE notifications SET create_time=created_at WHERE create_time IS NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='second_hand_task') THEN
    INSERT IGNORE INTO t_task_flea(task_id,trade_mode,item_price,requester_payment_code)
      SELECT task_id,trade_mode,item_price,requester_payment_code FROM second_hand_task;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='users' AND column_name='student_id' AND non_unique=0) THEN
    -- 如存在重复学号，此处故意失败，交由人工核对，不删除或合并真实用户。
    ALTER TABLE users ADD CONSTRAINT uk_users_student_id UNIQUE(student_id);
  END IF;
END$$
DELIMITER ;

CALL campushub_add_column('users','student_id','VARCHAR(20) NULL');
CALL campushub_add_column('verification_codes','student_id','VARCHAR(20) NULL');
CALL campushub_add_column('users','create_time','DATETIME(6) NULL');
CALL campushub_add_column('users','update_time','DATETIME(6) NULL');
CALL campushub_add_column('orders','amount_due','DECIMAL(10,2) NULL');
CALL campushub_add_column('orders','payer_id','BIGINT NULL');
CALL campushub_add_column('orders','payee_id','BIGINT NULL');
CALL campushub_add_column('orders','payment_status','ENUM(''not_required'',''unpaid'',''paid'') DEFAULT ''not_required''');
CALL campushub_add_column('orders','settlement_status','ENUM(''not_required'',''pending'',''recorded'') DEFAULT ''not_required''');
CALL campushub_add_column('orders','completion_submitted_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','acceptance_due_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','payment_due_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','overdue_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','escalation_due_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','escalated_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','paid_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','review_eligible_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','settled_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','disputed','BIT NOT NULL DEFAULT 0');
CALL campushub_add_column('orders','disputed_at','DATETIME(6) NULL');
CALL campushub_add_column('orders','dispute_reason','VARCHAR(1000) NULL');
CALL campushub_add_column('reviews','automatic','BIT NOT NULL DEFAULT 0');
CALL campushub_add_column('complaints','resolved','BIT NOT NULL DEFAULT 0');
CALL campushub_add_column('complaints','resolved_by','BIGINT NULL');
CALL campushub_add_column('complaints','resolved_at','DATETIME(6) NULL');
CALL campushub_add_column('complaints','resolution','VARCHAR(500) NULL');
CALL campushub_add_column('notifications','create_time','DATETIME(6) NULL');
CALL campushub_add_column('notifications','read_time','DATETIME(6) NULL');
CALL campushub_add_column('conversation_members','read_at','DATETIME(6) NULL');
CALL campushub_add_column('t_task_tutoring','duration_hours','DECIMAL(6,2) NULL');
CALL campushub_add_column('t_task_flea','trade_mode','VARCHAR(10) DEFAULT ''SELL''');
CALL campushub_add_column('t_task_flea','requester_payment_code','VARCHAR(255) NULL');

-- 保留历史值，仅规范存储范围；不把旧的5分制数据擅自换算成100分制。
ALTER TABLE users MODIFY credit_score INT NOT NULL DEFAULT 100, MODIFY status INT NOT NULL DEFAULT 1;
UPDATE users SET credit_score=GREATEST(0,LEAST(100,credit_score));
ALTER TABLE tasks MODIFY campus VARCHAR(20) NULL;
ALTER TABLE orders MODIFY status VARCHAR(32) NOT NULL DEFAULT 'in_progress';
UPDATE orders SET status='in_progress' WHERE status='accepted';
ALTER TABLE orders MODIFY status ENUM('in_progress','awaiting_acceptance','completed','cancelled') NOT NULL DEFAULT 'in_progress';
ALTER TABLE reviews MODIFY score INT NOT NULL;
ALTER TABLE t_task_flea MODIFY item_price DECIMAL(10,2) NULL;
UPDATE tasks SET reward=NULL WHERE task_type='TEAM';
CALL campushub_copy_legacy_times();
DROP PROCEDURE campushub_add_column;
DROP PROCEDURE campushub_copy_legacy_times;

-- amount_due仍为NULL的旧订单交由管理员核对，不据此追缴或补扣历史分数。
CREATE TABLE IF NOT EXISTS payment_attempts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL, active_slot INT NULL DEFAULT 1,
  out_trade_no VARCHAR(32) NOT NULL UNIQUE,
  channel ENUM('WECHAT','ALIPAY') NOT NULL,
  amount DECIMAL(10,2) NOT NULL, app_id VARCHAR(255), merchant_id VARCHAR(255),
  status ENUM('PENDING','PAID','CLOSED') NOT NULL DEFAULT 'PENDING',
  code_url VARCHAR(1500), provider_transaction_id VARCHAR(100),
  created_at DATETIME(6), paid_at DATETIME(6), last_queried_at DATETIME(6), closed_at DATETIME(6),
  UNIQUE KEY uk_active_payment(order_id,active_slot),
  UNIQUE KEY uk_channel_transaction(channel,provider_transaction_id),
  CONSTRAINT fk_payment_business_order FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS settlement_records (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE,
  payee_id BIGINT, administrator_id BIGINT, amount DECIMAL(10,2) NOT NULL,
  transfer_reference VARCHAR(100) NOT NULL UNIQUE, evidence_filename VARCHAR(100) NOT NULL,
  recorded_at DATETIME(6), CONSTRAINT fk_settlement_order FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS credit_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, event_key VARCHAR(100) NOT NULL UNIQUE,
  user_id BIGINT, previous_score INT NOT NULL, resulting_score INT NOT NULL,
  requested_delta INT NOT NULL, reason VARCHAR(1000), created_at DATETIME(6),
  CONSTRAINT fk_credit_event_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS credit_recovery_runs (
  recovery_day DATE NOT NULL PRIMARY KEY, version BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 升级核对：新支付规则不应自动填充任何历史欠款金额。
SELECT COUNT(*) AS legacy_orders_requiring_review FROM orders WHERE amount_due IS NULL;
