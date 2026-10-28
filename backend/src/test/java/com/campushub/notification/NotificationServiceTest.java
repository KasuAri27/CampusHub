package com.campushub.notification;

import com.campushub.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createNotificationDefaultsToUnread() {
        notificationService.createNotification(1L, "订单通知", "有人接单", "ORDER");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getReceiverId()).isEqualTo(1L);
        assertThat(saved.getTitle()).isEqualTo("订单通知");
        assertThat(saved.getContent()).isEqualTo("有人接单");
        assertThat(saved.getType()).isEqualTo("ORDER");
        assertThat(saved.getIsRead()).isFalse();
    }

    @Test
    void markAsReadUpdatesOnlyOwnerUnreadNotification() {
        Notification notification = new Notification();
        notification.setId(10L);
        notification.setReceiverId(1L);
        notification.setIsRead(false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 10L);

        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.getReadTime()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsReadRejectsOtherUserNotification() {
        Notification notification = new Notification();
        notification.setId(10L);
        notification.setReceiverId(1L);
        notification.setIsRead(false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作此通知");

        verify(notificationRepository, never()).save(any());
    }
}
