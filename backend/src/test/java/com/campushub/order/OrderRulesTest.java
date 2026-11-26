package com.campushub.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定订单规则数值（K5）。
 * 前端 order/orderFlow.js、order/orderPolicy.js 抄写了这些数值；本测试失败说明后端规则被改动，
 * 须通知 Frontend 对话同步前端副本及其单测，并同步更新 backend/PAYMENTS.md，再修改这里的期望值。
 */
class OrderRulesTest {

    @Test
    void pinsAcceptancePaymentOverdueAndEscalationTimeline() {
        // 提交完成后 24 小时验收、验收后 1 小时付款、逾期扣 10 分、再 24 小时升级管理员。
        assertThat(OrderRules.ACCEPTANCE_HOURS).isEqualTo(24);
        assertThat(OrderRules.PAYMENT_WINDOW_HOURS).isEqualTo(1);
        assertThat(OrderRules.OVERDUE_PENALTY_POINTS).isEqualTo(10);
        assertThat(OrderRules.ESCALATION_GRACE_HOURS).isEqualTo(24);
        // 无确认且无异议的完整时间线：24h 验收、25h 扣分、49h 管理员介入。
        assertThat(OrderRules.ACCEPTANCE_HOURS + OrderRules.PAYMENT_WINDOW_HOURS).isEqualTo(25);
        assertThat(OrderRules.ACCEPTANCE_HOURS + OrderRules.PAYMENT_WINDOW_HOURS + OrderRules.ESCALATION_GRACE_HOURS).isEqualTo(49);
    }

    @Test
    void pinsReviewWindowDefaultScoreAndCreditThreshold() {
        assertThat(OrderRules.REVIEW_WINDOW_DAYS).isEqualTo(7);
        assertThat(OrderRules.DEFAULT_REVIEW_SCORE).isEqualTo(4);
        assertThat(OrderRules.MIN_CREDIT_TO_TRADE).isEqualTo(70);
    }

    @Test
    void reviewScoreMovesCreditByFiveStarPlusOneAndLowStarsMinusOne() {
        assertThat(OrderRules.reviewCreditDelta(5)).isEqualTo(1);
        assertThat(OrderRules.reviewCreditDelta(4)).isZero();
        assertThat(OrderRules.reviewCreditDelta(3)).isZero();
        assertThat(OrderRules.reviewCreditDelta(2)).isEqualTo(-1);
        assertThat(OrderRules.reviewCreditDelta(1)).isEqualTo(-1);
        // 系统默认评分不得改变信用分。
        assertThat(OrderRules.reviewCreditDelta(OrderRules.DEFAULT_REVIEW_SCORE)).isZero();
    }
}
