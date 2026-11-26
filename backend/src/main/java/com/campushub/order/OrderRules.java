package com.campushub.order;

/**
 * 交易核心的订单规则常量（单一出处，K5）。
 * 现行业务规则见 backend/PAYMENTS.md；时间期限按业务时钟（Asia/Shanghai）起算。
 *
 * <p><b>改动须通知 Frontend 对话</b>：前端 {@code order/orderFlow.js}（验收、付款、逾期期限提示）与
 * {@code order/orderPolicy.js}（评价窗口）抄写了这里的数值，后端规则变化必须同步修订这两个文件及其单测
 * （模块契约 §6 “规则副本”）。{@code OrderRulesTest} 固定了这些数值，改动时该测试会失败，提醒同步。
 */
public final class OrderRules {

    private OrderRules() {
    }

    /** 服务方提交完成后，需求验收方的验收时限；超时且无争议自动验收。 */
    public static final int ACCEPTANCE_HOURS = 24;

    /** 验收后的正常付款期（小时）；超时未付款标记逾期并扣分。 */
    public static final int PAYMENT_WINDOW_HOURS = 1;

    /** 付款逾期一次性扣除的信用分。 */
    public static final int OVERDUE_PENALTY_POINTS = 10;

    /** 逾期扣分后的宽限期（小时）；期满仍未付款转管理员处置。 */
    public static final int ESCALATION_GRACE_HOURS = 24;

    /** 评价窗口（天），从订单开放评价时刻起算；期满由系统默认评价。 */
    public static final int REVIEW_WINDOW_DAYS = 7;

    /** 评价窗口期满的系统默认评分（星）。 */
    public static final int DEFAULT_REVIEW_SCORE = 4;

    /** 发布需求、接单所需的最低信用分。 */
    public static final int MIN_CREDIT_TO_TRADE = 70;

    /**
     * 评价对服务方信用分的影响：五星加 1，一二星减 1，三四星不变。
     * 系统默认四星因此不加不减。
     */
    public static int reviewCreditDelta(int score) {
        return score == 5 ? 1 : score <= 2 ? -1 : 0;
    }
}
