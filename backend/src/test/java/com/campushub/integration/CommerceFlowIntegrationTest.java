package com.campushub.integration;

import com.campushub.category.*;
import com.campushub.order.*;
import com.campushub.payment.*;
import com.campushub.review.*;
import com.campushub.user.*;
import com.campushub.util.JwtUtil;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真 Spring 过滤链、Service、事务和数据库；只替换外部支付网络，不伪造生产支付接口。 */
@SpringBootTest(properties = {"campushub.payment.wechat.app-id=test-app", "campushub.payment.wechat.merchant-id=test-merchant"})
@AutoConfigureMockMvc @ActiveProfiles("test")
@Import(CommerceFlowIntegrationTest.TimeConfiguration.class)
class CommerceFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired SettlementRecordRepository settlements;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewService reviewService;
    @Autowired CreditEventRepository creditEvents;
    @Autowired CreditScoreScheduler creditRecovery;
    @Autowired JwtUtil jwt;
    @Autowired com.campushub.util.BcryptUtil bcrypt;
    @Autowired com.campushub.verification.VerificationCodeRepository verificationCodes;
    @Autowired TestClock clock;
    @MockitoBean WechatPaymentGateway wechat;
    @MockitoBean com.campushub.util.MailService mail;
    User requester, provider, admin;
    Category category;
    final LocalDateTime start = com.campushub.support.BusinessFixtures.NOW;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }
    /** 使用同一可控时钟驱动验证器和定时业务，测试不会依赖执行日期和 sleep。 */
    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(com.campushub.support.BusinessFixtures.CLOCK.instant());
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
        void set(LocalDateTime time) { now.set(time.atZone(getZone()).toInstant()); }
    }

    @BeforeEach void reset() {
        clock.set(start);
        String url = jdbc.execute((java.sql.Connection connection) -> connection.getMetaData().getURL());
        // 明确防止错误环境变量把清理语句指向正常开发/生产数据库。
        assertThat(url).containsAnyOf("campushub_it", "campushub_ci");
        for (String table : List.of("credit_recovery_runs", "settlement_records", "payment_attempts", "credit_events", "reviews", "complaints",
                "messages", "conversation_members", "conversations", "orders", "t_task_running", "t_task_tutoring",
                "t_task_flea", "t_task_team", "t_task_consulting", "tasks", "notifications", "verification_codes", "users", "categories"))
            jdbc.update("DELETE FROM " + table);
        requester = seed("req", User.Role.user); provider = seed("pro", User.Role.user); admin = seed("adm", User.Role.admin);
        category = categories.save(Category.builder().name("测试跑腿").hasReview(true).sortOrder(1).build());
        when(wechat.channel()).thenReturn(PaymentAttempt.Channel.WECHAT);
        when(wechat.enabled()).thenReturn(true);
        when(wechat.prepay(anyString(), any(), anyString())).thenReturn("weixin://wxpay/bizpayurl?pr=fixture");
        when(wechat.query(anyString())).thenAnswer(invocation -> new PaymentGateway.VerifiedPayment(PaymentAttempt.Channel.WECHAT,
                invocation.getArgument(0), null, null, "CNY", "test-app", "test-merchant", false, null));
    }
    User seed(String prefix, User.Role role) {
        return users.save(User.builder().studentId(prefix + UUID.randomUUID().toString().substring(0, 8))
                .name(prefix).nickname(prefix).phone("fixture-" + prefix + UUID.randomUUID()).password("test-only-hash")
                .email(prefix + "@example.invalid").role(role).creditScore(100).status(1).build());
    }
    String token(User user) { return "Bearer " + jwt.generateToken(user.getStudentId(), user.getId()); }
    ObjectNode validTask() {
        ObjectNode body = json.createObjectNode();
        body.put("title", "请帮忙代取测试快递"); body.put("description", "从测试驿站取件后送到测试宿舍楼下");
        body.put("categoryId", category.getId()); body.put("taskType", "RUNNING"); body.put("expressReward", new BigDecimal("6.50"));
        body.put("pickupLocation", "测试驿站"); body.put("deliveryLocation", "测试宿舍"); body.put("deadline", start.plusDays(2).toString());
        return body;
    }
    long publish(ObjectNode body) throws Exception {
        JsonNode result = response(mvc.perform(post("/v1/tasks").header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("pending_review")));
        long task = result.path("data").path("id").asLong();
        mvc.perform(put("/v1/admin/tasks/{id}/approve", task).header("Authorization", token(admin))).andExpect(status().isOk());
        return task;
    }
    long accept(long task, User user) throws Exception {
        return response(mvc.perform(post("/v1/orders/accept").param("taskId", String.valueOf(task)).header("Authorization", token(user)))
                .andExpect(status().isOk())).path("data").path("orderId").asLong();
    }
    long acceptedService() throws Exception {
        long order = accept(publish(validTask()), provider);
        mvc.perform(post("/v1/orders/{id}/submit-completion", order).header("Authorization", token(provider))).andExpect(status().isOk());
        mvc.perform(post("/v1/orders/{id}/complete", order).header("Authorization", token(requester))).andExpect(status().isOk());
        return order;
    }
    PaymentAttempt invoice(long order) throws Exception {
        mvc.perform(post("/v1/payments/orders/{id}", order).header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"WECHAT\",\"amount\":0.01}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(6.50));
        return attempts.findByOrderId(order).orElseThrow();
    }
    PaymentGateway.VerifiedPayment paid(PaymentAttempt attempt, BigDecimal amount) {
        return new PaymentGateway.VerifiedPayment(PaymentAttempt.Channel.WECHAT, attempt.getOutTradeNo(), "wx-" + attempt.getOutTradeNo(),
                amount, "CNY", "test-app", "test-merchant", true, LocalDateTime.now(clock));
    }
    void notifyPaid(PaymentAttempt attempt) throws Exception {
        when(wechat.verifyNotification(anyString(), anyMap())).thenReturn(paid(attempt, attempt.getAmount()));
        mvc.perform(post("/v1/payments/notify/wechat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
    }
    JsonNode response(ResultActions action) throws Exception { return json.readTree(action.andReturn().getResponse().getContentAsString()); }

    @Test void completePaidBusinessFlowIncludingManualSettlementAndReview() throws Exception {
        long order = acceptedService();
        mvc.perform(post("/v1/reviews/submit").header("Authorization", token(requester)).param("orderId", "" + order).param("score", "5"))
                .andExpect(status().isConflict());
        PaymentAttempt attempt = invoice(order);
        notifyPaid(attempt); notifyPaid(attempt);
        assertThat(attempts.count()).isEqualTo(1);
        mvc.perform(post("/v1/reviews/submit").header("Authorization", token(requester)).param("orderId", "" + order).param("score", "5"))
                .andExpect(status().isOk());
        assertThat(users.findById(provider.getId()).orElseThrow().getCreditScore()).isEqualTo(100);
        assertThat(reviews.count()).isEqualTo(1);
        MockMultipartFile proof = new MockMultipartFile("evidence", "receipt.pdf", "application/pdf", "%PDF-1.4\nfixture receipt".getBytes());
        mvc.perform(multipart("/v1/admin/finance/orders/{id}/settlement", order).file(proof).param("amount", "6.50").param("reference", "bank-transfer-test-1")
                .header("Authorization", token(admin))).andExpect(status().isOk());
        mvc.perform(multipart("/v1/admin/finance/orders/{id}/settlement", order).file(proof).param("amount", "6.50").param("reference", "bank-transfer-test-1")
                .header("Authorization", token(admin))).andExpect(status().isOk());
        assertThat(settlements.count()).isEqualTo(1);
        assertThat(orders.findById(order).orElseThrow().getSettlementStatus()).isEqualTo(Order.SettlementStatus.recorded);
        mvc.perform(get("/v1/admin/finance/orders/{id}/evidence", order).header("Authorization", token(requester))).andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/finance/orders/{id}/evidence", order).header("Authorization", token(admin))).andExpect(status().isOk());
    }
    @Test void samePaymentIsReusedAndDifferentAmountNotificationIsRejected() throws Exception {
        long order = acceptedService(); PaymentAttempt attempt = invoice(order); invoice(order);
        assertThat(attempts.count()).isEqualTo(1);
        when(wechat.verifyNotification(anyString(), anyMap())).thenReturn(paid(attempt, new BigDecimal("0.01")));
        mvc.perform(post("/v1/payments/notify/wechat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(orders.findById(order).orElseThrow().getPaymentStatus()).isEqualTo(Order.PaymentStatus.unpaid);
    }
    @Test void fakeSignatureAndUnrelatedUserCannotChangePaymentState() throws Exception {
        long order = acceptedService(); invoice(order);
        mvc.perform(post("/v1/payments/orders/{id}", order).header("Authorization", token(provider))
                .header("X-User-Id", requester.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"WECHAT\"}"))
                .andExpect(status().isForbidden());
        when(wechat.verifyNotification(anyString(), anyMap())).thenThrow(new com.campushub.common.BusinessException(400, "签名无效"));
        mvc.perform(post("/v1/payments/notify/wechat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(orders.findById(order).orElseThrow().getPaymentStatus()).isEqualTo(Order.PaymentStatus.unpaid);
    }
    @Test void queryingChannelCompensatesForMissingNotification() throws Exception {
        long order = acceptedService(); PaymentAttempt attempt = invoice(order);
        when(wechat.query(attempt.getOutTradeNo())).thenReturn(paid(attempt, attempt.getAmount()));
        mvc.perform(get("/v1/payments/orders/{id}", order).header("Authorization", token(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        assertThat(orders.findById(order).orElseThrow().getReviewEligibleAt()).isNotNull();
    }
    /** 后台需求审核/删除改走 TaskService 后，真实过滤器与数据库下的状态前置、重复操作与审计保留规则。 */
    @Test void adminTaskModerationGoesThroughDomainServiceWithStateGuards() throws Exception {
        // 待审核需求：驳回成功并变成 cancelled，重复驳回与事后通过均被状态前置拒绝。
        long rejected = response(mvc.perform(post("/v1/tasks").header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON)
                .content(validTask().toString())).andExpect(status().isCreated())).path("data").path("id").asLong();
        mvc.perform(put("/v1/admin/tasks/{id}/reject", rejected).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"描述不清\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("cancelled"));
        mvc.perform(put("/v1/admin/tasks/{id}/reject", rejected).header("Authorization", token(admin))).andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/tasks/{id}/approve", rejected).header("Authorization", token(admin))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id = ? AND type = 'TASK_REVIEW_RESULT' AND content LIKE '%描述不清%'",
                Integer.class, requester.getId())).isEqualTo(1);

        // 已通过的需求：重复通过被拒，不会重复发审核通知；发布时间取业务时钟。
        long approved = publish(validTask());
        mvc.perform(put("/v1/admin/tasks/{id}/approve", approved).header("Authorization", token(admin))).andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/tasks/{id}/reject", approved).header("Authorization", token(admin))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id = ? AND content LIKE '%已通过审核%'",
                Integer.class, requester.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT published_at FROM tasks WHERE id = ?", java.sql.Timestamp.class, approved).toLocalDateTime())
                .isEqualTo(start);

        // 已有带金额快照订单：不得物理删除，需求与订单原样保留。
        accept(approved, provider);
        mvc.perform(delete("/v1/admin/tasks/{id}", approved).header("Authorization", token(admin))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, approved)).isEqualTo(1);
        assertThat(orders.findByTaskId(approved)).hasSize(1);

        // 没有订单的需求可以删除；非管理员不能走后台入口。
        mvc.perform(delete("/v1/admin/tasks/{id}", rejected).header("Authorization", token(requester))).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/tasks/{id}", rejected).header("Authorization", token(admin))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, rejected)).isZero();
        mvc.perform(delete("/v1/admin/tasks/{id}", rejected).header("Authorization", token(admin))).andExpect(status().isNotFound());
    }

    /** 取件码走免登录详情接口时，按真实 JWT 身份裁剪：匿名与无关用户为空，发布者与接单人可见。 */
    @Test void pickupCodeIsOnlyReturnedToRequesterAndAcceptedProviderOverHttp() throws Exception {
        ObjectNode body = validTask();
        body.put("pickupCode", "A-1234");
        long task = publish(body);
        User stranger = seed("str", User.Role.user);

        mvc.perform(get("/v1/tasks/{id}", task)).andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
        mvc.perform(get("/v1/tasks/{id}", task).header("Authorization", token(stranger))).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
        mvc.perform(get("/v1/tasks/{id}", task).header("Authorization", token(provider))).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
        mvc.perform(get("/v1/tasks/{id}", task).header("Authorization", token(requester))).andExpect(jsonPath("$.data.pickupCode").value("A-1234"));

        accept(task, provider);
        mvc.perform(get("/v1/tasks/{id}", task).header("Authorization", token(provider))).andExpect(jsonPath("$.data.pickupCode").value("A-1234"));
        mvc.perform(get("/v1/tasks/{id}", task).header("Authorization", token(stranger))).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
        mvc.perform(get("/v1/tasks/{id}", task)).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
    }

    @Test void unpaidDebtDeductsExactlyOnceThenAdministratorCanApplyAuditedPenalty() throws Exception {
        long order = acceptedService(); clock.set(start.plusHours(1));
        lifecycle.processDeadline(order); lifecycle.processDeadline(order);
        assertThat(users.findById(requester.getId()).orElseThrow().getCreditScore()).isEqualTo(90);
        assertThat(creditEvents.count()).isEqualTo(1);
        clock.set(start.plusHours(25)); lifecycle.processDeadline(order);
        String body = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"deduction\":20,\"ban\":true,\"reason\":\"经核实仍未付款\"}";
        for (int i = 0; i < 2; i++) mvc.perform(post("/v1/admin/finance/orders/{id}/penalty", order).header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        User penalized = users.findById(requester.getId()).orElseThrow();
        assertThat(penalized.getCreditScore()).isEqualTo(70); assertThat(penalized.getStatus()).isZero();
    }
    @Test void formalQualityDisputePausesTimersAndCanBeResolvedByAdministrator() throws Exception {
        long order = accept(publish(validTask()), provider);
        mvc.perform(post("/v1/orders/{id}/submit-completion", order).header("Authorization", token(provider))).andExpect(status().isOk());
        mvc.perform(post("/v1/complaints").header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order + ",\"reason\":\"质量不符\",\"description\":\"尚未送达约定地点\"}")).andExpect(status().isOk());
        clock.set(start.plusDays(4)); lifecycle.processDeadline(order);
        assertThat(orders.findById(order).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.awaiting_acceptance);
        assertThat(creditEvents.count()).isZero();
        mvc.perform(post("/v1/admin/finance/orders/{id}/resolve", order).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":3.25,\"paymentHours\":2,\"reason\":\"核实后双方按半价结算\"}")).andExpect(status().isOk());
        assertThat(orders.findById(order).orElseThrow().getPaymentDueAt()).isEqualTo(start.plusDays(4).plusHours(2));
        assertThat(orders.findById(order).orElseThrow().isDisputed()).isFalse();
    }
    @Test void sevenDayAutomaticReviewIsFourStarsAndDoesNotIncreaseCredit() throws Exception {
        long order = acceptedService(); PaymentAttempt attempt = invoice(order); notifyPaid(attempt);
        provider.setCreditScore(88); users.save(provider);
        clock.set(start.plusDays(7)); reviewService.submitDefaultReview(order); reviewService.submitDefaultReview(order);
        Review review = reviews.findByOrder_Id(order).orElseThrow();
        assertThat(review.getScore()).isEqualTo(4); assertThat(review.isAutomatic()).isTrue();
        assertThat(users.findById(provider.getId()).orElseThrow().getCreditScore()).isEqualTo(88);
    }
    @Test void reviewCommentCannotImpersonateAutomaticReviewOrBypassDeadline() throws Exception {
        long order = acceptedService(); notifyPaid(invoice(order)); clock.set(start.plusDays(7));
        mvc.perform(post("/v1/reviews/submit").header("Authorization", token(requester)).param("orderId", "" + order)
                .param("score", "5").param("comment", "默认好评")).andExpect(status().isBadRequest());
        assertThat(reviews.count()).isZero();
    }
    @Test void freeOrderNeedsAcceptanceButNoPaymentBeforeReview() throws Exception {
        ObjectNode body = validTask(); body.put("expressReward", BigDecimal.ZERO);
        long order = accept(publish(body), provider);
        mvc.perform(post("/v1/orders/{id}/submit-completion", order).header("Authorization", token(provider))).andExpect(status().isOk());
        mvc.perform(post("/v1/orders/{id}/complete", order).header("Authorization", token(requester))).andExpect(status().isOk());
        mvc.perform(post("/v1/reviews/submit").header("Authorization", token(requester)).param("orderId", "" + order).param("score", "4"))
                .andExpect(status().isOk());
    }
    @Test void missingFieldsAndValidationFailuresAreSpecific() throws Exception {
        ObjectNode body = validTask(); body.put("title", "短");
        mvc.perform(post("/v1/tasks").header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("title")));
        assertThat(orders.count()).isZero();
    }
    @Test void unauthenticatedOrdersAndSelfElevationAreRejected() throws Exception {
        mvc.perform(post("/v1/orders/accept").param("taskId", "1")).andExpect(status().isUnauthorized());
        // 角色切换接口已随角色合并删除；即便有人仍调用旧地址，也不能借此把自己变成管理员。
        mvc.perform(put("/v1/auth/role").header("Authorization", token(requester)).param("role", "admin"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        assertThat(users.findById(requester.getId()).orElseThrow().getRole()).isEqualTo(User.Role.user);
    }
    @Test void twoConcurrentProvidersProduceOnlyOneOrder() throws Exception {
        long task = publish(validTask()); User other = seed("other", User.Role.user);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (User user : List.of(provider, other)) results.add(workers.submit(() -> {
                startTogether.await();
                try { orderService.acceptTask(task, user.getId()); return true; }
                catch (com.campushub.common.BusinessException expected) { return false; }
            }));
            startTogether.countDown();
            int accepted = 0; for (var result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1); assertThat(orders.findByTaskId(task)).hasSize(1);
        } finally { workers.shutdownNow(); }
    }
    @Test void teamRecruitmentRemainsFreeAndAcceptsMultipleMembers() throws Exception {
        ObjectNode body = validTask(); body.put("taskType", "TEAM"); body.put("maxMembers", 3); body.put("reward", 999);
        long task = publish(body), first = accept(task, provider); User other = seed("other", User.Role.user);
        long second = accept(task, other);
        assertThat(orders.findById(first).orElseThrow().getAmountDue()).isZero();
        assertThat(orders.findById(second).orElseThrow().getPaymentStatus()).isEqualTo(Order.PaymentStatus.not_required);
    }
    @Test void tutoringTotalIsComputedOnServerAndFrozenAtAcceptance() throws Exception {
        ObjectNode body = validTask(); body.put("taskType", "TUTORING"); body.put("subject", "数学"); body.put("tutoringLocation", "图书馆");
        body.put("pricePerHour", new BigDecimal("30.00")); body.put("durationHours", new BigDecimal("1.50")); body.put("reward", "0.01");
        long order = accept(publish(body), provider);
        assertThat(orders.findById(order).orElseThrow().getAmountDue()).isEqualByComparingTo("45.00");
    }
    @Test void existingCreditRecoveryAddsOnePerDayAndStopsAt70() {
        provider.setCreditScore(69); users.save(provider);
        creditRecovery.recoverCreditScoreTask(); creditRecovery.recoverCreditScoreTask();
        assertThat(users.findById(provider.getId()).orElseThrow().getCreditScore()).isEqualTo(70);
        clock.set(start.plusDays(1)); creditRecovery.recoverCreditScoreTask();
        assertThat(users.findById(provider.getId()).orElseThrow().getCreditScore()).isEqualTo(70);
    }
    @Test void closedChannelOrderCanBeReopenedWithoutOverwritingItsHistory() throws Exception {
        long order = acceptedService(); PaymentAttempt old = invoice(order);
        when(wechat.query(old.getOutTradeNo())).thenReturn(new PaymentGateway.VerifiedPayment(PaymentAttempt.Channel.WECHAT,
                old.getOutTradeNo(), null, null, "CNY", "test-app", "test-merchant", false, null, true));
        mvc.perform(get("/v1/payments/orders/{id}", order).header("Authorization", token(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        PaymentAttempt next = invoice(order);
        assertThat(next.getOutTradeNo()).isNotEqualTo(old.getOutTradeNo());
        assertThat(attempts.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(PaymentAttempt.Status.CLOSED);
        assertThat(attempts.findAllByOrderIdOrderByIdDesc(order)).hasSize(2);
        notifyPaid(next);
        assertThat(orders.findById(order).orElseThrow().getPaymentStatus()).isEqualTo(Order.PaymentStatus.paid);
    }
    @Test void bannedUserCanLoginForPaymentAndAppealButCannotCreateNewTrade() throws Exception {
        long order = acceptedService();
        requester.setPassword(bcrypt.encode("Password1!")); requester.setStatus(0); users.save(requester);
        JsonNode login = response(mvc.perform(post("/v1/auth/login/password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("account", requester.getStudentId(), "password", "Password1!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.restricted").value(true)));
        String restricted = "Bearer " + login.path("data").path("token").asText();
        mvc.perform(post("/v1/tasks").header("Authorization", restricted).contentType(MediaType.APPLICATION_JSON).content(validTask().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/orders/accept").header("Authorization", restricted).param("taskId", "1")).andExpect(status().isForbidden());
        mvc.perform(get("/v1/orders/mine").header("Authorization", restricted)).andExpect(status().isOk());
        mvc.perform(post("/v1/payments/orders/{id}", order).header("Authorization", restricted).contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"WECHAT\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/complaints").header("Authorization", restricted).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order + ",\"reason\":\"付款纠纷\",\"description\":\"请求核对付款记录\"}")).andExpect(status().isOk());
    }
    @Test void registrationVerifiesCodeWithoutExposingItInApiResponse() throws Exception {
        String phone = "13800000999", studentId = "20261031999";
        mvc.perform(post("/v1/verification/send").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "studentId", studentId, "type", "REGISTER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        String code = verificationCodes.findByPhoneAndTypeAndUsedFalse(phone, "REGISTER").get(0).getCode();
        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "studentId", studentId, "name", "测试同学", "password", "Password1!", "code", code, "nickname", "测试昵称"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("user")).andExpect(jsonPath("$.data.user.studentId").value(studentId))
                .andExpect(jsonPath("$.data.user.nickname").value("测试昵称")).andExpect(jsonPath("$.data.user.college").doesNotExist());
        JsonNode login = response(mvc.perform(post("/v1/auth/login/password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("account", studentId, "password", "Password1!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.creditScore").value(100)));
        // 姓名在库里必须是密文，经实体读出来才是明文。
        String storedName = jdbc.queryForObject("SELECT name FROM users WHERE student_id = ?", String.class, studentId);
        assertThat(storedName).startsWith("gcm:").doesNotContain("测试同学");
        assertThat(users.findByStudentId(studentId).orElseThrow().getName()).isEqualTo("测试同学");
        // 学院注册时未填，之后在个人资料里补充；超长昵称给出 400 而不是撞数据库约束。
        String self = "Bearer " + login.path("data").path("token").asText();
        mvc.perform(put("/v1/auth/profile").header("Authorization", self).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("college", "计算机学院")))).andExpect(status().isOk());
        mvc.perform(get("/v1/auth/profile").header("Authorization", self))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.college").value("计算机学院")).andExpect(jsonPath("$.data.nickname").value("测试昵称"));
        mvc.perform(put("/v1/auth/profile").header("Authorization", self).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nickname", "十一个汉字的昵称超长了呀")))).andExpect(status().isBadRequest());
    }
    @Test void identityFieldsAreImmutableAndAdminNameIsFixed() throws Exception {
        // 个人资料请求里夹带学号不会被采纳，学号和邮箱保持注册时的值，昵称正常修改。
        mvc.perform(put("/v1/auth/profile").header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("studentId", "20269999999", "nickname", "新昵称")))).andExpect(status().isOk());
        mvc.perform(get("/v1/auth/profile").header("Authorization", token(requester))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(requester.getStudentId())).andExpect(jsonPath("$.data.email").value(requester.getEmail()))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
        // 绕过服务层直接改实体，学号、姓名、邮箱也不会落库。
        User loaded = users.findById(requester.getId()).orElseThrow();
        loaded.setStudentId("changed"); loaded.setName("改名"); loaded.setEmail("changed@example.invalid"); users.saveAndFlush(loaded);
        assertThat(jdbc.queryForObject("SELECT CONCAT(student_id, '|', email) FROM users WHERE id = ?", String.class, requester.getId()))
                .isEqualTo(requester.getStudentId() + "|" + requester.getEmail());
        assertThat(users.findById(requester.getId()).orElseThrow().getName()).isEqualTo("req");
        // 管理员：创建时传入的姓名被固定姓名覆盖；即使用 SQL 写入别的姓名，读出来仍是固定姓名。
        assertThat(users.findById(admin.getId()).orElseThrow().getName()).isEqualTo(User.ADMIN_NAME);
        jdbc.update("UPDATE users SET name = 'someone' WHERE id = ?", admin.getId());
        assertThat(users.findById(admin.getId()).orElseThrow().getName()).isEqualTo(User.ADMIN_NAME);
    }
    @Test void registrationCodeCannotBeUsedForAnotherStudentId() throws Exception {
        String phone = "13800000888";
        mvc.perform(post("/v1/verification/send").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "studentId", "20261031888", "type", "REGISTER")))).andExpect(status().isOk());
        String code = verificationCodes.findByPhoneAndTypeAndUsedFalse(phone, "REGISTER").get(0).getCode();
        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "studentId", "20261031777", "name", "测试同学", "password", "Password1!", "code", code, "nickname", "测试昵称"))))
                .andExpect(status().isBadRequest());
        assertThat(users.existsByStudentId("20261031777")).isFalse();
    }
    @Test void validationUsesBusinessClockRatherThanMachineCalendar() throws Exception {
        clock.set(LocalDateTime.of(2000, 1, 1, 12, 0));
        ObjectNode body = validTask(); body.put("deadline", LocalDateTime.now(clock).plusDays(1).toString());
        mvc.perform(post("/v1/tasks").header("Authorization", token(requester)).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated());
    }
}
