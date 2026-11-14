-- ============================================================================
-- M002 结构整理：把已执行过 M001 的旧库升级到与 02-04 全新建库一致的结构
-- ----------------------------------------------------------------------------
-- 适用：按旧版 02-create-tables.sql 建库、并已执行 M001-payment-upgrade.sql 的数据库。
-- 执行：先备份，在数据库副本上验证；用 mysql -D <库名> 选择目标库，不要在本文件写 USE。
--       DDL 会隐式提交，失败时停止发布并按备份核查。本脚本可重复执行。
--
-- 改动内容：
--   1. 删除重复的旧时间列：users.created_at/updated_at、notifications.created_at
--      （M001 已把数据复制到 create_time/update_time，本脚本再兜底复制一次），
--      以及实体未映射、旧种子脚本残留的 users.password。
--   2. 时间列统一为 DATETIME(6)；布尔列统一为 TINYINT(1)；
--      messages.msg_type 由 ENUM 改为 VARCHAR(20)，与实体的字符串字段一致；
--      conversations.keep_group 改为 INT，与实体的 Integer 三态一致。
--   3. M001 新建的表补上 utf8mb4_unicode_ci 排序规则。
--   4. 按实际查询重建索引：
--        verification_codes  + (phone, type, used)
--        notifications       + (receiver_id, create_time)、(receiver_id, is_read, create_time)
--                            - idx_receiver_read、idx_created_at
--        payment_attempts    + (status, last_queried_at, id)
--        orders              + (task_id, provider_id)、(status, review_eligible_at)
--                            - idx_task_id、idx_status
--        tasks               + (status, published_at)、(status, deadline)
--                            - idx_status、idx_task_type
--        conversations       + (task_id, conv_type)  - idx_task_id
--   5. users 增加信用分 0-100 的 CHECK 约束。
--
-- 故意保留、不删除的历史资料（新建库没有这些列/表，属于正常差异）：
--   t_task_flea.item_condition、t_task_flea.requester_payment_code、
--   orders.payment_code（旧收款凭证）、second_hand_task 表（旧二手数据原表）。
--   它们只供人工核对，应用不读写；是否清理须另行确认。
--
-- 列注释只写在 02-04 中；本脚本仅为被修改的列补写注释。
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS m002_add_column$$
CREATE PROCEDURE m002_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN definition_value TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', definition_value);
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DROP PROCEDURE IF EXISTS m002_drop_column$$
CREATE PROCEDURE m002_drop_column(IN t VARCHAR(64), IN c VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` DROP COLUMN `', c, '`');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DROP PROCEDURE IF EXISTS m002_add_index$$
CREATE PROCEDURE m002_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN columns_value TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = t AND index_name = i) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD INDEX `', i, '` (', columns_value, ')');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

-- 删除前必须已有可替代的索引，否则被外键占用的索引会删除失败；调用顺序保证先加后删。
DROP PROCEDURE IF EXISTS m002_drop_index$$
CREATE PROCEDURE m002_drop_index(IN t VARCHAR(64), IN i VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = t AND index_name = i) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` DROP INDEX `', i, '`');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

-- 旧时间列存在时，把仍为空的新列补齐，再整体兜底为当前时间，保证随后改 NOT NULL 不失败。
DROP PROCEDURE IF EXISTS m002_backfill_times$$
CREATE PROCEDURE m002_backfill_times()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'created_at') THEN
    UPDATE users SET create_time = created_at WHERE create_time IS NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'updated_at') THEN
    UPDATE users SET update_time = updated_at WHERE update_time IS NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'created_at') THEN
    UPDATE notifications SET create_time = created_at WHERE create_time IS NULL;
  END IF;
  UPDATE users SET create_time = CURRENT_TIMESTAMP(6) WHERE create_time IS NULL;
  UPDATE users SET update_time = create_time WHERE update_time IS NULL;
  UPDATE notifications SET create_time = CURRENT_TIMESTAMP(6) WHERE create_time IS NULL;
  UPDATE complaints SET create_time = CURRENT_TIMESTAMP(6) WHERE create_time IS NULL;
END$$

DROP PROCEDURE IF EXISTS m002_add_credit_check$$
CREATE PROCEDURE m002_add_credit_check()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                 WHERE constraint_schema = DATABASE() AND table_name = 'users'
                   AND constraint_name = 'chk_users_credit_score') THEN
    -- M001 已把旧分数钳制到 0-100；若仍有越界数据，这里故意失败，交由人工核对。
    ALTER TABLE users ADD CONSTRAINT chk_users_credit_score CHECK (credit_score BETWEEN 0 AND 100);
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------- 账号集群
CALL m002_add_column('users', 'nickname', 'VARCHAR(255) NULL');
CALL m002_backfill_times();
CALL m002_drop_column('users', 'created_at');
CALL m002_drop_column('users', 'updated_at');
-- 旧种子脚本写过的 password 列与 password_hash 内容相同，实体只映射 password_hash。
CALL m002_drop_column('users', 'password');
ALTER TABLE users
  MODIFY student_id   VARCHAR(20)  NULL COMMENT '学号：密码登录账号，注册时绑定；早期账号可能为空',
  MODIFY nickname     VARCHAR(255) NULL COMMENT '昵称：聊天和列表优先展示，为空时显示真实姓名；注册默认 User+学号前四位',
  MODIFY role         ENUM('requester','provider','admin') NOT NULL COMMENT '角色：requester=普通用户（可发布也可接单），admin=管理员；provider 为早期设计遗留，现注册不再产生',
  MODIFY credit_score INT NOT NULL DEFAULT 100 COMMENT '信用分 0-100：低于 70 禁止发布和接单',
  MODIFY status       INT NOT NULL DEFAULT 1 COMMENT '账号状态：1=正常，0=封禁（受限登录）',
  MODIFY create_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '注册时间',
  MODIFY update_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后修改时间';
