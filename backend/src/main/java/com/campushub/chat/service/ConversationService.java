package com.campushub.chat.service;

import com.campushub.chat.dto.ConversationDTO;
import com.campushub.chat.dto.MessageDTO;
import com.campushub.chat.entity.Conversation;
import com.campushub.chat.entity.ConversationMember;
import com.campushub.chat.entity.Message;
import com.campushub.chat.repository.ConversationMemberRepository;
import com.campushub.chat.repository.ConversationRepository;
import com.campushub.chat.repository.MessageRepository;
import com.campushub.common.BusinessException;
import com.campushub.task.Task;
import com.campushub.task.TaskRepository;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository convRepo;
    private final ConversationMemberRepository memberRepo;
    private final MessageRepository msgRepo;
    private final UserRepository userRepo;
    private final TaskRepository taskRepo;

    @Transactional(readOnly = true)
    public List<ConversationDTO> listConversations(Long userId) {
        // 如果这里是给用户用的，查出来之后过滤掉被软删除的
        List<Conversation> convs = convRepo.findByUserId(userId).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted())) // 过滤掉已删除的
                .toList();
        Map<Long, User> userCache = new java.util.HashMap<>();

        List<ConversationDTO> result = new ArrayList<>();
        for (Conversation c : convs) {
            Message lastMsg = msgRepo.findLastMessage(c.getId());
            ConversationMember member = memberRepo.findByConvIdAndUserId(c.getId(), userId);
            LocalDateTime since = null;
            if (member != null) {
                since = member.getReadAt() != null ? member.getReadAt() : member.getJoinedAt();
            }
            long unread = since != null ? msgRepo.countUnreadSince(c.getId(), userId, since) : 0L;
            int memberCount = (int) memberRepo.countByConvId(c.getId());

            MessageDTO lastMsgDTO = null;
            if (lastMsg != null) {
                String senderName;
                if (lastMsg.getSenderId() == 0L) {
                    senderName = "系统通知";
                } else {
                    User sender = userCache.computeIfAbsent(lastMsg.getSenderId(),
                            id -> userRepo.findById(id).orElse(null));
                    senderName = sender != null ? (sender.getNickname() != null ? sender.getNickname() : sender.getName()) : "未知";
                }
                lastMsgDTO = MessageDTO.builder()
                        .id(lastMsg.getId()).convId(lastMsg.getConvId())
                        .senderId(lastMsg.getSenderId())
                        .senderName(senderName)
                        .msgType(lastMsg.getMsgType())
                        .content(lastMsg.getContent())
                        .isRecalled(lastMsg.getIsRecalled())
                        .createdAt(lastMsg.getCreatedAt())
                        .build();
            }

            // 私聊：获取对方用户名和任务标题
            String otherUserName = null;
            String taskTitle = null;
            if ("TASK_PRIVATE".equals(c.getConvType())) {
                List<Long> memberIds = memberRepo.findUserIdsByConvId(c.getId());
                Long otherId = memberIds.stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
                if (otherId != null) {
                    User other = userCache.computeIfAbsent(otherId, id -> userRepo.findById(id).orElse(null));
                    otherUserName = other != null ? (other.getNickname() != null ? other.getNickname() : other.getName()) : null;
                }
                if (c.getTaskId() != null) {
                    taskTitle = "需求 #" + c.getTaskId();
                }
            }

            // 任务状态 + 权限判断（群聊和私聊）
            String taskStatus = null;
            boolean canDismiss = false;
            boolean isOwner = false;
            int keepGroup = c.getKeepGroup() != null ? c.getKeepGroup() : -1;
            if (("TEAM_GROUP".equals(c.getConvType()) || "TASK_PRIVATE".equals(c.getConvType()))
                    && c.getTaskId() != null) {
                Task task = taskRepo.findById(c.getTaskId()).orElse(null);
                if (task != null) {
                    taskStatus = task.getStatus().name();
                    boolean isExpired = "completed".equals(taskStatus)
                            || "cancelled".equals(taskStatus)
                            || (task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now()));
                    boolean notConfirmed = c.getKeepGroup() == null || c.getKeepGroup() == 0;

                    if ("TASK_PRIVATE".equals(c.getConvType())) {
                        // 私聊：当前查询列表的用户只要进来了就说明是参与人，双方均可见并选择
                        isOwner = true;
                    } else if ("TEAM_GROUP".equals(c.getConvType())) {
                        // 群聊：有群主匹配群主，没有群主匹配该需求任务的发布方（需求方）
                        if (c.getOwnerId() != null) {
                            isOwner = c.getOwnerId().equals(userId);
                        } else {
                            Long requesterId = (task.getRequester() != null) ? task.getRequester().getId() : null;
                            isOwner = requesterId != null && requesterId.equals(userId);
                        }
                    }
                    canDismiss = isExpired && notConfirmed && isOwner;
                }
            }

            result.add(ConversationDTO.builder()
                    .id(c.getId()).convType(c.getConvType())
                    .taskId(c.getTaskId()).orderId(c.getOrderId())
                    .groupName(c.getGroupName()).ownerId(c.getOwnerId())
                    .maxMembers(c.getMaxMembers()).memberCount(memberCount)
                    .lastMessage(lastMsgDTO).unreadCount(unread)
                    .otherUserName(otherUserName).taskTitle(taskTitle)
                    .taskStatus(taskStatus).keepGroup(keepGroup).canDismiss(canDismiss)
                    .isOwner(isOwner)
                    .build());
        }

        // 系统通知置顶
        result.sort((a, b) -> {
            if ("SYSTEM_NOTIFY".equals(a.getConvType())) return -1;
            if ("SYSTEM_NOTIFY".equals(b.getConvType())) return 1;
            return 0;
        });
        return result;
    }

    @Transactional
    public Conversation createPrivate(Long taskId, Long orderId, Long user1Id, Long user2Id) {
        Conversation conv = new Conversation();
        conv.setConvType("TASK_PRIVATE");
        conv.setTaskId(taskId);
        conv.setOrderId(orderId);
        conv = convRepo.save(conv);

        ConversationMember m1 = new ConversationMember();
        m1.setConvId(conv.getId()); m1.setUserId(user1Id); m1.setRole("MEMBER");
        memberRepo.save(m1);

        ConversationMember m2 = new ConversationMember();
        m2.setConvId(conv.getId()); m2.setUserId(user2Id); m2.setRole("MEMBER");
        memberRepo.save(m2);

        return conv;
    }

    @Transactional
    public Conversation createTeamGroup(Long taskId, String groupName, Long ownerId, int maxMembers) {
        Conversation conv = new Conversation();
        conv.setConvType("TEAM_GROUP");
        conv.setTaskId(taskId);
        conv.setGroupName(groupName);
        conv.setOwnerId(ownerId);
        conv.setMaxMembers(maxMembers);
        conv = convRepo.save(conv);

        ConversationMember m = new ConversationMember();
        m.setConvId(conv.getId()); m.setUserId(ownerId); m.setRole("OWNER");
        memberRepo.save(m);

        return conv;
    }

    @Transactional
    public void addMember(Long convId, Long userId, String role) {
        if (memberRepo.existsByConvIdAndUserId(convId, userId)) return;
        ConversationMember m = new ConversationMember();
        m.setConvId(convId); m.setUserId(userId); m.setRole(role);
        memberRepo.save(m);
    }

    @Transactional
    public void removeMember(Long convId, Long userId) {
        memberRepo.deleteByConvIdAndUserId(convId, userId);
    }

    public Conversation getById(Long convId) {
        return convRepo.findById(convId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
    }

    public Conversation findByTaskAndType(Long taskId, String convType) {
        return convRepo.findByTaskIdAndConvType(taskId, convType).orElse(null);
    }

    public List<Long> getMemberIds(Long convId) {
        return memberRepo.findUserIdsByConvId(convId);
    }

    public long getMemberCount(Long convId) {
        return memberRepo.countByConvId(convId);
    }

    @Transactional
    public void markAsRead(Long convId, Long userId) {
        ConversationMember member = memberRepo.findByConvIdAndUserId(convId, userId);
        if (member == null) {
            throw new BusinessException(404, "你不在该会话中");
        }
        member.setReadAt(LocalDateTime.now());
        memberRepo.save(member);
    }

    @Transactional
    public void keepGroup(Long convId) {
        Conversation conv = getById(convId);
        conv.setKeepGroup(1);
        convRepo.save(conv);
    }

    @Transactional
    public void dismissGroup(Long convId) {
        Conversation conv = getById(convId);
        conv.setIsDeleted(true); // 软删除标识
        convRepo.save(conv);
    }

    @Transactional
    public Conversation getOrCreateSystemNotify(Long userId) {
        List<Conversation> userConvs = convRepo.findByUserId(userId);
        Conversation conv = userConvs.stream()
                .filter(c -> "SYSTEM_NOTIFY".equals(c.getConvType()))
                .findFirst().orElse(null);
        if (conv == null) {
            conv = new Conversation();
            conv.setConvType("SYSTEM_NOTIFY");
            conv.setGroupName("系统通知");
            conv = convRepo.save(conv);
            ConversationMember m = new ConversationMember();
            m.setConvId(conv.getId()); m.setUserId(userId); m.setRole("MEMBER");
            memberRepo.save(m);
        }
        return conv;
    }

    /**
     * 判断当前用户是否有权限选择保留或删除该会话
     */
    @Transactional
    public boolean hasOperatePermission(Conversation conv, Long userId) {
        if ("TASK_PRIVATE".equals(conv.getConvType())) {
            // 私聊：需求方和服务方双方都能选择。只要当前用户是此私聊的有效成员即可操作。
            return memberRepo.existsByConvIdAndUserId(conv.getId(), userId);
        } else if ("TEAM_GROUP".equals(conv.getConvType())) {
            // 群聊：由群主选择。如果没有群主就默认是需求方选择。
            if (conv.getOwnerId() != null) {
                return conv.getOwnerId().equals(userId);
            }
            if (conv.getTaskId() != null) {
                Task task = taskRepo.findById(conv.getTaskId()).orElse(null);
                if (task != null && task.getRequester() != null) {
                    return task.getRequester().getId().equals(userId);
                }
            }
        }
        return false;
    }
}
