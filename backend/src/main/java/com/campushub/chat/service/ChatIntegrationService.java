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
 * 业务领域与聊天模块的联动服务（模块契约 §3.3）。
 * 负责在任务审核通过、接单、取消订单、任务完成等业务生命周期节点，自动维护对应的群聊或私聊会话及系统通知。
 * <p>
 * 过渡期（K3）：标量参数版本是契约目标签名，新增调用一律使用；收 {@link Task} 实体的旧版本只做转发，
 * 待交易核心切换调用方后删除。是否组队由调用方按需求辨别列判断后以 {@code team} 传入，聊天侧不再识别实体子类。
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

    /**
     * 组队需求审核通过后建群，发布者为群主并写入“创建了群聊”系统消息。
     *
     * @param taskId      组队需求 ID
     * @param title       需求标题，用于拼群名
     * @param requesterId 发布者用户 ID，成为群主
     * @param maxMembers  群人数上限（含发起人），接单入群时按它校验容量
     */
    @Transactional
    public void createTeamGroup(Long taskId, String title, Long requesterId, int maxMembers) {
        String ownerName = userName(requesterId);
        Conversation conv = convService.createTeamGroup(
                taskId,
                "「" + title + "」组队群",
                requesterId,
                maxMembers
        );
        msgService.saveAndGet(conv.getId(), 0L,
                ownerName + " 创建了群聊", "SYSTEM");
    }

    /** 旧签名，仅转发到标量版本；K3 第④步删除。 */
    @Transactional
    public void createTeamGroup(Task task) {
        TeamingTask tm = (TeamingTask) task;
        createTeamGroup(task.getId(), task.getTitle(), task.getRequester().getId(), tm.getMaxMembers());
    }

    /**
     * 接单后自动建联：组队任务将成员加入团队群并校验容量；单人任务则自动建立两人私聊通道。
     * 私聊按订单隔离，同一订单重复调用不会重复建会话。
     *
     * @param taskId      需求 ID
     * @param team        是否组队需求，由调用方按辨别列判断
     * @param title       需求标题，写入私聊开场系统消息
     * @param requesterId 发布者用户 ID
     * @param orderId     本次接单产生的订单 ID
     * @param providerId  接单方用户 ID
     * @throws BusinessException 组队群已满员（400），调用方事务随之回滚
     */
    @Transactional
    public void handleAfterAccept(Long taskId, boolean team, String title, Long requesterId,
                                  Long orderId, Long providerId) {
        String providerName = userName(providerId);
        if (team) {
            Conversation conv = convService.findByTaskAndType(taskId, "TEAM_GROUP");
            if (conv == null) return;
            long current = convService.getMemberCount(conv.getId());
            if (current >= conv.getMaxMembers()) {
                throw new BusinessException(400, "该组队已满员");
            }
            convService.addMember(conv.getId(), providerId, "MEMBER");
            msgService.saveAndGet(conv.getId(), 0L,
                    providerName + " 加入了群聊", "SYSTEM");
        } else {
            String requesterName = userName(requesterId);
            Conversation existing = convService.findByTaskOrderAndType(taskId, orderId, "TASK_PRIVATE");
            if (existing == null) {
                Conversation conv = convService.createPrivate(
                        taskId, orderId,
                        requesterId, providerId
                );
                msgService.saveAndGet(conv.getId(), 0L,
                        providerName + " 接取了 " + requesterName + " 的需求「" + title + "」，开始沟通吧", "SYSTEM");
            }
        }
    }

    /** 旧签名，仅转发到标量版本，组队判断沿用原有的实体子类判断；K3 第④步删除。 */
    @Transactional
    public void handleAfterAccept(Task task, Long orderId, Long providerId) {
        handleAfterAccept(task.getId(), task instanceof TeamingTask, task.getTitle(),
                task.getRequester().getId(), orderId, providerId);
    }

    /**
     * 订单取消时的会话处理：发起人取消将触发组队群解散提示，普通成员取消则移出群聊并广播动态。
     * 单人需求的私聊不在这里处理，交由群聊清理任务按需求状态回收。
     *
     * @param taskId      需求 ID
     * @param team        是否组队需求，由调用方按辨别列判断；为 false 时不做任何处理
     * @param userId      发起取消的用户 ID
     * @param isRequester 取消方是否为需求发布者
     */
    @Transactional
    public void handleCancelOrder(Long taskId, boolean team, Long userId, boolean isRequester) {
        if (!team) return;
        String uname = userName(userId);
        Conversation conv = convService.findByTaskAndType(taskId, "TEAM_GROUP");
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

    /** 旧签名，仅转发到标量版本，组队判断沿用原有的实体子类判断；K3 第④步删除。 */
    @Transactional
    public void handleCancelOrder(Task task, Long userId, boolean isRequester) {
        handleCancelOrder(task.getId(), task instanceof TeamingTask, userId, isRequester);
    }

    /** 无调用方，契约 §3.3 修订后删除；K3 第④步与旧签名一并清理。 */
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
