-- ============================================================================
-- 03 交易核心（task 分支）
-- ----------------------------------------------------------------------------
-- 表：categories（含字典数据）、tasks 及五张需求子表、orders、payment_attempts、
--     settlement_records、reviews、complaints
-- 写权限：仅交易核心（task/order/payment/review/complaint/category 包）。
--         admin 只读聚合，写入须经交易核心的 Service（模块契约 §2、§7）。
-- 依赖：02（外键引用 users）。
--
-- 一笔交易的数据走向：
--   发布需求 → tasks + 一张子表（pending_review）→ 管理员审核 → published
--   → 有人接单 → orders（冻结金额和付款方向）→ 服务方提交完成 → 需求方验收
--   → 有偿单付款 payment_attempts → 管理员线下转账后登记 settlement_records
--   → 需求方评价 reviews；有纠纷时发起 complaints。
-- 订单的履约状态、付款状态、结算状态三条线分开记录，completed 不代表已付款。
-- ============================================================================

USE campushub;

-- ----------------------------------------------------------------------------
-- categories 需求分类（字典表）
-- 发布需求时选择的分类，决定广场筛选项以及订单完成后能否评价。
-- 预置数据在本文件末尾，属于业务必需数据，不是测试数据。
-- ----------------------------------------------------------------------------
CREATE TABLE categories (
    id              INT             NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    name            VARCHAR(20)     NOT NULL                 COMMENT '分类名称',
    icon            VARCHAR(255)    DEFAULT NULL             COMMENT '分类图标 URL',
    has_review      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '该分类订单是否开放评价：1=开放（七天未评自动四星），0=不开放',
    sort_order      INT             NOT NULL DEFAULT 0       COMMENT '前端展示顺序，越小越靠前',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求分类字典';

-- ----------------------------------------------------------------------------
-- tasks 需求（父表）
-- 所有类型需求的公共字段。采用 JPA JOINED 继承：每条需求在本表一行，
-- 同时在 task_type 对应的子表有一行，主键相同：
--   RUNNING → t_task_running   TUTORING → t_task_tutoring   FLEA → t_task_flea
--   TEAM    → t_task_team      CONSULTING → t_task_consulting
-- 状态流转：pending_review --审核通过--> published --接单--> in_progress --验收--> completed
--           审核驳回 → rejected（保留驳回理由）--发布者修改后重新提交--> pending_review；
--           发布者取消、截止仍无人接单（定时任务）进入 cancelled。2026-11-20 起驳回不再直接置 cancelled，历史数据不转换。
--           组队需求未满员时保持 published，允许继续有人加入。
-- reward 是本需求的约定总价，接单时原样冻结到 orders.amount_due：
--   RUNNING=跑腿费，TUTORING=时薪×时长（分位四舍五入），FLEA=约定成交价，
--   CONSULTING=咨询费（为空表示免费），TEAM 恒为空（组队只做无偿招募）。
-- ----------------------------------------------------------------------------
CREATE TABLE tasks (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '需求ID，也是子表主键',
    requester_id    BIGINT          NOT NULL                 COMMENT '发布者',
    category_id     INT             NOT NULL                 COMMENT '分类',
    task_type       VARCHAR(20)     NOT NULL                 COMMENT '需求类型（决定子表）：RUNNING/TUTORING/FLEA/TEAM/CONSULTING',
    title           VARCHAR(50)     NOT NULL                 COMMENT '标题',
    description     VARCHAR(500)    NOT NULL                 COMMENT '详细描述',
    reward          DECIMAL(10,2)   DEFAULT NULL             COMMENT '约定总价（元），规则见表注释',
    campus          VARCHAR(20)     DEFAULT NULL             COMMENT '校区：鼓楼校区/仙林校区/浦口校区/苏州校区',
    status          ENUM('pending_review','published','in_progress','completed','cancelled','rejected')
                                    NOT NULL DEFAULT 'pending_review' COMMENT '需求状态，流转见表注释',
    location        VARCHAR(100)    DEFAULT NULL             COMMENT '展示用地点；跑腿为“取货地 → 送达地”',
    deadline        DATETIME(6)     DEFAULT NULL             COMMENT '截止时间：过了截止仍未接单会被自动取消，也不能再接单',
    view_count      INT             NOT NULL DEFAULT 0       COMMENT '浏览量',
    published_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发布时间；审核通过时刷新为通过时刻，广场按它倒序',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后修改时间；群聊清理以它作为任务结束时刻',
    reject_reason   VARCHAR(200)    DEFAULT NULL             COMMENT '最近一次驳回理由；仅 rejected 状态有值，重新提交后清空',
    rejected_at     DATETIME(6)     DEFAULT NULL             COMMENT '最近一次被驳回的时间；重新提交后清空',
    PRIMARY KEY (id),
    KEY idx_tasks_requester_id (requester_id),
    KEY idx_tasks_category_id (category_id),
    KEY idx_tasks_published_at (published_at),
    KEY idx_tasks_status_published_at (status, published_at),
    KEY idx_tasks_status_deadline (status, deadline),
    CONSTRAINT fk_task_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_task_category  FOREIGN KEY (category_id)  REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求（父表）';

-- ----------------------------------------------------------------------------
-- t_task_running 跑腿子表（task_type=RUNNING）
-- ----------------------------------------------------------------------------
CREATE TABLE t_task_running (
    task_id           BIGINT        PRIMARY KEY              COMMENT '需求ID（= tasks.id）',
    pickup_location   VARCHAR(100)  NOT NULL                 COMMENT '取货/出发地点',
    delivery_location VARCHAR(100)  NOT NULL                 COMMENT '送达地点',
    pickup_code       VARCHAR(50)   DEFAULT NULL             COMMENT '取件码或备注，仅接单后可见',
    sub_reward        DECIMAL(10,2) NOT NULL                 COMMENT '跑腿费（元），与 tasks.reward 相同',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跑腿需求';

-- ----------------------------------------------------------------------------
-- t_task_tutoring 辅导子表（task_type=TUTORING）
-- 总价 = 时薪 × 约定时长，发布时算好写入 tasks.reward。
-- ----------------------------------------------------------------------------
CREATE TABLE t_task_tutoring (
    task_id           BIGINT        PRIMARY KEY              COMMENT '需求ID（= tasks.id）',
    subject           VARCHAR(50)   NOT NULL                 COMMENT '辅导科目',
    price_per_hour    DECIMAL(10,2) NOT NULL                 COMMENT '时薪（元/小时）',
    tutoring_location VARCHAR(100)  NOT NULL                 COMMENT '辅导地点',
    duration_hours    DECIMAL(6,2)  DEFAULT NULL             COMMENT '约定时长（小时，0-1000）；为空的旧需求不能接单，需重新发布',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习辅导需求';

-- ----------------------------------------------------------------------------
-- t_task_flea 二手子表（task_type=FLEA）
-- trade_mode 决定付款方向，不能按“发布者付款”推断：
--   SELL 发布者卖：接单人是买家，接单人付款给发布者；
--   BUY  发布者买：发布者付款给接单人。
-- ----------------------------------------------------------------------------
CREATE TABLE t_task_flea (
    task_id           BIGINT        PRIMARY KEY              COMMENT '需求ID（= tasks.id）',
    item_price        DECIMAL(10,2) DEFAULT NULL             COMMENT '约定成交总价（元）；为空的旧需求不能接单',
    trade_mode        VARCHAR(10)   NOT NULL DEFAULT 'SELL'  COMMENT '交易方向：SELL=发布者出售，BUY=发布者求购',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二手交易需求';

-- ----------------------------------------------------------------------------
-- t_task_team 组队子表（task_type=TEAM）
-- 无偿招募：订单金额为 0，不涉及付款和评价。
-- 管理员审核通过时自动建群（conversations.conv_type=TEAM_GROUP，task_id 指向本需求）。
-- 每有一人加入生成一张订单；人数达到上限后需求转为 in_progress。
-- ----------------------------------------------------------------------------
CREATE TABLE t_task_team (
    task_id           BIGINT        PRIMARY KEY              COMMENT '需求ID（= tasks.id）',
    max_members       INT           NOT NULL DEFAULT 2       COMMENT '人数上限，包含发起人自己',
    chat_group_id     BIGINT        DEFAULT NULL             COMMENT '预留字段，目前不写入；群聊通过 conversations.task_id 关联',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动组队需求';

-- ----------------------------------------------------------------------------
-- t_task_consulting 咨询子表（task_type=CONSULTING）
-- 是否收费看 tasks.reward：为空或 0 即免费。
-- ----------------------------------------------------------------------------
CREATE TABLE t_task_consulting (
    task_id           BIGINT        PRIMARY KEY              COMMENT '需求ID（= tasks.id）',
    consulting_mode   VARCHAR(20)   NOT NULL DEFAULT 'ONLINE' COMMENT '咨询方式：ONLINE=线上，OFFLINE=线下',
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信息咨询需求';

-- ----------------------------------------------------------------------------
-- orders 订单（组队时为参与记录）
-- 接单时生成，同时冻结金额与付款方向：amount_due 取自 tasks.reward，payer/payee 按需求类型判定。
-- requester/provider 是“发布者/接单人”，payer/payee 是“付款人/收款人”，二手出售时两者相反。
--
-- 三条状态线分开记录：
--   履约 status：in_progress --收款方提交完成--> awaiting_acceptance --付款方验收或24小时自动验收--> completed
--                in_progress 时双方可取消 → cancelled
--   付款 payment_status：not_required（金额为 0）/ unpaid（验收后待付款）/ paid（渠道验签或查单确认）
--   结算 settlement_status：not_required / pending（等待管理员线下转账）/ recorded（已登记转账凭证）
--
-- 时间线（有偿单、无异议）：提交完成 → 24h 自动验收 → 验收后 1h 内付款
--   → 超时标记逾期并对付款人一次性扣 10 分 → 再过 24h 仍未付款升级给管理员。
-- 付款方对“服务未完成/质量不符”提出异议后 disputed=1，暂停自动验收、扣分和升级计时，
-- 由管理员裁决。
-- 定时任务按 acceptance_due_at、payment_due_at、escalation_due_at、review_eligible_at 扫描。
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id                      BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    task_id                 BIGINT          NOT NULL                 COMMENT '所属需求',
    requester_id            BIGINT          NOT NULL                 COMMENT '需求发布者',
    provider_id             BIGINT          NOT NULL                 COMMENT '接单人/组队参与者',
    status                  ENUM('in_progress','awaiting_acceptance','completed','cancelled')
                                            NOT NULL DEFAULT 'in_progress' COMMENT '履约状态，流转见表注释',
    accepted_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '接单/加入时间',
    completed_at            DATETIME(6)     DEFAULT NULL             COMMENT '验收完成时间',
    cancelled_at            DATETIME(6)     DEFAULT NULL             COMMENT '取消时间',
    create_time             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后修改时间',
    amount_due              DECIMAL(10,2)   DEFAULT NULL             COMMENT '应付金额（元），接单时冻结；为空的是规则上线前的历史订单，须人工核对',
    payer_id                BIGINT          DEFAULT NULL             COMMENT '付款人（同时是验收方），二手出售时是接单人',
    payee_id                BIGINT          DEFAULT NULL             COMMENT '收款人（同时是提交完成的一方）',
    payment_status          ENUM('not_required','unpaid','paid')
                                            NOT NULL DEFAULT 'not_required' COMMENT '付款状态',
    settlement_status       ENUM('not_required','pending','recorded')
                                            NOT NULL DEFAULT 'not_required' COMMENT '人工结算状态；recorded 只表示已登记线下转账，不是渠道自动打款',
    completion_submitted_at DATETIME(6)     DEFAULT NULL             COMMENT '收款方提交完成的时间',
    acceptance_due_at       DATETIME(6)     DEFAULT NULL             COMMENT '自动验收时刻（提交完成 + 24h）',
    payment_due_at          DATETIME(6)     DEFAULT NULL             COMMENT '付款截止（验收 + 1h）',
    overdue_at              DATETIME(6)     DEFAULT NULL             COMMENT '被标记逾期并扣分的时刻；非空即已扣过分',
    escalation_due_at       DATETIME(6)     DEFAULT NULL             COMMENT '升级管理员时刻（逾期 + 24h）',
    escalated_at            DATETIME(6)     DEFAULT NULL             COMMENT '实际升级给管理员的时刻',
    paid_at                 DATETIME(6)     DEFAULT NULL             COMMENT '渠道确认的付款时间',
    review_eligible_at      DATETIME(6)     DEFAULT NULL             COMMENT '开放评价时刻：有偿单为付款时间，无偿单为验收时间；此后 7 天未评自动四星',
    settled_at              DATETIME(6)     DEFAULT NULL             COMMENT '管理员登记结算的时间',
    disputed                TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否处于争议中：1=暂停自动验收、扣分和升级',
    disputed_at             DATETIME(6)     DEFAULT NULL             COMMENT '发起争议时间',
    dispute_reason          VARCHAR(1000)   DEFAULT NULL             COMMENT '争议原因',
    PRIMARY KEY (id),
    KEY idx_orders_requester_id (requester_id),
    KEY idx_orders_provider_id (provider_id),
    KEY idx_orders_task_id_provider_id (task_id, provider_id),
    KEY idx_orders_status_review_eligible_at (status, review_eligible_at),
    CONSTRAINT fk_order_task      FOREIGN KEY (task_id)      REFERENCES tasks(id),
    CONSTRAINT fk_order_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_order_provider  FOREIGN KEY (provider_id)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单/组队参与记录';

-- ----------------------------------------------------------------------------
-- payment_attempts 支付单
-- 一行对应一次向微信/支付宝下单（Native 扫码 / 当面付）。只有后台验签通知或主动查单
-- 才能把它改成 PAID，前端跳转、二维码生成、请求超时都不算付款。
-- * 每个订单同一时刻最多一张“有效”支付单：有效单 active_slot=1，
--   (order_id, active_slot) 唯一；渠道确认关闭后置 NULL，才允许重新下单。
-- * 失败重试沿用原单，不改金额，避免旧二维码仍按旧金额收款。
-- * PENDING 的单由定时任务按 last_queried_at 轮询查单，补偿丢失的回调。
-- ----------------------------------------------------------------------------
CREATE TABLE payment_attempts (
    id                      BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '支付单ID',
    order_id                BIGINT          NOT NULL                 COMMENT '对应订单',
    active_slot             INT             NULL DEFAULT 1           COMMENT '有效单占位：1=当前有效单，NULL=已关闭的历史单',
    out_trade_no            VARCHAR(32)     NOT NULL                 COMMENT '商户订单号（发给渠道的单号，32 位无横线 UUID）',
    channel                 ENUM('WECHAT','ALIPAY') NOT NULL         COMMENT '支付渠道',
    amount                  DECIMAL(10,2)   NOT NULL                 COMMENT '下单金额（元），必须等于订单 amount_due',
    app_id                  VARCHAR(255)    NOT NULL                 COMMENT '下单时使用的渠道 AppID，回调时核对',
    merchant_id             VARCHAR(255)    NOT NULL                 COMMENT '下单时使用的商户号/卖家号，回调时核对',
    status                  ENUM('PENDING','PAID','CLOSED') NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=待支付，PAID=渠道确认已付，CLOSED=渠道确认已关闭',
    code_url                VARCHAR(1500)   DEFAULT NULL             COMMENT '付款二维码内容',
    provider_transaction_id VARCHAR(100)    DEFAULT NULL             COMMENT '渠道交易号（微信 transaction_id / 支付宝 trade_no）',
    create_time             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下单时间',
    paid_at                 DATETIME(6)     DEFAULT NULL             COMMENT '渠道返回的付款时间',
    last_queried_at         DATETIME(6)     DEFAULT NULL             COMMENT '最近一次主动查单时间',
    closed_at               DATETIME(6)     DEFAULT NULL             COMMENT '关闭时间',
    UNIQUE KEY uk_payment_attempts_out_trade_no (out_trade_no),
    UNIQUE KEY uk_payment_attempts_order_id_active_slot (order_id, active_slot),
    UNIQUE KEY uk_payment_attempts_channel_provider_transaction_id (channel, provider_transaction_id),
    KEY idx_payment_attempts_status_last_queried_at_id (status, last_queried_at, id),
    CONSTRAINT fk_payment_business_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单';

-- ----------------------------------------------------------------------------
-- settlement_records 人工结算登记
-- 普通商户未开通自动打款：管理员先在线下实际转账给收款人，再在后台登记流水号、
-- 金额（须等于订单全额，平台不抽佣）和转账凭证。一单最多登记一次。
-- 凭证文件存放在 private-storage/settlements，只能通过管理员接口下载，不公开映射。
-- ----------------------------------------------------------------------------
CREATE TABLE settlement_records (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '登记ID',
    order_id            BIGINT          NOT NULL                 COMMENT '对应订单',
    payee_id            BIGINT          NOT NULL                 COMMENT '收款人（冗余自订单，便于核对）',
    administrator_id    BIGINT          NOT NULL                 COMMENT '登记的管理员',
    amount              DECIMAL(10,2)   NOT NULL                 COMMENT '实际转账金额（元）',
    transfer_reference  VARCHAR(100)    NOT NULL                 COMMENT '线下转账流水号，防止同一笔转账登记两次',
    evidence_filename   VARCHAR(100)    NOT NULL                 COMMENT '凭证文件名（private-storage/settlements 下）',
    recorded_at         DATETIME(6)     NOT NULL                 COMMENT '登记时间',
    UNIQUE KEY uk_settlement_records_order_id (order_id),
    UNIQUE KEY uk_settlement_records_transfer_reference (transfer_reference),
    CONSTRAINT fk_settlement_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工结算登记';

-- ----------------------------------------------------------------------------
-- reviews 评价
-- 付款方（需求验收方）单向评价收款方，一单一评。
-- 开放时机见 orders.review_eligible_at；所属分类 has_review=0 的订单不开放评价。
-- 7 天未评由定时任务写入默认四星（automatic=1），不加减分。
-- 手动评分影响收款方信用：五星 +1，一二星 -1，三四星不变（流水键 review:{订单ID}）。
-- ----------------------------------------------------------------------------
CREATE TABLE reviews (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '评价ID',
    order_id        BIGINT          NOT NULL                 COMMENT '被评价的订单',
    score           INT             NOT NULL                 COMMENT '评分 1-5',
    comment         VARCHAR(200)    DEFAULT NULL             COMMENT '评价内容',
    automatic       TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '1=超时系统默认四星，0=用户手动评价',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '评价时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_order_id (order_id),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_score CHECK (score >= 1 AND score <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价';

-- ----------------------------------------------------------------------------
-- complaints 投诉
-- 订单任一方对另一方发起，管理员处理后填写结论。
-- 付款方以“服务未完成/质量不符”投诉时，订单同时进入争议（orders.disputed=1）；
-- 收款方的催款投诉不会暂停追缴计时。
-- ----------------------------------------------------------------------------
CREATE TABLE complaints (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '投诉ID',
    complainant_id  BIGINT          NOT NULL                 COMMENT '投诉人',
    order_id        BIGINT          NOT NULL                 COMMENT '涉及的订单',
    target_id       BIGINT          NOT NULL                 COMMENT '被投诉人（订单另一方）',
    reason          VARCHAR(100)    NOT NULL                 COMMENT '投诉理由（选项文字）',
    description     VARCHAR(500)    DEFAULT NULL             COMMENT '详细描述',
    image_url       VARCHAR(500)    DEFAULT NULL             COMMENT '证据图片 URL',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '投诉时间',
    resolved        TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已处理',
    resolved_by     BIGINT          DEFAULT NULL             COMMENT '处理的管理员',
    resolved_at     DATETIME(6)     DEFAULT NULL             COMMENT '处理时间',
    resolution      VARCHAR(500)    DEFAULT NULL             COMMENT '处理结论',
    PRIMARY KEY (id),
    KEY idx_complaints_order_id (order_id),
    CONSTRAINT fk_complaint_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉';

-- ----------------------------------------------------------------------------
-- 分类字典预置数据（业务必需，生产环境同样需要）
-- 编号固定，测试数据按编号引用：1 快递代取，2 学习辅导，3 二手交易，4 组队匹配，5 信息咨询。
-- ----------------------------------------------------------------------------
INSERT INTO categories (id, name, has_review, sort_order) VALUES
    (1, '快递代取', 1, 1),
    (2, '学习辅导', 1, 2),
    (3, '二手交易', 0, 3),
    (4, '组队匹配', 0, 4),
    (5, '信息咨询', 1, 5);
