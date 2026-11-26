package com.campushub.chat.service;

import com.campushub.chat.entity.Conversation;
import com.campushub.common.BusinessException;
import com.campushub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 聊天联动标量签名（模块契约 §3.3）：只凭调用方传入的标量完成建群、入群、私聊与退群。 */
@ExtendWith(MockitoExtension.class)
class ChatIntegrationServiceTest {

    @Mock private ConversationService convService;
    @Mock private MessageService msgService;
    @Mock private UserRepository userRepo;

    @InjectMocks
    private ChatIntegrationService service;

    private static Conversation conv(long id, Integer maxMembers) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setMaxMembers(maxMembers);
        return c;
    }

    @Test
    void createTeamGroupUsesScalarArguments() {
        when(userRepo.findById(7L)).thenReturn(Optional.empty());
        when(convService.createTeamGroup(1L, "「拼车」组队群", 7L, 4)).thenReturn(conv(50L, 4));

        service.createTeamGroup(1L, "拼车", 7L, 4);

        verify(msgService).saveAndGet(50L, 0L, "用户7 创建了群聊", "SYSTEM");
    }

    @Test
    void acceptTeamTaskJoinsGroupWhenNotFull() {
        when(convService.findByTaskAndType(1L, "TEAM_GROUP")).thenReturn(conv(50L, 3));
        when(convService.getMemberCount(50L)).thenReturn(2L);

        service.handleAfterAccept(1L, true, "拼车", 7L, 100L, 9L);

        verify(convService).addMember(50L, 9L, "MEMBER");
        verify(convService, never()).createPrivate(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void acceptTeamTaskRejectsWhenGroupFull() {
        when(convService.findByTaskAndType(1L, "TEAM_GROUP")).thenReturn(conv(50L, 3));
        when(convService.getMemberCount(50L)).thenReturn(3L);

        assertThatThrownBy(() -> service.handleAfterAccept(1L, true, "拼车", 7L, 100L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该组队已满员");
        verify(convService, never()).addMember(anyLong(), anyLong(), anyString());
    }

    @Test
    void acceptSingleTaskCreatesPrivateChatPerOrder() {
        when(convService.findByTaskOrderAndType(1L, 100L, "TASK_PRIVATE")).thenReturn(null);
        when(convService.createPrivate(1L, 100L, 7L, 9L)).thenReturn(conv(60L, null));

        service.handleAfterAccept(1L, false, "代取快递", 7L, 100L, 9L);

        verify(msgService).saveAndGet(eq(60L), eq(0L), contains("「代取快递」"), eq("SYSTEM"));
        verify(convService, never()).findByTaskAndType(anyLong(), anyString());
    }

    @Test
    void acceptSingleTaskReusesExistingPrivateChat() {
        when(convService.findByTaskOrderAndType(1L, 100L, "TASK_PRIVATE")).thenReturn(conv(60L, null));

        service.handleAfterAccept(1L, false, "代取快递", 7L, 100L, 9L);

        verify(convService, never()).createPrivate(anyLong(), anyLong(), anyLong(), anyLong());
        verifyNoInteractions(msgService);
    }

    @Test
    void cancelByMemberLeavesTeamGroup() {
        when(convService.findByTaskAndType(1L, "TEAM_GROUP")).thenReturn(conv(50L, 3));

        service.handleCancelOrder(1L, true, 9L, false);

        verify(convService).removeMember(50L, 9L);
        verify(msgService).saveAndGet(eq(50L), eq(0L), contains("退出了群聊"), eq("SYSTEM"));
    }

    @Test
    void cancelByRequesterAnnouncesDismissal() {
        when(convService.findByTaskAndType(1L, "TEAM_GROUP")).thenReturn(conv(50L, 3));

        service.handleCancelOrder(1L, true, 7L, true);

        verify(msgService).saveAndGet(50L, 0L, "发起人取消任务，群聊即将解散", "SYSTEM");
        verify(convService).removeMember(50L, 7L);
    }

    @Test
    void cancelSingleTaskDoesNothing() {
        service.handleCancelOrder(1L, false, 9L, false);

        verifyNoInteractions(convService, msgService, userRepo);
    }
}
