-- ============================================================================
-- 05 测试数据（仅开发/演示环境，生产库不要执行）
-- ----------------------------------------------------------------------------
-- 前提：刚执行完 01-04 的空库。主键写死，便于对照下面的场景说明；
--       重复执行会因主键冲突直接失败，需要重来请先 DROP DATABASE 后重建。
-- 时间：全部相对“执行本脚本的时刻”计算，让定时任务立刻有东西可扫。
--       会话时区先设为 +08:00，保证 NOW() 与应用的上海时间一致。
-- 手机号：库里存的是占位串而不是真实 AES 密文（密钥随环境变化），
--         所以测试账号只能用学号 + 密码登录，个人资料里的手机号显示为 ***。
--
-- 账号（密码统一 123456）
--   ID 学号        姓名       信用分  用来测什么
--   1  241880900  测试用户1   100    主要发布者：发需求、验收、付款、评价、发起组队
--   2  241880901  测试用户2   100    主要接单人：提交完成、收款、被评价
--   3  241880902  测试用户3    70    临界值：70 分仍可发布和接单，不参与每日恢复
--   4  241880903  测试用户4    69    低于 70：发布、接单被拒；次日零点恢复到 70 后停止
--   5  241880904  测试用户5    99    被打过两星（-1）；可加入组队把队伍凑满
--   6  241880999  管理员      100    审核需求、处理投诉、登记人工结算
--
-- 场景速查（需求 → 订单）
--   T1  跑腿 已发布            无订单   用户2/3 接单成功；用户4 接单被拒（<70）
--   T2  辅导 进行中            O1      用户2 可“提交完成”
--   T3  二手出售 进行中        O2      待验收：卖家=发布者用户2 收款，买家=接单人用户1 付款并验收
--   T4  咨询 已完成            O3      已验收待付款，付款期剩约 40 分钟（本地未开通渠道，付款会报错）
--   T5  跑腿 已完成            O4      已逾期：用户4 已被扣 10 分（79→69），约 20 小时后升级管理员
--   T6  辅导 已完成            O5      已付款待结算、待评价：用户1 可评价；管理员可登记结算
--   T7  跑腿 已完成            O6      已付款已评价（两星，用户5 100→99），待结算
--   T8  组队 已发布 上限4人    O7 O8   群里已有用户1(群主)/2/3，剩 1 个名额；用户5 加入后满员
--   T9  咨询 待审核（免费）     无订单   管理员审核通过/驳回
--   T10 组队 待审核 上限3人    无订单   管理员审核通过后自动建群
--   T11 跑腿 已发布但已过截止  无订单   约 1 分钟内被定时任务自动取消
--   T12 跑腿 进行中            O9      争议中：用户1 投诉“服务未完成”，等管理员裁决
--   T13 二手求购 已发布        无订单   接单人是卖家：发布者用户1 付款给接单人
--   T14 咨询 已取消            无订单   被管理员驳回的需求
-- ============================================================================

USE campushub;

SET time_zone = '+08:00';
SET @now = NOW(6);
-- 测试密码 123456 的 BCrypt 哈希（由 Spring Security BCryptPasswordEncoder 生成并校验）
SET @pwd = '$2a$10$zxNS7Bt5EfwkMh6pdIvWgOICetaxd2xH4qbe/hCpTvAkUSZucNS3q';

