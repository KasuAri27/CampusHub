-- ============================================================================
-- M003 命名统一与约束收紧（2026-11 用户批准）
-- ----------------------------------------------------------------------------
-- 适用：已执行 M002 的数据库。执行完后继续执行后续编号的升级脚本。
-- 执行：先备份，在数据库副本上验证；用 mysql -D <库名> 选择目标库。可重复执行。
--
-- 改动内容：
--   1. 创建/修改时间列统一改名为 create_time / update_time：
--      categories、tasks、orders、payment_attempts、reviews、credit_events、
--      conversations、messages 中的 created_at / updated_at。
--      注意：JPA 实体仍映射旧列名的，需由各功能分支同步修改，否则启动或读写报错。
--   2. 全部非主键索引改为统一命名：唯一 uk_<表>_<列...>，普通 idx_<表>_<列...>。
--      原先由外键自动生成的索引（如 user_id、fk_conv_owner）一并改为显式名称。
--   3. 收紧非空：credit_events.user_id/create_time、credit_recovery_runs.version、
--      payment_attempts.create_time/app_id/merchant_id、
--      settlement_records.payee_id/administrator_id/recorded_at。
--      这些列在现有业务代码中总会写值；若库中仍有空值，本脚本故意失败，交由人工核对。
--   4. users.nickname 限长：VARCHAR(20) + CHECK，最长 10 个汉字或 20 个英文字符。
--      执行前可用下面的语句找出超长昵称，人工处理后再执行（本脚本不截断用户数据）：
--        SELECT id, nickname FROM users WHERE LENGTH(nickname) + CHAR_LENGTH(nickname) > 40;
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS m003_rename_column$$
CREATE PROCEDURE m003_rename_column(IN t VARCHAR(64), IN old_name VARCHAR(64), IN new_name VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = t AND column_name = old_name)
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = t AND column_name = new_name) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` RENAME COLUMN `', old_name, '` TO `', new_name, '`');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DROP PROCEDURE IF EXISTS m003_rename_index$$
CREATE PROCEDURE m003_rename_index(IN t VARCHAR(64), IN old_name VARCHAR(64), IN new_name VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = t AND index_name = old_name)
     AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = t AND index_name = new_name) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` RENAME INDEX `', old_name, '` TO `', new_name, '`');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DROP PROCEDURE IF EXISTS m003_add_nickname_check$$
CREATE PROCEDURE m003_add_nickname_check()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                 WHERE constraint_schema = DATABASE() AND table_name = 'users'
                   AND constraint_name = 'chk_users_nickname_length') THEN
    ALTER TABLE users ADD CONSTRAINT chk_users_nickname_length
      CHECK (LENGTH(nickname) + CHAR_LENGTH(nickname) <= 40);
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------- 1. 时间列改名
CALL m003_rename_column('categories',       'created_at', 'create_time');
CALL m003_rename_column('tasks',            'created_at', 'create_time');
CALL m003_rename_column('tasks',            'updated_at', 'update_time');
CALL m003_rename_column('orders',           'created_at', 'create_time');
CALL m003_rename_column('orders',           'updated_at', 'update_time');
CALL m003_rename_column('payment_attempts', 'created_at', 'create_time');
CALL m003_rename_column('reviews',          'created_at', 'create_time');
CALL m003_rename_column('credit_events',    'created_at', 'create_time');
CALL m003_rename_column('conversations',    'created_at', 'create_time');
CALL m003_rename_column('conversations',    'updated_at', 'update_time');
CALL m003_rename_column('messages',         'created_at', 'create_time');

