package com.campushub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CampusHub 校园互助服务平台主启动类。
 * 负责 Spring Boot 容器初始化、各领域组件扫描与控制台端口指引输出。
 */
@SpringBootApplication
public class CampusHubApplication {

    private static final Logger log = LoggerFactory.getLogger(CampusHubApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CampusHubApplication.class, args);
    }

    @Bean
    CommandLineRunner startupBanner(Environment env) {
        return args -> {
            String port = env.getProperty("server.port", "8080");
            log.info("============================================================");
            log.info("  CampusHub 启动成功");
            log.info("  REST API: http://localhost:{}/api", port);
            log.info("  WebSocket: ws://localhost:{}/api/ws/chat", port);
            log.info("============================================================");
        };
    }
}
