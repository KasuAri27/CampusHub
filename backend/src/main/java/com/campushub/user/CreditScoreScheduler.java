package com.campushub.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 信用分每日恢复调度器。
 * 每天零点利用数据库日期唯一键进行集群互斥，为信誉受损（低于70分）的用户自动补偿恢复1点信用分。
 */
public class CreditScoreScheduler {

    private final UserRepository userRepository;
    private final CreditRecoveryRunRepository runs;
    private final java.time.Clock clock;

    // fixedRate = 60000 * 60 * 24 表示每隔 1 天执行一次
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void recoverCreditScoreTask() {
        java.time.LocalDate day = java.time.LocalDate.now(clock);
        if (runs.existsById(day)) return;
        CreditRecoveryRun run = new CreditRecoveryRun();
        run.setRecoveryDay(day);
        // 先占日期唯一键，再更新分数；并发插入失败的事务不会执行恢复。
        runs.saveAndFlush(run);
        int updatedCount = userRepository.recoverCreditScore();
        if (updatedCount > 0) {
            log.info("定时任务执行：已自动为 {} 名信用分低于70分的用户恢复1点信用分", updatedCount);
        }
    }
}
