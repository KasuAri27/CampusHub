package com.campushub.chat.service;

import com.campushub.chat.dto.ConversationDTO;
import com.campushub.chat.entity.Conversation;
import com.campushub.chat.repository.ConversationMemberRepository;
import com.campushub.chat.repository.ConversationRepository;
import com.campushub.chat.repository.MessageRepository;
import com.campushub.task.TaskChatView;
import com.campushub.task.TaskReadService;
import com.campushub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 会话列表与群聊操作权限改读需求只读视图（模块契约 §3.6）。 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTaskViewTest {

    @Mock private ConversationRepository convRepo;
    @Mock private ConversationMemberRepository memberRepo;
    @Mock private MessageRepository msgRepo;
    @Mock private UserRepository userRepo;
    @Mock private TaskReadService taskReadService;

    @InjectMocks
    private ConversationService service;

    private static Conversation teamGroup(long id, Long taskId, Long ownerId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setConvType("TEAM_GROUP");
        c.setTaskId(taskId);
        c.setOwnerId(ownerId);
        return c;
    }

    private static TaskChatView teamView(long taskId, String status, long requesterId) {
        return new TaskChatView(taskId, true, "拼车", status, requesterId, 4, null,
                LocalDateTime.now(), false);
    }

    @Test
    void listFetchesTaskViewsInOneBatch() {
        Conversation finished = teamGroup(50L, 1L, null);
        Conversation active = teamGroup(51L, 2L, 7L);
        Conversation system = new Conversation();
        system.setId(52L);
        system.setConvType("SYSTEM_NOTIFY");
        when(convRepo.findByUserId(7L)).thenReturn(List.of(finished, active, system));
        when(taskReadService.findChatViews(List.of(1L, 2L))).thenReturn(Map.of(
                1L, teamView(1L, "completed", 7L),
                2L, teamView(2L, "in_progress", 7L)));

        List<ConversationDTO> result = service.listConversations(7L);

        verify(taskReadService).findChatViews(List.of(1L, 2L));
        verify(taskReadService, never()).findChatView(anyLong());
        ConversationDTO finishedDto = result.stream().filter(d -> d.getId() == 50L).findFirst().orElseThrow();
        assertThat(finishedDto.getTaskStatus()).isEqualTo("completed");
        // 群主为空时由需求发布者兜底，终态且未确认保留即可解散
        assertThat(finishedDto.isOwner()).isTrue();
        assertThat(finishedDto.isCanDismiss()).isTrue();
        ConversationDTO activeDto = result.stream().filter(d -> d.getId() == 51L).findFirst().orElseThrow();
        assertThat(activeDto.isCanDismiss()).isFalse();
    }

    @Test
    void groupWithoutOwnerFallsBackToRequester() {
        Conversation conv = teamGroup(50L, 1L, null);
        when(taskReadService.findChatView(1L)).thenReturn(Optional.of(teamView(1L, "completed", 7L)));

        assertThat(service.hasOperatePermission(conv, 7L)).isTrue();
        assertThat(service.hasOperatePermission(conv, 8L)).isFalse();
    }

    @Test
    void groupWithoutOwnerAndMissingTaskIsDenied() {
        Conversation conv = teamGroup(50L, 1L, null);
        when(taskReadService.findChatView(1L)).thenReturn(Optional.empty());

        assertThat(service.hasOperatePermission(conv, 7L)).isFalse();
    }

    @Test
    void groupOwnerDecidesWithoutReadingTask() {
        Conversation conv = teamGroup(50L, 1L, 9L);

        assertThat(service.hasOperatePermission(conv, 9L)).isTrue();
        verify(taskReadService, never()).findChatView(anyLong());
    }
}
