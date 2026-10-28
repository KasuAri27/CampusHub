package com.campushub.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditScoreScheduler {

    private final UserRepository userRepository;

    // fixedRate = 60000 * 60 * 24 表示每隔 1 天执行一次
    @Scheduled(fixedRate = 60000 * 60 * 24)
    @Transactional
    public void recoverCreditScoreTask() {
        int updatedCount = userRepository.recoverCreditScore();
        if (updatedCount > 0) {
            log.info("定时任务执行：已自动为 {} 名信用分低于70分的用户恢复1点信用分", updatedCount);
        }
    }
}