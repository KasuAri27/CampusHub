package com.campushub.chat.service;

import com.campushub.chat.entity.Conversation;
import com.campushub.chat.repository.ConversationRepository;
import com.campushub.task.TaskChatView;
import com.campushub.task.TaskReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 群聊清理只依据需求只读视图（模块契约 §3.6）：订单义务看结论字段，闲置计时看需求更新时间。 */
@ExtendWith(MockitoExtension.class)
class GroupCleanupSchedulerTest {

    @Mock private ConversationRepository convRepo;
    @Mock private ConversationService convService;
    @Mock private MessageService msgService;
    @Mock private TaskReadService taskReadService;

    @InjectMocks
    private GroupCleanupScheduler scheduler;

    private static Conversation privateConv(long id, long taskId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setConvType("TASK_PRIVATE");
        c.setTaskId(taskId);
        return c;
    }

    private static TaskChatView view(long taskId, String status, LocalDateTime updatedAt, boolean obligations) {
        return new TaskChatView(taskId, false, "代取快递", status, 7L, null, null, updatedAt, obligations);
    }

    private void givenPrivateConvs(Conversation... convs) {
        when(convRepo.findByConvType("TEAM_GROUP")).thenReturn(List.of());
        when(convRepo.findByConvType("TASK_PRIVATE")).thenReturn(List.of(convs));
    }

    @Test
    void openObligationsKeepConversation() {
        Conversation conv = privateConv(60L, 1L);
        givenPrivateConvs(conv);
        when(taskReadService.findChatViews(List.of(1L)))
                .thenReturn(Map.of(1L, view(1L, "completed", LocalDateTime.now().minusHours(1), true)));

        scheduler.cleanupExpiredGroups();

        assertThat(conv.getKeepGroup()).isNull();
        verify(msgService, never()).saveAndGet(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void idleFinishedTaskAsksToKeep() {
        Conversation conv = privateConv(60L, 1L);
        givenPrivateConvs(conv);
        when(taskReadService.findChatViews(List.of(1L)))
                .thenReturn(Map.of(1L, view(1L, "completed", LocalDateTime.now().minusHours(1), false)));

        scheduler.cleanupExpiredGroups();

        assertThat(conv.getKeepGroup()).isZero();
        assertThat(conv.getKeepAskedAt()).isNotNull();
        verify(msgService).saveAndGet(eq(60L), eq(0L), contains("是否保留"), eq("SYSTEM"));
        verify(convRepo).save(conv);
    }

    @Test
    void recentlyFinishedTaskIsNotYetIdle() {
        Conversation conv = privateConv(60L, 1L);
        givenPrivateConvs(conv);
        when(taskReadService.findChatViews(List.of(1L)))
                .thenReturn(Map.of(1L, view(1L, "cancelled", LocalDateTime.now(), false)));

        scheduler.cleanupExpiredGroups();

        assertThat(conv.getKeepGroup()).isNull();
        verify(convRepo, never()).save(any());
    }

    @Test
    void activeTaskIsNeverCleaned() {
        Conversation conv = privateConv(60L, 1L);
        givenPrivateConvs(conv);
        when(taskReadService.findChatViews(List.of(1L)))
                .thenReturn(Map.of(1L, view(1L, "in_progress", LocalDateTime.now().minusHours(1), false)));

        scheduler.cleanupExpiredGroups();

        verify(convRepo, never()).save(any());
    }

    @Test
    void unconfirmedAfterAskIsDismissed() {
        Conversation conv = privateConv(60L, 1L);
        conv.setKeepGroup(0);
        conv.setKeepAskedAt(LocalDateTime.now().minusHours(1));
        givenPrivateConvs(conv);
        when(taskReadService.findChatViews(List.of(1L)))
                .thenReturn(Map.of(1L, view(1L, "completed", LocalDateTime.now().minusHours(2), false)));

        scheduler.cleanupExpiredGroups();

        verify(convService).dismissGroup(60L);
    }

    @Test
    void missingTaskViewIsSkipped() {
        Conversation conv = privateConv(60L, 1L);
        givenPrivateConvs(conv);
        when(taskReadService.findChatViews(List.of(1L))).thenReturn(Map.of());

        scheduler.cleanupExpiredGroups();

        verify(convRepo, never()).save(any());
        verify(convService, never()).dismissGroup(anyLong());
    }
}
