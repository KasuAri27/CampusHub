package com.campushub.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByReceiverIdOrderByCreateTimeDesc(Long receiverId, Pageable pageable);
    Page<Notification> findByReceiverIdAndIsReadFalseOrderByCreateTimeDesc(Long receiverId, Pageable pageable);
    long countByReceiverIdAndIsReadFalse(Long receiverId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readTime = CURRENT_TIMESTAMP WHERE n.receiverId = ?1 AND n.isRead = false")
    void markAllAsReadByReceiverId(Long receiverId);
}