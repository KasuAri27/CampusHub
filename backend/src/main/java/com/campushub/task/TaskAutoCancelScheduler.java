package com.campushub.task;

import com.campushub.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAutoCancelScheduler {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    // 每隔 60 秒检查一次过期任务
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoCancelExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        // 查找所有状态为 published 且超时未接单的需求
        List<Task> expiredTasks = taskRepository.findExpiredTasks(Task.TaskStatus.published, now);

        if (!expiredTasks.isEmpty()) {
            for (Task task : expiredTasks) {
                // 1. 设置状态为已取消
                task.setStatus(Task.TaskStatus.cancelled);
                taskRepository.save(task);

                // 2. 给需求发布方发送系统通知
                String title = "需求已过期自动取消";
                String content = String.format("您的需求【%s】由于达到截止时间且无人接单，已由系统自动取消。", task.getTitle());

                // notificationService 的参数为 (receiverId, title, content, type)
                notificationService.createNotification(
                        task.getRequester().getId(),
                        title,
                        content,
                        "SYSTEM"
                );

                log.info("需求 [ID: {}] 超时未接单，系统已自动取消并发送通知。", task.getId());
            }
        }
    }
}