CALL m002_add_credit_check();

ALTER TABLE credit_events        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE credit_recovery_runs CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE verification_codes
  MODIFY expire_time DATETIME(6) NOT NULL COMMENT '过期时间',
  MODIFY create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生成时间';
CALL m002_add_index('verification_codes', 'idx_verification_codes_lookup', 'phone, type, used');

-- ---------------------------------------------------------------- 交易核心
ALTER TABLE categories
  MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

-- M001 改类型时丢了 campus / orders.status / reviews.score / item_price 的列注释，这里一并补回。
ALTER TABLE tasks
  MODIFY campus       VARCHAR(20) NULL COMMENT '校区：鼓楼校区/仙林校区/浦口校区/苏州校区',
  MODIFY deadline    DATETIME(6) NULL COMMENT '截止时间：过了截止仍未接单会被自动取消，也不能再接单',
  MODIFY published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发布时间；审核通过时刷新为通过时刻，广场按它倒序',
  MODIFY created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后修改时间；群聊清理以它作为任务结束时刻';
CALL m002_add_index('tasks', 'idx_tasks_status_published', 'status, published_at');
CALL m002_add_index('tasks', 'idx_tasks_status_deadline', 'status, deadline');
CALL m002_drop_index('tasks', 'idx_status');
CALL m002_drop_index('tasks', 'idx_task_type');

-- 若 t_task_flea 是 M001 兜底新建的，它的排序规则也需要统一。
ALTER TABLE t_task_flea CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
UPDATE t_task_flea SET trade_mode = 'SELL' WHERE trade_mode IS NULL;
ALTER TABLE t_task_flea
  MODIFY item_price DECIMAL(10,2) NULL COMMENT '约定成交总价（元）；为空的旧需求不能接单',
  MODIFY trade_mode VARCHAR(10) NOT NULL DEFAULT 'SELL' COMMENT '交易方向：SELL=发布者出售，BUY=发布者求购';

UPDATE orders SET payment_status = 'not_required' WHERE payment_status IS NULL;
UPDATE orders SET settlement_status = 'not_required' WHERE settlement_status IS NULL;
ALTER TABLE orders
  MODIFY status            ENUM('in_progress','awaiting_acceptance','completed','cancelled') NOT NULL DEFAULT 'in_progress' COMMENT '履约状态，流转见表注释',
  MODIFY accepted_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '接单/加入时间',
  MODIFY completed_at      DATETIME(6) NULL COMMENT '验收完成时间',
  MODIFY cancelled_at      DATETIME(6) NULL COMMENT '取消时间',
  MODIFY created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后修改时间',
  MODIFY payment_status    ENUM('not_required','unpaid','paid') NOT NULL DEFAULT 'not_required' COMMENT '付款状态',
  MODIFY settlement_status ENUM('not_required','pending','recorded') NOT NULL DEFAULT 'not_required' COMMENT '人工结算状态；recorded 只表示已登记线下转账，不是渠道自动打款',
  MODIFY disputed          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否处于争议中：1=暂停自动验收、扣分和升级';
CALL m002_add_index('orders', 'idx_orders_task_provider', 'task_id, provider_id');
CALL m002_add_index('orders', 'idx_orders_status_review', 'status, review_eligible_at');
CALL m002_drop_index('orders', 'idx_task_id');
CALL m002_drop_index('orders', 'idx_status');

ALTER TABLE payment_attempts   CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE settlement_records CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CALL m002_add_index('payment_attempts', 'idx_payment_attempts_status_queried', 'status, last_queried_at, id');

ALTER TABLE reviews
  MODIFY score      INT NOT NULL COMMENT '评分 1-5',
  MODIFY automatic TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=超时系统默认四星，0=用户手动评价',
  MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '评价时间';

ALTER TABLE complaints
  MODIFY create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '投诉时间',
  MODIFY resolved    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已处理';

-- ---------------------------------------------------------------- 消息集群
ALTER TABLE conversations
  MODIFY keep_group    INT NULL COMMENT '保留确认：NULL=还没问过，0=已询问待确认，1=确认保留',
  MODIFY keep_asked_at DATETIME(6) NULL COMMENT '发出保留询问的时间',
  MODIFY created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间，会话列表按它倒序';
CALL m002_add_index('conversations', 'idx_conv_task_type', 'task_id, conv_type');
CALL m002_drop_index('conversations', 'idx_task_id');

ALTER TABLE conversation_members
  MODIFY joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '加入时间';

ALTER TABLE messages
  MODIFY msg_type   VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT=文字，IMAGE=图片（content 为图片 URL），SYSTEM=系统提示',
  MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发送时间';

CALL m002_add_index('notifications', 'idx_notifications_receiver_time', 'receiver_id, create_time');
CALL m002_add_index('notifications', 'idx_notifications_receiver_unread', 'receiver_id, is_read, create_time');
CALL m002_drop_index('notifications', 'idx_receiver_read');
CALL m002_drop_index('notifications', 'idx_created_at');
CALL m002_drop_column('notifications', 'created_at');
ALTER TABLE notifications
  MODIFY create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发送时间',
  MODIFY read_time   DATETIME(6) NULL COMMENT '已读时间';

DROP PROCEDURE m002_add_column;
DROP PROCEDURE m002_drop_column;
DROP PROCEDURE m002_add_index;
DROP PROCEDURE m002_drop_index;
DROP PROCEDURE m002_backfill_times;
DROP PROCEDURE m002_add_credit_check;
