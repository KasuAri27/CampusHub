-- ============================================================================
-- 04 消息集群（chat 分支）
-- ----------------------------------------------------------------------------
-- 表：conversations、conversation_members、messages、notifications
-- 写权限：仅消息集群（chat、notification 包）。其他集群通过
--         NotificationService.createNotification 和 ChatIntegrationService 间接写入
--         （模块契约 §3.1、§3.3），不直接操作这四张表。
-- 依赖：02（外键引用 users）。会话里的 task_id / order_id 只是普通关联字段，
--       不加外键，因此不依赖 03 的建表顺序。
--
-- 会话有三种（conv_type）：
--   TASK_PRIVATE   接单后自动建立的私聊：发布者 + 接单人，一单一个（task_id + order_id 定位）
--   TEAM_GROUP     组队需求审核通过时自动建群，发起人是群主，每加入一人进一名成员
--   SYSTEM_NOTIFY  每个用户一个“系统通知”会话，站内通知会同步发一条系统消息到这里
-- 实时推送走 WebSocket /api/ws/chat，消息先落库再按会话成员广播。
-- ============================================================================

USE campushub;

-- ----------------------------------------------------------------------------
-- conversations 会话
-- 需求完成或取消后，定时任务开始倒计时（当前为测试用的 5 分钟）：先发消息询问是否保留
-- （keep_group=0），再过 5 分钟仍未确认就软删除（is_deleted=1）。
-- 仍有未付款、未结算或争议中订单的会话不会被清理。
-- ----------------------------------------------------------------------------
CREATE TABLE conversations (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '会话ID',
    conv_type       VARCHAR(20)     NOT NULL                 COMMENT '会话类型：TASK_PRIVATE / TEAM_GROUP / SYSTEM_NOTIFY',
    task_id         BIGINT          DEFAULT NULL             COMMENT '关联需求（系统通知会话为空）',
    order_id        BIGINT          DEFAULT NULL             COMMENT '关联订单（仅私聊有）',
    group_name      VARCHAR(50)     DEFAULT NULL             COMMENT '会话名称：群聊为「需求标题」组队群，系统通知为“系统通知”',
    owner_id        BIGINT          DEFAULT NULL             COMMENT '群主（仅群聊有，即组队发起人）',
    max_members     INT             DEFAULT NULL             COMMENT '群人数上限（仅群聊有，含群主）',
    keep_group      INT             DEFAULT NULL             COMMENT '保留确认：NULL=还没问过，0=已询问待确认，1=确认保留',
    keep_asked_at   DATETIME(6)     DEFAULT NULL             COMMENT '发出保留询问的时间',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已解散/软删除',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间，会话列表按它倒序',
    PRIMARY KEY (id),
    KEY idx_conv_type (conv_type),
    KEY idx_conv_task_type (task_id, conv_type),
    CONSTRAINT fk_conv_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话';

-- ----------------------------------------------------------------------------
-- conversation_members 会话成员
-- 谁能看到、能在哪个会话里发消息，全看这张表。用户的会话列表也从这里查。
-- read_at 之后别人发的消息计为未读。
-- ----------------------------------------------------------------------------
CREATE TABLE conversation_members (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '成员记录ID',
    conv_id         BIGINT          NOT NULL                 COMMENT '会话',
    user_id         BIGINT          NOT NULL                 COMMENT '成员',
    role            ENUM('OWNER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '群主 OWNER / 普通成员 MEMBER；私聊双方都是 MEMBER',
    joined_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '加入时间',
    read_at         DATETIME(6)     DEFAULT NULL             COMMENT '最后已读时间，为空表示从未打开过',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_user (conv_id, user_id),
    FOREIGN KEY (conv_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员';

-- ----------------------------------------------------------------------------
-- messages 消息
-- sender_id=0 表示系统消息（建群、入群、接单提示、站内通知同步等），因此不对 users 加外键。
-- 撤回只置 is_recalled=1，不删除原文。
-- ----------------------------------------------------------------------------
CREATE TABLE messages (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    conv_id         BIGINT          NOT NULL                 COMMENT '所属会话',
    sender_id       BIGINT          NOT NULL                 COMMENT '发送者；0=系统',
    msg_type        VARCHAR(20)     NOT NULL DEFAULT 'TEXT'  COMMENT '消息类型：TEXT=文字，IMAGE=图片（content 为图片 URL），SYSTEM=系统提示',
    content         TEXT            NOT NULL                 COMMENT '消息内容',
    is_recalled     TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已撤回',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_conv_created (conv_id, created_at),
    FOREIGN KEY (conv_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息';

-- ----------------------------------------------------------------------------
-- notifications 站内通知
-- 任何业务通过 NotificationService.createNotification 发通知时，同一事务里
-- 写本表一行 + 往接收者的 SYSTEM_NOTIFY 会话发一条“【标题】内容”系统消息。
-- 通知页从本表分页查询（按 create_time 倒序，可只看未读）。
-- ----------------------------------------------------------------------------
CREATE TABLE notifications (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '通知ID',
    receiver_id     BIGINT          NOT NULL                 COMMENT '接收者',
    type            VARCHAR(30)     NOT NULL                 COMMENT '业务事件标签，如 ORDER_ACCEPTED / ACCEPTANCE / ACCEPTED / PAYMENT / OVERDUE / TASK_REVIEW / DISPUTE_RESOLVED',
    title           VARCHAR(100)    NOT NULL                 COMMENT '标题',
    content         TEXT            NOT NULL                 COMMENT '正文',
    is_read         TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已读',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发送时间',
    read_time       DATETIME(6)     DEFAULT NULL             COMMENT '已读时间',
    PRIMARY KEY (id),
    KEY idx_notifications_receiver_time (receiver_id, create_time),
    KEY idx_notifications_receiver_unread (receiver_id, is_read, create_time),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知';