-- ---------------------------------------------------------------- 账号
INSERT INTO users (id, student_id, name, nickname, email, phone, password_hash, role, credit_score, status, create_time, update_time) VALUES
    (1, '241880900', '测试用户1', '测试用户1', '241880900@smail.nju.edu.cn', 'seed-phone-241880900', @pwd, 'requester', 100, 1, @now - INTERVAL 30 DAY, @now),
    (2, '241880901', '测试用户2', '测试用户2', '241880901@smail.nju.edu.cn', 'seed-phone-241880901', @pwd, 'requester', 100, 1, @now - INTERVAL 30 DAY, @now),
    (3, '241880902', '测试用户3', '测试用户3', '241880902@smail.nju.edu.cn', 'seed-phone-241880902', @pwd, 'requester',  70, 1, @now - INTERVAL 30 DAY, @now),
    (4, '241880903', '测试用户4', '测试用户4', '241880903@smail.nju.edu.cn', 'seed-phone-241880903', @pwd, 'requester',  69, 1, @now - INTERVAL 30 DAY, @now),
    (5, '241880904', '测试用户5', '测试用户5', '241880904@smail.nju.edu.cn', 'seed-phone-241880904', @pwd, 'requester',  99, 1, @now - INTERVAL 30 DAY, @now),
    (6, '241880999', '管理员',    '管理员',    '241880999@smail.nju.edu.cn', 'seed-phone-241880999', @pwd, 'admin',     100, 1, @now - INTERVAL 30 DAY, @now);

-- ---------------------------------------------------------------- 需求（父表）
-- reward 按发布规则填写：辅导=时薪×时长，二手=成交价，组队恒为空，免费咨询为空。
INSERT INTO tasks (id, requester_id, category_id, task_type, title, description, reward, campus, status, location, deadline, view_count, published_at, create_time) VALUES
    ( 1, 1, 1, 'RUNNING',    '帮取菜鸟驿站快递',   '一个小件快递，送到仙林 3 栋楼下即可。',         5.00, '仙林校区', 'published',      '菜鸟驿站 → 仙林3栋',  @now + INTERVAL 3 DAY,  12, @now - INTERVAL 1 DAY,  @now - INTERVAL 25 HOUR),
    ( 2, 1, 2, 'TUTORING',   '高数期末辅导',       '重点讲多元微积分，约两小时。',                 100.00, '仙林校区', 'in_progress',    '仙林图书馆',          @now + INTERVAL 5 DAY,  30, @now - INTERVAL 2 DAY,  @now - INTERVAL 49 HOUR),
    ( 3, 2, 3, 'FLEA',       '出售九成新台灯',     '宿舍搬家出，自取。',                           120.00, '仙林校区', 'in_progress',    '仙林5栋',             @now + INTERVAL 7 DAY,  18, @now - INTERVAL 2 DAY,  @now - INTERVAL 49 HOUR),
    ( 4, 3, 5, 'CONSULTING', '保研经验咨询',       '想了解夏令营材料准备，线上聊半小时。',          30.00, '鼓楼校区', 'completed',      '线上',                @now + INTERVAL 2 DAY,  25, @now - INTERVAL 2 DAY,  @now - INTERVAL 49 HOUR),
    ( 5, 4, 1, 'RUNNING',    '代取外卖',           '午饭送到教学楼 A 座。',                          8.00, '仙林校区', 'completed',      '东门 → 教A',          @now + INTERVAL 1 DAY,   9, @now - INTERVAL 3 DAY,  @now - INTERVAL 73 HOUR),
    ( 6, 1, 2, 'TUTORING',   'C语言作业答疑',      '指针和链表部分，约一个半小时。',                60.00, '仙林校区', 'completed',      '基础实验楼',          @now + INTERVAL 1 DAY,  21, @now - INTERVAL 4 DAY,  @now - INTERVAL 97 HOUR),
    ( 7, 3, 1, 'RUNNING',    '图书馆还书',         '三本书还到杜厦图书馆。',                         6.00, '仙林校区', 'completed',      '仙林2栋 → 杜厦图书馆', @now + INTERVAL 1 DAY,   7, @now - INTERVAL 5 DAY,  @now - INTERVAL 121 HOUR),
    ( 8, 1, 4, 'TEAM',       '周末羽毛球双打',     '周六下午体育馆，凑满四人开打。',                 NULL, '仙林校区', 'published',      '仙林体育馆',          @now + INTERVAL 5 DAY,  40, @now - INTERVAL 2 DAY,  @now - INTERVAL 50 HOUR),
    ( 9, 1, 5, 'CONSULTING', '考研择校咨询',       '想听听学长学姐的建议，免费交流。',               NULL, '鼓楼校区', 'pending_review', '线上',                @now + INTERVAL 6 DAY,   0, @now - INTERVAL 1 HOUR, @now - INTERVAL 1 HOUR),
    (10, 3, 4, 'TEAM',       '数学建模组队',       '缺一名编程同学，参加校赛。',                     NULL, '仙林校区', 'pending_review', '线上',                @now + INTERVAL 10 DAY,  0, @now - INTERVAL 1 HOUR, @now - INTERVAL 1 HOUR),
    (11, 1, 1, 'RUNNING',    '代买早餐',           '明早八点前送到宿舍。',                           4.00, '仙林校区', 'published',      '食堂 → 仙林3栋',      @now - INTERVAL 1 HOUR,  3, @now - INTERVAL 1 DAY,  @now - INTERVAL 25 HOUR),
    (12, 1, 1, 'RUNNING',    '搬运行李',           '两个箱子从宿舍搬到南门快递点。',                15.00, '仙林校区', 'in_progress',    '仙林3栋 → 南门',      @now + INTERVAL 2 DAY,  11, @now - INTERVAL 2 DAY,  @now - INTERVAL 49 HOUR),
    (13, 1, 3, 'FLEA',       '求购二手自行车',     '能正常骑即可，最好带锁。',                     200.00, '仙林校区', 'published',      '仙林校区',            @now + INTERVAL 7 DAY,  15, @now - INTERVAL 1 DAY,  @now - INTERVAL 25 HOUR),
    (14, 2, 5, 'CONSULTING', '代写课程论文',       '有偿代写，价格好商量。',                        50.00, '鼓楼校区', 'cancelled',      '线上',                @now + INTERVAL 3 DAY,   2, @now - INTERVAL 1 DAY,  @now - INTERVAL 1 DAY);

