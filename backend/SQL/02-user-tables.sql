-- ============================================================================
-- 02 账号集群（user 分支）
-- ----------------------------------------------------------------------------
-- 表：users、credit_events、credit_recovery_runs、verification_codes
-- 写权限：仅账号集群的 UserService / CreditService / VerificationCodeService；
--         其他集群只读。admin 调分、封禁也必须经域服务（模块契约 §3.2、§3.4）。
-- 依赖：无。其余所有集群的表都外键引用 users，必须最先建。
-- ============================================================================

USE campushub;

-- ----------------------------------------------------------------------------
-- users 用户
-- 一行一个账号，存身份、登录凭据、角色、信用分和封禁状态。
-- * 角色只分普通用户和管理员。普通用户不再区分“需求方/服务方”，同一账号既能发布也能接单，
--   需求与订单里的 requester/provider 指的是“这一单的发布者/接单人”，与账号角色无关。
-- * 登录：学号 + 密码（BCrypt），或手机号 + 邮箱验证码。
-- * 手机号用 AES 加密后入库；同一明文加密结果固定，所以能做唯一约束和按密文查找。
-- * 信用分 0-100，初始 100；低于 70 不能发布需求或接单；低于 70 时每天零点恢复 1 分，
--   恢复到 70 为止（见 credit_recovery_runs）。所有加减分必须经 CreditService，
--   每次变更在 credit_events 留一条流水。
-- * 封禁(status=0)后仍可受限登录：只能处理本人订单、付款、投诉和账号安全，
--   每次请求从库里重新加载状态，封禁立即生效，无需等旧 JWT 过期。
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    student_id      VARCHAR(20)     DEFAULT NULL             COMMENT '学号：密码登录账号，注册时绑定；早期账号可能为空',
    name            VARCHAR(20)     NOT NULL                 COMMENT '真实姓名',
    nickname        VARCHAR(20)     DEFAULT NULL             COMMENT '昵称：最长 10 个汉字或 20 个英文字符（汉字计 2，英文数字计 1）；为空时显示真实姓名；注册默认 User+学号前四位',
    email           VARCHAR(100)    NOT NULL                 COMMENT '校园邮箱：接收验证码，注册时由学号生成 学号@smail.nju.edu.cn',
    phone           VARCHAR(255)    NOT NULL                 COMMENT '手机号密文：AES 加密后 Base64；解密失败时页面显示 ***',
    password_hash   VARCHAR(255)    NOT NULL                 COMMENT 'BCrypt 密码哈希',
    role            ENUM('user','admin')
                                    NOT NULL                 COMMENT '角色：user=普通用户（可发布需求也可接单，但不能接自己的单），admin=管理员（只做审核与监管，不发布不接单）',
    credit_score    INT             NOT NULL DEFAULT 100     COMMENT '信用分 0-100：低于 70 禁止发布和接单',
    avatar          VARCHAR(500)    DEFAULT NULL             COMMENT '头像 URL（/uploads 下的公开文件）',
    status          INT             NOT NULL DEFAULT 1       COMMENT '账号状态：1=正常，0=封禁（受限登录）',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '注册时间',
    update_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_student_id (student_id),
    CONSTRAINT chk_users_credit_score CHECK (credit_score BETWEEN 0 AND 100),
    -- 按显示宽度限长：utf8mb4 下汉字 3 字节、英文 1 字节，(字节数 + 字符数) 恰好让汉字计 4、英文计 2，
    -- 上限 40 即“10 个汉字或 20 个英文字符”。仅靠 VARCHAR(20) 会放行 20 个汉字。
    CONSTRAINT chk_users_nickname_length CHECK (LENGTH(nickname) + CHAR_LENGTH(nickname) <= 40)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- ----------------------------------------------------------------------------
-- credit_events 信用分变更流水
-- 每次经 CreditService.adjust 加减分写一行，只增不改，用于审计和对账。
-- * event_key 是幂等键：同一个键重复调用不会重复扣分；参数不一致时报 409。
--   命名约定：overdue:{订单ID}            逾期未付款扣 10 分
--             overdue-reversal:{订单ID}   渠道证明付款未逾期，撤销误罚
--             review:{订单ID}             被评价后的加减分（五星+1，一二星-1，三四星 0）
--             admin:{订单ID}:{操作编号}   管理员处罚
-- * 支付回调发现“付款其实没逾期”时，按 overdue:{订单ID} 找到原流水，按原实际扣分额撤销。
-- * 每日恢复（低于 70 每天 +1）是批量 UPDATE，不写本表。
-- ----------------------------------------------------------------------------
CREATE TABLE credit_events (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '流水ID',
    event_key       VARCHAR(100)    NOT NULL                 COMMENT '幂等键，见表注释的命名约定',
    user_id         BIGINT          NOT NULL                 COMMENT '被调整的用户',
    previous_score  INT             NOT NULL                 COMMENT '调整前分数',
    resulting_score INT             NOT NULL                 COMMENT '调整后分数（已钳制到 0-100）',
    requested_delta INT             NOT NULL                 COMMENT '请求的增减值；因钳制，实际变化可能小于它',
    reason          VARCHAR(1000)   DEFAULT NULL             COMMENT '变更原因，展示给用户和管理员',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_credit_events_event_key (event_key),
    KEY idx_credit_events_user_id (user_id),
    CONSTRAINT fk_credit_event_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信用分变更流水';

-- ----------------------------------------------------------------------------
-- credit_recovery_runs 每日信用恢复执行记录
-- CreditScoreScheduler 每天零点（上海时间）先插入当天日期再执行恢复。日期是主键，
-- 重启或多实例同时触发时只有一个事务能插入成功，保证一天只恢复一次。
-- 想在开发环境当天重复测试恢复，删掉当天这一行即可。
-- ----------------------------------------------------------------------------
CREATE TABLE credit_recovery_runs (
    recovery_day    DATE            NOT NULL                 COMMENT '已执行恢复的日期',
    version         BIGINT          NOT NULL DEFAULT 0       COMMENT 'JPA 乐观锁版本号，插入时为 0',
    PRIMARY KEY (recovery_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日信用恢复执行记录';

-- ----------------------------------------------------------------------------
-- verification_codes 验证码
-- 注册、验证码登录时生成的 6 位码，通过校园邮箱发送（不回显到页面）。
-- * 注册码绑定申请时的学号，防止用别人的学号注册。
-- * 校验条件：手机号 + 验证码 + 类型 + 未使用 + 未过期；用过即作废。
-- ----------------------------------------------------------------------------
CREATE TABLE verification_codes (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '验证码ID',
    phone           VARCHAR(11)     NOT NULL                 COMMENT '申请时填写的手机号（明文，仅用于匹配本次申请）',
    student_id      VARCHAR(20)     DEFAULT NULL             COMMENT '注册场景绑定的学号',
    code            VARCHAR(6)      NOT NULL                 COMMENT '6 位验证码',
    type            VARCHAR(20)     NOT NULL                 COMMENT '用途：REGISTER / LOGIN / BIND',
    expire_time     DATETIME(6)     NOT NULL                 COMMENT '过期时间',
    used            TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已使用',
    create_time     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生成时间',
    PRIMARY KEY (id),
    KEY idx_verification_codes_phone_type_used (phone, type, used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码';
