package com.campushub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 免登录白名单与受保护入口的边界：只断言“是否被认证拦截”，不依赖业务数据是否存在。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityWhitelistTest {

    @Autowired MockMvc mvc;

    @Test
    void orderReviewIsPublicPerContract() throws Exception {
        // 契约 6.2：游客可查订单评价。订单不存在时业务返回什么都行，但绝不能是 401/403。
        int status = mvc.perform(get("/v1/reviews/order/999999")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void submittingReviewStillRequiresLogin() throws Exception {
        int status = mvc.perform(post("/v1/reviews/submit").param("orderId", "1").param("score", "5"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }
}
