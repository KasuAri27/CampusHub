package com.campushub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.ZoneId;

/** 校园业务时间统一为上海时区；测试可替换 Clock，无需真实等待业务截止。 */
@Configuration
public class BusinessTimeConfig {
    /** @Future 与订单期限使用同一个时钟，避免测试或服务器时区出现两个“现在”。 */
    @Bean
    public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator(Clock clock) {
        var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validator.setConfigurationInitializer(configuration -> configuration.clockProvider(() -> clock));
        return validator;
    }
    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }

    /** 测试关闭自动调度，单独调用扫描方法验证边界，避免后台任务污染用例。 */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "campushub.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfiguration { }
}