-- ---------------------------------------------------------------- 需求子表
INSERT INTO t_task_running (task_id, pickup_location, delivery_location, pickup_code, sub_reward) VALUES
    ( 1, '菜鸟驿站',   '仙林3栋',     '6-2-1024', 5.00),
    ( 5, '东门',       '教A',         NULL,       8.00),
    ( 7, '仙林2栋',    '杜厦图书馆',  NULL,       6.00),
    (11, '食堂',       '仙林3栋',     NULL,       4.00),
    (12, '仙林3栋',    '南门',        NULL,      15.00);

INSERT INTO t_task_tutoring (task_id, subject, price_per_hour, tutoring_location, duration_hours) VALUES
    (2, '高等数学', 50.00, '仙林图书馆', 2.00),
    (6, 'C语言',    40.00, '基础实验楼', 1.50);

INSERT INTO t_task_flea (task_id, item_price, trade_mode) VALUES
    ( 3, 120.00, 'SELL'),
    (13, 200.00, 'BUY');

INSERT INTO t_task_team (task_id, max_members) VALUES
    ( 8, 4),
    (10, 3);

INSERT INTO t_task_consulting (task_id, consulting_mode) VALUES
    ( 4, 'ONLINE'),
    ( 9, 'ONLINE'),
    (14, 'ONLINE');

