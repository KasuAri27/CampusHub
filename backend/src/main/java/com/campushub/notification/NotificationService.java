package com.campushub.notification;

import com.campushub.chat.entity.Conversation;
import com.campushub.chat.service.ConversationService;
import com.campushub.chat.service.MessageService;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/** 站内通知服务，同步写入独立通知流水并投递至专属系统聊天会话。 */
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ConversationService convService;
    private final MessageService msgService;

    /**
     * 创建并分发系统通知：
     * 同一事务内双写通知表与系统通知聊天会话，确保历史站内信与实时聊天入口均可感知。
     */
    @Transactional
    public void createNotification(Long receiverId, String title, String content, String type) {
        // 通知表作为统一查询来源，同时在系统会话展示；两个写入处于同一事务，避免旧入口漏通知。
        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notificationRepository.save(notification);
        Conversation conv = convService.getOrCreateSystemNotify(receiverId);
        String fullContent = "【" + title + "】" + content;
        msgService.saveAndGet(conv.getId(), 0L, fullContent, "SYSTEM");
    }

    public PageResult<Notification> getUserNotifications(Long userId, int pageNum, int pageSize, Boolean unreadOnly) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize);
        var page = (unreadOnly != null && unreadOnly)
                ? notificationRepository.findByReceiverIdAndIsReadFalseOrderByCreateTimeDesc(userId, pageRequest)
                : notificationRepository.findByReceiverIdOrderByCreateTimeDesc(userId, pageRequest);
        return PageResult.of(page);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("通知不存在"));
        if (!notification.getReceiverId().equals(userId)) {
            throw new BusinessException("无权操作此通知");
        }
        if (!notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadTime(java.time.LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByReceiverId(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByReceiverIdAndIsReadFalse(userId);
    }
}
