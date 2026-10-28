package com.campushub.chat.service;

import com.campushub.chat.entity.Conversation;
import com.campushub.chat.repository.ConversationRepository;
import com.campushub.task.Task;
import com.campushub.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GroupCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupCleanupScheduler.class);
    // 测试用：5分钟生命周期
    private static final long LIFECYCLE_MINUTES = 5;

    private final ConversationRepository convRepo;
    private final ConversationService convService;
    private final MessageService msgService;
    private final TaskRepository taskRepo;

    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    @Transactional
    public void cleanupExpiredGroups() {
        log.debug("定时检查过期会话...");
        List<Conversation> all = new ArrayList<>();
        all.addAll(convRepo.findByConvType("TEAM_GROUP"));
        all.addAll(convRepo.findByConvType("TASK_PRIVATE"));

        for (Conversation conv : all) {
            if (conv.getTaskId() == null) continue;
            Task task = taskRepo.findById(conv.getTaskId()).orElse(null);
            if (task == null) continue;

            /* 生命周期从需求方确认完成/取消后开始计时，不以截止时间为准 */
            boolean isExpired = "completed".equals(task.getStatus().name())
                    || "cancelled".equals(task.getStatus().name());

            if (!isExpired) continue;

            LocalDateTime expiryTime = task.getUpdatedAt();
            if (expiryTime.plusMinutes(LIFECYCLE_MINUTES).isAfter(LocalDateTime.now())) continue;

            String typeName = "TEAM_GROUP".equals(conv.getConvType()) ? "群聊" : "私聊";

            if (conv.getKeepGroup() == null) {
                log.info("{} {} 已过期，发送保留询问通知", typeName, conv.getId());
                msgService.saveAndGet(conv.getId(), 0L,
                        typeName + "已闲置超过" + LIFECYCLE_MINUTES + "分钟，是否保留？" + LIFECYCLE_MINUTES + "分钟后未确认将自动删除",
                        "SYSTEM");
                conv.setKeepGroup(0);
                conv.setKeepAskedAt(LocalDateTime.now());
                convRepo.save(conv);
            } else if (conv.getKeepGroup() == 0 && conv.getKeepAskedAt() != null) {
                if (conv.getKeepAskedAt().plusMinutes(LIFECYCLE_MINUTES).isBefore(LocalDateTime.now())) {
                    log.info("{} {} 超时未确认保留，自动删除", typeName, conv.getId());
                    msgService.saveAndGet(conv.getId(), 0L,
                            typeName + "已超过保留确认时间，自动删除", "SYSTEM");
                    convService.dismissGroup(conv.getId());
                }
            }
        }
    }
}