-- ---------------------------------------------------------------- 订单
-- 付款方向：普通需求 发布者付款、接单人收款；二手出售(T3) 接单人付款、发布者收款。
-- 组队订单金额为 0：无需付款、无需结算、不开放评价（组队分类 has_review=0）。
INSERT INTO orders (id, task_id, requester_id, provider_id, status, accepted_at, completed_at, create_time,
                    amount_due, payer_id, payee_id, payment_status, settlement_status,
                    completion_submitted_at, acceptance_due_at, payment_due_at, overdue_at, escalation_due_at,
                    paid_at, review_eligible_at, disputed, disputed_at, dispute_reason) VALUES
    -- O1 进行中：用户2 可提交完成
    (1, 2, 1, 2, 'in_progress',         @now - INTERVAL 2 HOUR,  NULL,                     @now - INTERVAL 2 HOUR,
         100.00, 1, 2, 'unpaid', 'pending',
         NULL, NULL, NULL, NULL, NULL,
         NULL, NULL, 0, NULL, NULL),
    -- O2 二手出售待验收：卖家用户2 已提交完成，买家用户1 验收，22 小时后自动验收
    (2, 3, 2, 1, 'awaiting_acceptance', @now - INTERVAL 1 DAY,   NULL,                     @now - INTERVAL 1 DAY,
         120.00, 1, 2, 'unpaid', 'pending',
         @now - INTERVAL 2 HOUR, @now + INTERVAL 22 HOUR, NULL, NULL, NULL,
         NULL, NULL, 0, NULL, NULL),
    -- O3 已验收待付款：付款截止在 40 分钟后
    (3, 4, 3, 2, 'completed',           @now - INTERVAL 1 DAY,   @now - INTERVAL 20 MINUTE, @now - INTERVAL 1 DAY,
         30.00, 3, 2, 'unpaid', 'pending',
         @now - INTERVAL 3 HOUR, @now + INTERVAL 21 HOUR, @now + INTERVAL 40 MINUTE, NULL, NULL,
         NULL, NULL, 0, NULL, NULL),
    -- O4 已逾期：24 小时无异议自动验收 → 1 小时未付 → 扣 10 分，20 小时后升级管理员
    (4, 5, 4, 2, 'completed',           @now - INTERVAL 2 DAY,   @now - INTERVAL 5 HOUR,   @now - INTERVAL 2 DAY,
         8.00, 4, 2, 'unpaid', 'pending',
         @now - INTERVAL 29 HOUR, @now - INTERVAL 5 HOUR, @now - INTERVAL 4 HOUR, @now - INTERVAL 4 HOUR, @now + INTERVAL 20 HOUR,
         NULL, NULL, 0, NULL, NULL),
    -- O5 已付款待结算、待评价：评价期还剩约 5 天
    (5, 6, 1, 2, 'completed',           @now - INTERVAL 3 DAY,   @now - INTERVAL 48 HOUR,  @now - INTERVAL 3 DAY,
         60.00, 1, 2, 'paid', 'pending',
         @now - INTERVAL 50 HOUR, @now - INTERVAL 26 HOUR, @now - INTERVAL 47 HOUR, NULL, NULL,
         @now - INTERVAL 47 HOUR - INTERVAL 30 MINUTE, @now - INTERVAL 47 HOUR - INTERVAL 30 MINUTE, 0, NULL, NULL),
    -- O6 已付款已评价（两星），待结算
    (6, 7, 3, 5, 'completed',           @now - INTERVAL 4 DAY,   @now - INTERVAL 3 DAY,    @now - INTERVAL 4 DAY,
         6.00, 3, 5, 'paid', 'pending',
         @now - INTERVAL 3 DAY - INTERVAL 2 HOUR, @now + INTERVAL 22 HOUR - INTERVAL 3 DAY, @now - INTERVAL 3 DAY + INTERVAL 1 HOUR, NULL, NULL,
         @now - INTERVAL 3 DAY + INTERVAL 30 MINUTE, @now - INTERVAL 3 DAY + INTERVAL 30 MINUTE, 0, NULL, NULL),
    -- O7 / O8 组队成员（用户2、用户3）
    (7, 8, 1, 2, 'in_progress',         @now - INTERVAL 1 DAY,   NULL,                     @now - INTERVAL 1 DAY,
         0.00, 1, 2, 'not_required', 'not_required',
         NULL, NULL, NULL, NULL, NULL,
         NULL, NULL, 0, NULL, NULL),
    (8, 8, 1, 3, 'in_progress',         @now - INTERVAL 20 HOUR, NULL,                     @now - INTERVAL 20 HOUR,
         0.00, 1, 3, 'not_required', 'not_required',
         NULL, NULL, NULL, NULL, NULL,
         NULL, NULL, 0, NULL, NULL),
    -- O9 争议中：用户3 提交完成后，用户1 以“服务未完成”投诉，自动验收已暂停
    (9, 12, 1, 3, 'awaiting_acceptance', @now - INTERVAL 1 DAY,  NULL,                     @now - INTERVAL 1 DAY,
         15.00, 1, 3, 'unpaid', 'pending',
         @now - INTERVAL 6 HOUR, @now + INTERVAL 18 HOUR, NULL, NULL, NULL,
         NULL, NULL, 1, @now - INTERVAL 3 HOUR, '只搬了一个箱子，另一个还在宿舍，服务未完成');