-- ---------------------------------------------------------------- 2. 索引改名
CALL m003_rename_index('users',                'uk_email',                            'uk_users_email');
CALL m003_rename_index('users',                'uk_phone',                            'uk_users_phone');
CALL m003_rename_index('credit_events',        'event_key',                           'uk_credit_events_event_key');
CALL m003_rename_index('credit_events',        'fk_credit_event_user',                'idx_credit_events_user_id');
CALL m003_rename_index('verification_codes',   'idx_verification_codes_lookup',       'idx_verification_codes_phone_type_used');
CALL m003_rename_index('categories',           'uk_name',                             'uk_categories_name');
CALL m003_rename_index('tasks',                'idx_requester_id',                    'idx_tasks_requester_id');
CALL m003_rename_index('tasks',                'idx_category_id',                     'idx_tasks_category_id');
CALL m003_rename_index('tasks',                'idx_published_at',                    'idx_tasks_published_at');
CALL m003_rename_index('tasks',                'idx_tasks_status_published',          'idx_tasks_status_published_at');
CALL m003_rename_index('orders',               'idx_requester_id',                    'idx_orders_requester_id');
CALL m003_rename_index('orders',               'idx_provider_id',                     'idx_orders_provider_id');
CALL m003_rename_index('orders',               'idx_orders_task_provider',            'idx_orders_task_id_provider_id');
CALL m003_rename_index('orders',               'idx_orders_status_review',            'idx_orders_status_review_eligible_at');
CALL m003_rename_index('payment_attempts',     'out_trade_no',                        'uk_payment_attempts_out_trade_no');
CALL m003_rename_index('payment_attempts',     'uk_active_payment',                   'uk_payment_attempts_order_id_active_slot');
CALL m003_rename_index('payment_attempts',     'uk_channel_transaction',              'uk_payment_attempts_channel_provider_transaction_id');
CALL m003_rename_index('payment_attempts',     'idx_payment_attempts_status_queried', 'idx_payment_attempts_status_last_queried_at_id');
CALL m003_rename_index('settlement_records',   'order_id',                            'uk_settlement_records_order_id');
CALL m003_rename_index('settlement_records',   'transfer_reference',                  'uk_settlement_records_transfer_reference');
CALL m003_rename_index('reviews',              'uk_order_id',                         'uk_reviews_order_id');
CALL m003_rename_index('complaints',           'idx_order_id',                        'idx_complaints_order_id');
CALL m003_rename_index('conversations',        'idx_conv_type',                       'idx_conversations_conv_type');
CALL m003_rename_index('conversations',        'idx_conv_task_type',                  'idx_conversations_task_id_conv_type');
CALL m003_rename_index('conversations',        'fk_conv_owner',                       'idx_conversations_owner_id');
CALL m003_rename_index('conversation_members', 'uk_conv_user',                        'uk_conversation_members_conv_id_user_id');
CALL m003_rename_index('conversation_members', 'user_id',                             'idx_conversation_members_user_id');
CALL m003_rename_index('messages',             'idx_conv_created',                    'idx_messages_conv_id_create_time');
CALL m003_rename_index('notifications',        'idx_notifications_receiver_time',     'idx_notifications_receiver_id_create_time');
CALL m003_rename_index('notifications',        'idx_notifications_receiver_unread',   'idx_notifications_receiver_id_is_read_create_time');

-- ---------------------------------------------------------------- 3. 收紧非空
ALTER TABLE credit_events
  MODIFY user_id     BIGINT NOT NULL COMMENT '被调整的用户',
  MODIFY create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发生时间';

-- 版本号为空只可能来自手工插入，按初始版本 0 补齐。
UPDATE credit_recovery_runs SET version = 0 WHERE version IS NULL;
ALTER TABLE credit_recovery_runs
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号，插入时为 0';

ALTER TABLE payment_attempts
  MODIFY app_id      VARCHAR(255) NOT NULL COMMENT '下单时使用的渠道 AppID，回调时核对',
  MODIFY merchant_id VARCHAR(255) NOT NULL COMMENT '下单时使用的商户号/卖家号，回调时核对',
  MODIFY create_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下单时间';

ALTER TABLE settlement_records
  MODIFY payee_id         BIGINT      NOT NULL COMMENT '收款人（冗余自订单，便于核对）',
  MODIFY administrator_id BIGINT      NOT NULL COMMENT '登记的管理员',
  MODIFY recorded_at      DATETIME(6) NOT NULL COMMENT '登记时间';

-- ---------------------------------------------------------------- 4. 昵称限长
ALTER TABLE users
  MODIFY nickname VARCHAR(20) NULL COMMENT '昵称：最长 10 个汉字或 20 个英文字符（汉字计 2，英文数字计 1）；为空时显示真实姓名；注册默认 User+学号前四位';
CALL m003_add_nickname_check();

DROP PROCEDURE m003_rename_column;
DROP PROCEDURE m003_rename_index;
DROP PROCEDURE m003_add_nickname_check;
