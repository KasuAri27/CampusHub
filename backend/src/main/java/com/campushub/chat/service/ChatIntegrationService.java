package com.campushub.chat.service;

import com.campushub.chat.entity.Conversation;
import com.campushub.common.BusinessException;
import com.campushub.task.Task;
import com.campushub.task.TeamingTask;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/**
 * 业务领域与聊天模块的联动服务。
 * 负责在任务审核通过、接单、取消订单、任务完成等业务生命周期节点，自动维护对应的群聊或私聊会话及系统通知。
 */
public class ChatIntegrationService {

    private final ConversationService convService;
    private final MessageService msgService;
    private final UserRepository userRepo;

    private String userName(Long userId) {
        if (userId == null || userId == 0L) return "系统";
        User u = userRepo.findById(userId).orElse(null);
        return u != null ? (u.getNickname() != null ? u.getNickname() : u.getName()) : "用户" + userId;
    }

    @Transactional
    public void createTeamGroup(Task task) {
        TeamingTask tm = (TeamingTask) task;
        String ownerName = userName(task.getRequester().getId());
        Conversation conv = convService.createTeamGroup(
                task.getId(),
                "「" + task.getTitle() + "」组队群",
                task.getRequester().getId(),
                tm.getMaxMembers()
        );
        msgService.saveAndGet(conv.getId(), 0L,
                ownerName + " 创建了群聊", "SYSTEM");
    }

    /** 接单后自动建联：组队任务将成员加入团队群并校验容量；单人任务则自动建立两人私聊通道。 */
    @Transactional
    public void handleAfterAccept(Task task, Long orderId, Long providerId) {
        String providerName = userName(providerId);
        if (task instanceof TeamingTask) {
            Conversation conv = convService.findByTaskAndType(task.getId(), "TEAM_GROUP");
            if (conv == null) return;
            long current = convService.getMemberCount(conv.getId());
            if (current >= conv.getMaxMembers()) {
                throw new BusinessException(400, "该组队已满员");
            }
            convService.addMember(conv.getId(), providerId, "MEMBER");
            msgService.saveAndGet(conv.getId(), 0L,
                    providerName + " 加入了群聊", "SYSTEM");
        } else {
            String requesterName = userName(task.getRequester().getId());
            Conversation existing = convService.findByTaskOrderAndType(task.getId(), orderId, "TASK_PRIVATE");
            if (existing == null) {
                Conversation conv = convService.createPrivate(
                        task.getId(), orderId,
                        task.getRequester().getId(), providerId
                );
                msgService.saveAndGet(conv.getId(), 0L,
                        providerName + " 接取了 " + requesterName + " 的需求「" + task.getTitle() + "」，开始沟通吧", "SYSTEM");
            }
        }
    }

    /** 订单取消时的会话处理：发起人取消将触发组队群解散提示，普通成员取消则移出群聊并广播动态。 */
    @Transactional
    public void handleCancelOrder(Task task, Long userId, boolean isRequester) {
        String uname = userName(userId);
        if (task instanceof TeamingTask) {
            Conversation conv = convService.findByTaskAndType(task.getId(), "TEAM_GROUP");
            if (conv == null) return;
            if (isRequester) {
                msgService.saveAndGet(conv.getId(), 0L,
                        "发起人取消任务，群聊即将解散", "SYSTEM");
                convService.removeMember(conv.getId(), userId);
            } else {
                convService.removeMember(conv.getId(), userId);
                msgService.saveAndGet(conv.getId(), 0L,
                        uname + " 退出了群聊", "SYSTEM");
            }
        }
    }

    @Transactional
    public void handleTaskCompleted(Task task) {
        if (task instanceof TeamingTask) {
            Conversation conv = convService.findByTaskAndType(task.getId(), "TEAM_GROUP");
            if (conv == null) return;
            msgService.saveAndGet(conv.getId(), 0L,
                    "任务已完成！群主可决定是否解散群聊", "SYSTEM");
        }
    }
}