-- ---------------------------------------------------------------- 支付单
-- 仅模拟“渠道已确认付款”后的库内状态，单号和交易号都是 SEED 开头的假值，不对应任何真实交易。
INSERT INTO payment_attempts (id, order_id, active_slot, out_trade_no, channel, amount, app_id, merchant_id, status,
                              code_url, provider_transaction_id, create_time, paid_at, last_queried_at, closed_at) VALUES
    (1, 5, 1, 'SEED0000000000000000000000000001', 'WECHAT', 60.00, 'seed-app-id', 'seed-merchant-id', 'PAID',
        NULL, 'SEED-WECHAT-TRANSACTION-0001', @now - INTERVAL 47 HOUR - INTERVAL 45 MINUTE, @now - INTERVAL 47 HOUR - INTERVAL 30 MINUTE, NULL, NULL),
    (2, 6, 1, 'SEED0000000000000000000000000002', 'ALIPAY',  6.00, 'seed-app-id', 'seed-seller-id',   'PAID',
        NULL, 'SEED-ALIPAY-TRANSACTION-0002', @now - INTERVAL 3 DAY + INTERVAL 20 MINUTE, @now - INTERVAL 3 DAY + INTERVAL 30 MINUTE, NULL, NULL);

-- ---------------------------------------------------------------- 评价与投诉
INSERT INTO reviews (id, order_id, score, comment, automatic, create_time) VALUES
    (1, 6, 2, '比约定晚了一个多小时才送到。', 0, @now - INTERVAL 2 DAY);

INSERT INTO complaints (id, complainant_id, order_id, target_id, reason, description, image_url, create_time, resolved) VALUES
    (1, 1, 9, 3, '服务未完成', '只搬了一个箱子，另一个还在宿舍。', NULL, @now - INTERVAL 3 HOUR, 0);

-- ---------------------------------------------------------------- 信用分流水
-- 与上面的分数对得上：用户4 因 O4 逾期 79→69；用户5 因 O6 被打两星 100→99。
INSERT INTO credit_events (id, event_key, user_id, previous_score, resulting_score, requested_delta, reason, create_time) VALUES
    (1, 'overdue:4', 4, 79, 69, -10, '验收后1小时未付款', @now - INTERVAL 4 HOUR),
    (2, 'review:6',  5, 100, 99,  -1, '订单评分：2',      @now - INTERVAL 2 DAY);

