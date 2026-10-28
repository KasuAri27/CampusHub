-- ============================================
-- CampusHub 建表 SQL
-- 来源：P3 详细设计文档 - 3.2 建表 SQL
-- 表创建顺序遵循外键依赖关系
-- ============================================

USE campushub;

-- --------------------------------------------
-- 1. 用户表 users
-- --------------------------------------------
CREATE TABLE users (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    name            VARCHAR(20)     NOT NULL                 COMMENT '真实姓名',
    email           VARCHAR(100)    NOT NULL                 COMMENT '学校邮箱',
    phone           VARCHAR(255)    NOT NULL                 COMMENT '手机号（AES加密存储）',
    password_hash   VARCHAR(255)    NOT NULL                 COMMENT 'bcrypt哈希密码',
    role            ENUM('requester','provider','admin')
                                    NOT NULL                 COMMENT '用户角色',
    credit_score    DECIMAL(3,1)    NOT NULL DEFAULT 5.0     COMMENT '信用评分',
    avatar          VARCHAR(500)    DEFAULT NULL             COMMENT '头像URL',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '状态：1=正常, 0=禁用',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- --------------------------------------------
-- 2. 需求分类表 categories
-- --------------------------------------------
CREATE TABLE categories (
    id              INT             NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    name            VARCHAR(20)     NOT NULL                 COMMENT '分类名称',
    icon            VARCHAR(255)    DEFAULT NULL             COMMENT '分类图标URL',
    has_review      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否支持评价：1=支持, 0=不支持',
    sort_order      INT             NOT NULL DEFAULT 0       COMMENT '排序权重',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求分类表';

-- --------------------------------------------
-- 3. 需求基础表 tasks（类表继承父表）
-- --------------------------------------------
CREATE TABLE tasks (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '需求ID/基类主键',
    requester_id    BIGINT          NOT NULL                 COMMENT '发布者ID',
    category_id     INT             NOT NULL                 COMMENT '分类ID',
    task_type       VARCHAR(20)     NOT NULL                 COMMENT '多态辨别者：RUNNING/TUTORING/FLEA/TEAM',
    title           VARCHAR(50)     NOT NULL                 COMMENT '需求标题',
    description     VARCHAR(500)    NOT NULL                 COMMENT '详细描述',
    reward          DECIMAL(10,2)   DEFAULT NULL             COMMENT '报酬金额（基类保留，向后兼容）',
    campus          ENUM('鼓楼校区','仙林校区','浦口校区','苏州校区')
                                    DEFAULT NULL             COMMENT '所属校区',
    status          ENUM('pending_review','published','in_progress','completed','cancelled')
                                    NOT NULL DEFAULT 'pending_review' COMMENT '需求状态',
    location        VARCHAR(100)    DEFAULT NULL             COMMENT '地点（基类保留，向后兼容）',
    deadline        DATETIME        DEFAULT NULL             COMMENT '截止时间',
    view_count      INT             NOT NULL DEFAULT 0       COMMENT '浏览量',
    published_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_requester_id (requester_id),
    KEY idx_category_id (category_id),
    KEY idx_status (status),
    KEY idx_published_at (published_at),
    KEY idx_task_type (task_type),
    CONSTRAINT fk_task_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_task_category  FOREIGN KEY (category_id)  REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求基础表（父类）';

-- --------------------------------------------
-- 3a. 跑腿服务子表（ExpressTask）
-- --------------------------------------------
CREATE TABLE t_task_running (
    task_id          BIGINT          PRIMARY KEY              COMMENT '关联tasks表id',
    pickup_location  VARCHAR(100)    NOT NULL                 COMMENT '出发/取货地点',
    delivery_location VARCHAR(100)   NOT NULL                 COMMENT '送达/目的地',
    pickup_code      VARCHAR(50)     DEFAULT NULL             COMMENT '取件码/备注信息',
    sub_reward       DECIMAL(10,2)   NOT NULL                 COMMENT '跑腿费报酬',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跑腿服务专属表';

-- --------------------------------------------
-- 3b. 学习辅导子表（TutoringTask）
-- --------------------------------------------
CREATE TABLE t_task_tutoring (
    task_id          BIGINT          PRIMARY KEY              COMMENT '关联tasks表id',
    subject          VARCHAR(50)     NOT NULL                 COMMENT '辅导科目',
    price_per_hour   DECIMAL(10,2)   NOT NULL                 COMMENT '时薪报酬',
    tutoring_location VARCHAR(100)   NOT NULL                 COMMENT '辅导地点',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习辅导专属表';

-- --------------------------------------------
-- 3c. 二手交易子表（SecondHandTask）
-- --------------------------------------------
CREATE TABLE t_task_flea (
    task_id          BIGINT          PRIMARY KEY              COMMENT '关联tasks表id',
    item_price       DECIMAL(10,2)   NOT NULL                 COMMENT '物品售价',
    item_condition   VARCHAR(20)     NOT NULL DEFAULT '九成新' COMMENT '物品成色',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二手交易专属表';

-- --------------------------------------------
-- 3d. 活动组队子表（TeamingTask）
-- --------------------------------------------
CREATE TABLE t_task_team (
    task_id          BIGINT          PRIMARY KEY              COMMENT '关联tasks表id',
    max_members      INT             NOT NULL DEFAULT 2       COMMENT '成团人数上限',
    chat_group_id    BIGINT          DEFAULT NULL             COMMENT '关联群聊ID（预留）',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动组队专属表';

-- --------------------------------------------
-- 3e. 信息咨询子表（ConsultingTask）
-- --------------------------------------------
CREATE TABLE t_task_consulting (
                                   task_id          BIGINT          PRIMARY KEY              COMMENT '关联 tasks id',
                                   consulting_mode  VARCHAR(20)     NOT NULL DEFAULT 'ONLINE' COMMENT '咨询方式: ONLINE/OFFLINE',
                                   FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信息咨询任务表';

-- --------------------------------------------
-- 4. 订单表 orders
-- --------------------------------------------
CREATE TABLE orders (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    task_id         BIGINT          NOT NULL                 COMMENT '关联需求ID',
    requester_id    BIGINT          NOT NULL                 COMMENT '需求方ID',
    provider_id     BIGINT          NOT NULL                 COMMENT '服务方/参与者ID',
    status          ENUM('accepted','in_progress','completed','cancelled')
                                    NOT NULL DEFAULT 'accepted' COMMENT '订单状态',
    accepted_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接单/参与时间',
    completed_at    DATETIME        DEFAULT NULL             COMMENT '完成时间',
    cancelled_at    DATETIME        DEFAULT NULL             COMMENT '取消时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_requester_id (requester_id),
    KEY idx_provider_id (provider_id),
    KEY idx_status (status),
    CONSTRAINT fk_order_task      FOREIGN KEY (task_id)      REFERENCES tasks(id),
    CONSTRAINT fk_order_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_order_provider  FOREIGN KEY (provider_id)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单/参与记录表';

-- --------------------------------------------
-- 5. 评价表 reviews
-- --------------------------------------------
CREATE TABLE reviews (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '评价ID',
    order_id        BIGINT          NOT NULL                 COMMENT '关联订单ID',
    score           TINYINT         NOT NULL                 COMMENT '评分，1-5',
    comment         VARCHAR(200)    DEFAULT NULL             COMMENT '评价内容',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_score CHECK (score >= 1 AND score <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- --------------------------------------------
-- 6. 通知表 notifications
-- --------------------------------------------
CREATE TABLE notifications (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '通知ID',
    receiver_id     BIGINT          NOT NULL                 COMMENT '接收者ID',
    type            VARCHAR(30)     NOT NULL                 COMMENT '通知类型',
    title           VARCHAR(100)    NOT NULL                 COMMENT '通知标题',
    content         TEXT            NOT NULL                 COMMENT '通知内容',
    is_read         TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已读',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_receiver_read (receiver_id, is_read),
    KEY idx_created_at (created_at),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- --------------------------------------------
-- 8. 投诉表 complaints
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS complaints (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '投诉ID',
    complainant_id  BIGINT          NOT NULL                COMMENT '投诉发起人ID',
    order_id        BIGINT          NOT NULL                COMMENT '被投诉订单ID',
    target_id       BIGINT          NOT NULL                COMMENT '被投诉人ID',
    reason          VARCHAR(100)    NOT NULL                COMMENT '投诉理由',
    description     VARCHAR(500)    DEFAULT NULL            COMMENT '详细描述',
    image_url       VARCHAR(500)    DEFAULT NULL            COMMENT '证据图片URL',
    create_time     DATETIME(6)     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    CONSTRAINT fk_complaint_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉表';

-- --------------------------------------------
-- 9. 验证码表 verification_codes
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS verification_codes (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '验证码ID',
    phone           VARCHAR(11)     NOT NULL                 COMMENT '手机号',
    code            VARCHAR(6)      NOT NULL                 COMMENT '验证码',
    type            VARCHAR(20)     NOT NULL                 COMMENT '类型：REGISTER/LOGIN/BIND',
    expire_time     DATETIME        NOT NULL                 COMMENT '过期时间',
    used            TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已使用',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- --------------------------------------------
-- 10. 会话表 conversations
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '会话ID',
    conv_type       VARCHAR(20)     NOT NULL                 COMMENT 'TASK_PRIVATE / TEAM_GROUP / SYSTEM_NOTIFY',
    task_id         BIGINT          DEFAULT NULL             COMMENT '关联需求ID（系统通知为NULL）',
    order_id        BIGINT          DEFAULT NULL             COMMENT '关联订单ID',
    group_name      VARCHAR(50)     DEFAULT NULL             COMMENT '群名称',
    owner_id        BIGINT          DEFAULT NULL             COMMENT '群主ID',
    max_members     INT             DEFAULT NULL             COMMENT '人数上限',
    keep_group      TINYINT(1)      DEFAULT NULL             COMMENT '是否保留群聊',
    keep_asked_at   DATETIME        DEFAULT NULL             COMMENT '保留询问时间',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已被软删除：1=是, 0=否',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_conv_type (conv_type),
    CONSTRAINT fk_conv_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- --------------------------------------------
-- 11. 会话成员表 conversation_members
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_members (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    conv_id         BIGINT          NOT NULL                 COMMENT '会话ID',
    user_id         BIGINT          NOT NULL                 COMMENT '成员ID',
    role            ENUM('OWNER','MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_user (conv_id, user_id),
    FOREIGN KEY (conv_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员';

-- --------------------------------------------
-- 12. 消息表 messages
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    conv_id         BIGINT          NOT NULL                 COMMENT '所属会话',
    sender_id       BIGINT          NOT NULL                 COMMENT '发送者（0=系统）',
    msg_type        ENUM('TEXT','SYSTEM','IMAGE') NOT NULL DEFAULT 'TEXT',
    content         TEXT            NOT NULL                 COMMENT '消息内容',
    is_recalled     TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否撤回',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv_created (conv_id, created_at),
    FOREIGN KEY (conv_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';
