package com.campushub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 连通性检测：用应用自身的连接配置（CAMPUSHUB_REDIS_*）对真实 Redis 做 PING 和一次读写往返，
 * 不使用内嵌或模拟的 Redis，连不上就是真实失败。
 *
 * <p>前置条件：环境变量 CAMPUSHUB_TEST_REDIS_REQUIRED=true 时才执行。CI 的后端任务自带 Redis 服务并设置该变量，
 * 且流水线不允许出现被跳过的用例，所以本检测在 CI 中必定运行；本机未启动 Redis 时默认跳过，不阻塞日常构建。
 *
 * <p>只加载 Redis 切片，不启动数据库与 Web 层。写入的键带随机后缀并设置过期时间，
 * 不读取、不清空库内其他数据，可以指向共用的开发实例。
 */
@DataRedisTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CAMPUSHUB_TEST_REDIS_REQUIRED", matches = "true")
class RedisConnectivityTest {
    @Autowired StringRedisTemplate redis;

    @Test void configuredRedisAnswersPingAndRoundTripsValue() {
        String pong = redis.execute((RedisCallback<String>) RedisConnection::ping);
        assertThat(pong).isEqualTo("PONG");

        String key = "campushub:ci:connectivity:" + UUID.randomUUID();
        try {
            // 过期时间兜底：即使断言失败后删除未执行，探测键也不会长期残留。
            redis.opsForValue().set(key, "ok", Duration.ofSeconds(30));
            assertThat(redis.opsForValue().get(key)).isEqualTo("ok");
        } finally {
            redis.delete(key);
        }
    }
}