-- ---------------------------------------------------------------- 会话
-- 1-5 系统通知会话，6-12 订单私聊，13 组队群（T8）。
INSERT INTO conversations (id, conv_type, task_id, order_id, group_name, owner_id, max_members, create_time, update_time) VALUES
    ( 1, 'SYSTEM_NOTIFY', NULL, NULL, '系统通知', NULL, NULL, @now - INTERVAL 30 DAY, @now - INTERVAL 2 HOUR),
    ( 2, 'SYSTEM_NOTIFY', NULL, NULL, '系统通知', NULL, NULL, @now - INTERVAL 30 DAY, @now - INTERVAL 20 MINUTE),
    ( 3, 'SYSTEM_NOTIFY', NULL, NULL, '系统通知', NULL, NULL, @now - INTERVAL 30 DAY, @now - INTERVAL 20 MINUTE),
    ( 4, 'SYSTEM_NOTIFY', NULL, NULL, '系统通知', NULL, NULL, @now - INTERVAL 30 DAY, @now - INTERVAL 4 HOUR),
    ( 5, 'SYSTEM_NOTIFY', NULL, NULL, '系统通知', NULL, NULL, @now - INTERVAL 30 DAY, @now - INTERVAL 1 HOUR),
    ( 6, 'TASK_PRIVATE',  2,  1, NULL, NULL, NULL, @now - INTERVAL 2 HOUR,  @now - INTERVAL 90 MINUTE),
    ( 7, 'TASK_PRIVATE',  3,  2, NULL, NULL, NULL, @now - INTERVAL 1 DAY,   @now - INTERVAL 2 HOUR),
    ( 8, 'TASK_PRIVATE',  4,  3, NULL, NULL, NULL, @now - INTERVAL 1 DAY,   @now - INTERVAL 1 DAY),
    ( 9, 'TASK_PRIVATE',  5,  4, NULL, NULL, NULL, @now - INTERVAL 2 DAY,   @now - INTERVAL 2 DAY),
    (10, 'TASK_PRIVATE',  6,  5, NULL, NULL, NULL, @now - INTERVAL 3 DAY,   @now - INTERVAL 3 DAY),
    (11, 'TASK_PRIVATE',  7,  6, NULL, NULL, NULL, @now - INTERVAL 4 DAY,   @now - INTERVAL 4 DAY),
    (12, 'TASK_PRIVATE', 12,  9, NULL, NULL, NULL, @now - INTERVAL 1 DAY,   @now - INTERVAL 3 HOUR),
    (13, 'TEAM_GROUP',    8, NULL, '「周末羽毛球双打」组队群', 1, 4, @now - INTERVAL 2 DAY, @now - INTERVAL 30 MINUTE);

-- 会话成员：系统通知会话只有本人；私聊是发布者 + 接单人；群聊群主 OWNER，其余 MEMBER。
-- read_at 为空或早于最新消息的，会显示未读。
INSERT INTO conversation_members (conv_id, user_id, role, joined_at, read_at) VALUES
    ( 1, 1, 'MEMBER', @now - INTERVAL 30 DAY, @now - INTERVAL 3 HOUR),
    ( 2, 2, 'MEMBER', @now - INTERVAL 30 DAY, NULL),
    ( 3, 3, 'MEMBER', @now - INTERVAL 30 DAY, NULL),
    ( 4, 4, 'MEMBER', @now - INTERVAL 30 DAY, NULL),
    ( 5, 6, 'MEMBER', @now - INTERVAL 30 DAY, NULL),
    ( 6, 1, 'MEMBER', @now - INTERVAL 2 HOUR, @now - INTERVAL 90 MINUTE),
    ( 6, 2, 'MEMBER', @now - INTERVAL 2 HOUR, @now - INTERVAL 100 MINUTE),
    ( 7, 2, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 2 HOUR),
    ( 7, 1, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 5 HOUR),
    ( 8, 3, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 1 DAY),
    ( 8, 2, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 1 DAY),
    ( 9, 4, 'MEMBER', @now - INTERVAL 2 DAY,  @now - INTERVAL 2 DAY),
    ( 9, 2, 'MEMBER', @now - INTERVAL 2 DAY,  @now - INTERVAL 2 DAY),
    (10, 1, 'MEMBER', @now - INTERVAL 3 DAY,  @now - INTERVAL 3 DAY),
    (10, 2, 'MEMBER', @now - INTERVAL 3 DAY,  @now - INTERVAL 3 DAY),
    (11, 3, 'MEMBER', @now - INTERVAL 4 DAY,  @now - INTERVAL 4 DAY),
    (11, 5, 'MEMBER', @now - INTERVAL 4 DAY,  @now - INTERVAL 4 DAY),
    (12, 1, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 3 HOUR),
    (12, 3, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 6 HOUR),
    (13, 1, 'OWNER',  @now - INTERVAL 2 DAY,  @now - INTERVAL 30 MINUTE),
    (13, 2, 'MEMBER', @now - INTERVAL 1 DAY,  @now - INTERVAL 2 HOUR),
    (13, 3, 'MEMBER', @now - INTERVAL 20 HOUR, NULL);

