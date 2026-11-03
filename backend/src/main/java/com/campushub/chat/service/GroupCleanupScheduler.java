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
/**
 * 聊天会话生命周期回收调度器。
 * 任务完成或取消后启动会话淘汰计时；处于未支付、未结算或争议状态的会话予以豁免以保留证据。
 */
public class GroupCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupCleanupScheduler.class);
    // 测试用：5分钟生命周期
    private static final long LIFECYCLE_MINUTES = 5;

    private final ConversationRepository convRepo;
    private final ConversationService convService;
    private final MessageService msgService;
    private final TaskRepository taskRepo;
    private final com.campushub.order.OrderRepository orders;

    /**
     * 定时扫描两阶段淘汰：
     * 1. 任务终态且闲置超期后，向会话发送保留询问通知并将 keepGroup 设为 0；
     * 2. 若再次超期仍未被用户确认保留，则系统自动解散删除该会话。
     */
    @Scheduled(fixedRate = 60000)
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
            // 待付款、待结算和争议中的会话不能按旧的演示用生命周期提前下线。
            if (orders.findByTaskId(task.getId()).stream().anyMatch(order -> order.isDisputed()
                    || order.getPaymentStatus() == com.campushub.order.Order.PaymentStatus.unpaid
                    || order.getPaymentStatus() == com.campushub.order.Order.PaymentStatus.paid
                    && order.getSettlementStatus() != com.campushub.order.Order.SettlementStatus.recorded)) continue;

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