-- ---------------------------------------------------------------- 消息
-- sender_id=0 的是系统消息，文案与代码中自动生成的一致。
INSERT INTO messages (conv_id, sender_id, msg_type, content, create_time) VALUES
    -- 系统通知会话：与下方 notifications 一一对应（“【标题】内容”）
    ( 1, 0, 'SYSTEM', '【有人接单了】测试用户2 接了你的需求《周末羽毛球双打》。', @now - INTERVAL 1 DAY),
    ( 1, 0, 'SYSTEM', '【有人接单了】测试用户3 接了你的需求《周末羽毛球双打》。', @now - INTERVAL 20 HOUR),
    ( 1, 0, 'SYSTEM', '【有人接单了】测试用户2 接了你的需求《高数期末辅导》。', @now - INTERVAL 2 HOUR),
    ( 1, 0, 'SYSTEM', '【请验收服务】服务方已提交完成，24小时无异议将自动验收。', @now - INTERVAL 2 HOUR),
    ( 2, 0, 'SYSTEM', '【服务已验收】验收已完成，付款及结算状态请在订单中查看。', @now - INTERVAL 20 MINUTE),
    ( 3, 0, 'SYSTEM', '【验收已完成】有偿订单请在验收后1小时内付款；无偿订单可直接评价。', @now - INTERVAL 20 MINUTE),
    ( 4, 0, 'SYSTEM', '【付款逾期扣分】本订单已扣10分；24小时后仍未付款将由管理员介入。', @now - INTERVAL 4 HOUR),
    ( 5, 0, 'SYSTEM', '【新需求待审核: 考研择校咨询】【新需求待审核】\n需求ID: 9\n标题: 考研择校咨询', @now - INTERVAL 1 HOUR),
    ( 5, 0, 'SYSTEM', '【新需求待审核: 数学建模组队】【新需求待审核】\n需求ID: 10\n标题: 数学建模组队', @now - INTERVAL 1 HOUR),
    -- 私聊：接单时的系统提示 + 双方对话
    ( 6, 0, 'SYSTEM', '测试用户2 接取了 测试用户1 的需求「高数期末辅导」，开始沟通吧', @now - INTERVAL 2 HOUR),
    ( 6, 2, 'TEXT',   '你好，今晚七点图书馆三楼可以吗？', @now - INTERVAL 100 MINUTE),
    ( 6, 1, 'TEXT',   '可以，我带上习题册。', @now - INTERVAL 90 MINUTE),
    ( 7, 0, 'SYSTEM', '测试用户1 接取了 测试用户2 的需求「出售九成新台灯」，开始沟通吧', @now - INTERVAL 1 DAY),
    ( 7, 1, 'TEXT',   '台灯还在吗？明天中午去拿。', @now - INTERVAL 23 HOUR),
    ( 7, 2, 'TEXT',   '已经放到楼下了，拿到记得在订单里确认验收。', @now - INTERVAL 2 HOUR),
    ( 8, 0, 'SYSTEM', '测试用户2 接取了 测试用户3 的需求「保研经验咨询」，开始沟通吧', @now - INTERVAL 1 DAY),
    ( 9, 0, 'SYSTEM', '测试用户2 接取了 测试用户4 的需求「代取外卖」，开始沟通吧', @now - INTERVAL 2 DAY),
    (10, 0, 'SYSTEM', '测试用户2 接取了 测试用户1 的需求「C语言作业答疑」，开始沟通吧', @now - INTERVAL 3 DAY),
    (11, 0, 'SYSTEM', '测试用户5 接取了 测试用户3 的需求「图书馆还书」，开始沟通吧', @now - INTERVAL 4 DAY),
    (12, 0, 'SYSTEM', '测试用户3 接取了 测试用户1 的需求「搬运行李」，开始沟通吧', @now - INTERVAL 1 DAY),
    (12, 3, 'TEXT',   '已经搬完了，麻烦确认一下。', @now - INTERVAL 6 HOUR),
    (12, 1, 'TEXT',   '还有一个箱子在宿舍，我先发起投诉了。', @now - INTERVAL 3 HOUR),
    -- 组队群
    (13, 0, 'SYSTEM', '测试用户1 创建了群聊', @now - INTERVAL 2 DAY),
    (13, 0, 'SYSTEM', '测试用户2 加入了群聊', @now - INTERVAL 1 DAY),
    (13, 0, 'SYSTEM', '测试用户3 加入了群聊', @now - INTERVAL 20 HOUR),
    (13, 1, 'TEXT',   '还差一个人，大家帮忙拉一下。', @now - INTERVAL 2 HOUR),
    (13, 2, 'TEXT',   '我带两支拍子。', @now - INTERVAL 30 MINUTE);

-- ---------------------------------------------------------------- 站内通知
-- 与系统通知会话里的消息一一对应。
-- 驳回通知（T14）由管理员接口直接写入本表，不同步系统消息，这里保持一致。
INSERT INTO notifications (receiver_id, type, title, content, is_read, create_time, read_time) VALUES
    (1, 'ORDER_ACCEPTED', '有人接单了',   '测试用户2 接了你的需求《周末羽毛球双打》。', 1, @now - INTERVAL 1 DAY,      @now - INTERVAL 23 HOUR),
    (1, 'ORDER_ACCEPTED', '有人接单了',   '测试用户3 接了你的需求《周末羽毛球双打》。', 1, @now - INTERVAL 20 HOUR,    @now - INTERVAL 19 HOUR),
    (1, 'ORDER_ACCEPTED', '有人接单了',   '测试用户2 接了你的需求《高数期末辅导》。',   0, @now - INTERVAL 2 HOUR,     NULL),
    (1, 'ACCEPTANCE',     '请验收服务',   '服务方已提交完成，24小时无异议将自动验收。', 0, @now - INTERVAL 2 HOUR,     NULL),
    (2, 'ACCEPTED',       '服务已验收',   '验收已完成，付款及结算状态请在订单中查看。', 0, @now - INTERVAL 20 MINUTE,  NULL),
    (2, 'TASK_REVIEW_RESULT', '需求审核结果', '你的需求「代写课程论文」已被驳回。\n驳回理由：代写作业违反校规。', 0, @now - INTERVAL 1 DAY, NULL),
    (3, 'ACCEPTED',       '验收已完成',   '有偿订单请在验收后1小时内付款；无偿订单可直接评价。', 0, @now - INTERVAL 20 MINUTE, NULL),
    (4, 'OVERDUE',        '付款逾期扣分', '本订单已扣10分；24小时后仍未付款将由管理员介入。', 0, @now - INTERVAL 4 HOUR, NULL),
    (6, 'TASK_REVIEW',    '新需求待审核: 考研择校咨询', '【新需求待审核】\n需求ID: 9\n标题: 考研择校咨询', 0, @now - INTERVAL 1 HOUR, NULL),
    (6, 'TASK_REVIEW',    '新需求待审核: 数学建模组队', '【新需求待审核】\n需求ID: 10\n标题: 数学建模组队', 0, @now - INTERVAL 1 HOUR, NULL);
