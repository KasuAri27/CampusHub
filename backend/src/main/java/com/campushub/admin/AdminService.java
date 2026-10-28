package com.campushub.admin;

import com.campushub.category.Category;
import com.campushub.category.CategoryRepository;
import com.campushub.chat.dto.MessageDTO;
import com.campushub.chat.entity.Conversation;
import com.campushub.chat.entity.ConversationMember;
import com.campushub.chat.entity.Message;
import com.campushub.chat.repository.ConversationMemberRepository;
import com.campushub.chat.repository.ConversationRepository;
import com.campushub.chat.repository.MessageRepository;
import com.campushub.chat.service.ChatIntegrationService;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import com.campushub.notification.Notification;
import com.campushub.notification.NotificationRepository;
import com.campushub.order.OrderRepository;
import com.campushub.review.ReviewRepository;
import com.campushub.task.Task;
import com.campushub.task.Task.TaskStatus;
import com.campushub.task.TaskRepository;
import com.campushub.task.TeamingTask;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import com.campushub.order.Order;
import com.campushub.review.Review;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ChatIntegrationService chatIntegrationService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;

    private User requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (user.getRole() != User.Role.admin) {
            throw new BusinessException(403, "无管理员权限");
        }
        return user;
    }

    // ==================== 仪表盘 ====================

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(Long adminUserId) {
        requireAdmin(adminUserId);

        long totalUsers = userRepository.count();
        long totalTasks = taskRepository.count();
        long totalNotifications = notificationRepository.count();
        long totalCategories = categoryRepository.count();

        long bannedUsers = userRepository.findAll().stream()
                .filter(u -> u.getStatus() != null && u.getStatus() != 1).count();

        List<Task> allTasks = taskRepository.findAll();
        long publishedTasks = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.published).count();
        long inProgressTasks = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.in_progress).count();
        long completedTasks = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.completed).count();
        long cancelledTasks = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.cancelled).count();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayNewTasks = allTasks.stream()
                .filter(t -> t.getPublishedAt() != null && t.getPublishedAt().isAfter(todayStart)).count();

        long totalConversations = conversationRepository.count();
        long totalMessages = messageRepository.count();
        long todaysMessages = messageRepository.findAll().stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(todayStart)).count();

        return DashboardStatsDTO.builder()
                .totalUsers(totalUsers).bannedUsers(bannedUsers)
                .totalTasks(totalTasks).publishedTasks(publishedTasks)
                .inProgressTasks(inProgressTasks).completedTasks(completedTasks)
                .totalOrders(cancelledTasks).todayNewTasks(todayNewTasks)
                .totalConversations(totalConversations)
                .totalMessages(totalMessages)
                .todaysMessages(todaysMessages)
                .build();
    }

    // ==================== 用户管理 ====================

    @Transactional(readOnly = true)
    public PageResult<UserManagementDTO> listUsers(Long adminUserId, int page, int size,
                                                    String keyword, String role) {
        requireAdmin(adminUserId);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), kw),
                        cb.like(cb.lower(root.get("email")), kw),
                        cb.like(cb.lower(root.get("studentId")), kw)
                ));
            }
            if (role != null && !role.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("role"), User.Role.valueOf(role)));
                } catch (IllegalArgumentException ignored) {}
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> userPage = userRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));

        List<UserManagementDTO> items = userPage.getContent().stream()
                .map(this::toUserDTO).collect(Collectors.toList());

        return new PageResult<>(items, userPage.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public UserManagementDTO getUserDetail(Long adminUserId, Long targetId) {
        requireAdmin(adminUserId);
        User u = userRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        return toUserDTO(u);
    }

    @Transactional
    public void toggleUserStatus(Long adminUserId, Long targetUserId) {
        requireAdmin(adminUserId);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "目标用户不存在"));
        target.setStatus(target.getStatus() != null && target.getStatus() == 1 ? 0 : 1);
        userRepository.save(target);
    }

    @Transactional
    public void adjustCredit(Long adminUserId, Long targetUserId, int delta) {
        requireAdmin(adminUserId);
        User admin = userRepository.findById(adminUserId).get();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "目标用户不存在"));
        int current = target.getCreditScore() != null ? target.getCreditScore() : 0;
        if (delta > 0 && current >= 100) {
            throw new BusinessException(400, "信用分已达上限（100分），无法继续增加");
        }
        int newScore = Math.min(100, Math.max(0, current + delta));
        target.setCreditScore(newScore);
        userRepository.save(target);

        String adminName = admin.getName();
        String action = delta >= 0 ? "增加了 " + Math.abs(delta) + " 分" : "扣除了 " + Math.abs(delta) + " 分";
        String title = "信用分变动通知";
        String content = "管理员 " + adminName + " " + action + "，当前信用分：" + newScore;
        Notification n = new Notification();
        n.setReceiverId(targetUserId);
        n.setTitle(title);
        n.setContent(content);
        n.setType("CREDIT_CHANGE");
        notificationRepository.save(n);

        if (newScore < 70) {
            Notification warn = new Notification();
            warn.setReceiverId(targetUserId);
            warn.setTitle("信用分不足提醒");
            warn.setContent("您的信用分已降至 " + newScore + "（低于70），发布权限已被限制。如需申诉，请联系管理员 " + adminName + "。");
            warn.setType("CREDIT_WARNING");
            notificationRepository.save(warn);
        }
    }

    // ==================== 需求管理 ====================

    @Transactional(readOnly = true)
    public PageResult<TaskManagementDTO> listAllTasks(Long adminUserId, int page, int size,
                                                       String status, String keyword) {
        requireAdmin(adminUserId);

        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"), Task.TaskStatus.valueOf(status)));
                } catch (IllegalArgumentException ignored) {}
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Task> taskPage = taskRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "publishedAt")));

        List<TaskManagementDTO> items = taskPage.getContent().stream()
                .map(this::toTaskDTO).collect(Collectors.toList());

        return new PageResult<>(items, taskPage.getTotalElements(), page, size);
    }

    @Transactional
    public void deleteTask(Long adminUserId, Long taskId) {
        requireAdmin(adminUserId);
        if (!taskRepository.existsById(taskId)) {
            throw new BusinessException(404, "需求不存在");
        }
        // 管理员删除需求时级联删除评价 → 订单 → 需求
        orderRepository.findByTaskId(taskId).forEach(o -> {
            reviewRepository.deleteByOrder_Id(o.getId());
            orderRepository.delete(o);
        });
        taskRepository.deleteById(taskId);
    }

    @Transactional
    public TaskManagementDTO approveTask(Long adminUserId, Long taskId) {
        requireAdmin(adminUserId);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (task.getStatus() != TaskStatus.pending_review) {
            throw new BusinessException(400, "仅待审核状态的需求可以审核通过");
        }
        task.setStatus(TaskStatus.published);
        task.setPublishedAt(LocalDateTime.now());
        taskRepository.save(task);

        if (task instanceof TeamingTask) {
            chatIntegrationService.createTeamGroup(task);
        }

        notifyRequester(task, "approved");

        return toTaskDTO(task);
    }

    @Transactional
    public TaskManagementDTO rejectTask(Long adminUserId, Long taskId, String reason) {
        requireAdmin(adminUserId);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (task.getStatus() != TaskStatus.pending_review) {
            throw new BusinessException(400, "仅待审核状态的需求可以驳回");
        }
        task.setStatus(TaskStatus.cancelled);
        taskRepository.save(task);

        notifyRequester(task, "rejected", reason);

        return toTaskDTO(task);
    }

    private void notifyRequester(Task task, String action, String reason) {
        String title = "需求审核结果";
        String verb = "approved".equals(action) ? "已通过审核" : "已被驳回";
        String content = "你的需求「" + task.getTitle() + "」" + verb + "。";
        if (reason != null && !reason.isBlank()) {
            content += "\n驳回理由：" + reason;
        }
        Notification n = new Notification();
        n.setReceiverId(task.getRequester().getId());
        n.setTitle(title);
        n.setContent(content);
        n.setType("TASK_REVIEW_RESULT");
        notificationRepository.save(n);
    }

    private void notifyRequester(Task task, String action) {
        notifyRequester(task, action, null);
    }

    // ==================== 分类管理 ====================

    @Transactional(readOnly = true)
    public List<CategoryManagementDTO> listCategories(Long adminUserId) {
        requireAdmin(adminUserId);
        return categoryRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(c -> CategoryManagementDTO.builder()
                        .id(c.getId()).name(c.getName()).icon(c.getIcon())
                        .hasReview(c.getHasReview()).sortOrder(c.getSortOrder()).build())
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryManagementDTO createCategory(Long adminUserId, CategoryUpsertRequest req) {
        requireAdmin(adminUserId);
        Category c = Category.builder()
                .name(req.getName()).icon(req.getIcon())
                .hasReview(req.getHasReview() != null && req.getHasReview())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
        c = categoryRepository.save(c);
        return CategoryManagementDTO.builder()
                .id(c.getId()).name(c.getName()).icon(c.getIcon())
                .hasReview(c.getHasReview()).sortOrder(c.getSortOrder()).build();
    }

    @Transactional
    public CategoryManagementDTO updateCategory(Long adminUserId, Integer categoryId,
                                                 CategoryUpsertRequest req) {
        requireAdmin(adminUserId);
        Category c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (req.getName() != null) c.setName(req.getName());
        if (req.getIcon() != null) c.setIcon(req.getIcon());
        if (req.getHasReview() != null) c.setHasReview(req.getHasReview());
        if (req.getSortOrder() != null) c.setSortOrder(req.getSortOrder());
        c = categoryRepository.save(c);
        return CategoryManagementDTO.builder()
                .id(c.getId()).name(c.getName()).icon(c.getIcon())
                .hasReview(c.getHasReview()).sortOrder(c.getSortOrder()).build();
    }

    @Transactional
    public void deleteCategory(Long adminUserId, Integer categoryId) {
        requireAdmin(adminUserId);
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(404, "分类不存在");
        }
        categoryRepository.deleteById(categoryId);
    }

    // ==================== 系统通知 ====================

    @Transactional(readOnly = true)
    public PageResult<NotificationDTO> listNotifications(Long adminUserId, int page, int size) {
        requireAdmin(adminUserId);
        Page<Notification> nPage = notificationRepository.findAll(
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<NotificationDTO> items = nPage.getContent().stream()
                .map(n -> NotificationDTO.builder()
                        .id(n.getId()).receiverId(n.getReceiverId())
                        .title(n.getTitle()).content(n.getContent())
                        .type(n.getType()).isRead(n.getIsRead())
                        .createTime(n.getCreateTime()).readTime(n.getReadTime()).build())
                .collect(Collectors.toList());
        return new PageResult<>(items, nPage.getTotalElements(), page, size);
    }

    @Transactional
    public void sendNotification(Long adminUserId, SystemNotificationRequest req) {
        requireAdmin(adminUserId);
        if (req.getReceiverId() != null) {
            Notification n = new Notification();
            n.setReceiverId(req.getReceiverId());
            n.setTitle(req.getTitle());
            n.setContent(req.getContent());
            n.setType(req.getType() != null ? req.getType() : "SYSTEM");
            notificationRepository.save(n);
        } else {
            List<User> allUsers = userRepository.findAll();
            List<Notification> batch = allUsers.stream().map(u -> {
                Notification n = new Notification();
                n.setReceiverId(u.getId());
                n.setTitle(req.getTitle());
                n.setContent(req.getContent());
                n.setType(req.getType() != null ? req.getType() : "SYSTEM");
                return n;
            }).collect(Collectors.toList());
            notificationRepository.saveAll(batch);
        }
    }

    // ==================== 聊天管理 ====================

    @Transactional(readOnly = true)
    public PageResult<ConversationManagementDTO> listAllConversations(Long adminUserId,
                                                                      int page, int size,
                                                                      String convType) {
        requireAdmin(adminUserId);

        List<Conversation> all;
        if (convType != null && !convType.isBlank()) {
            all = conversationRepository.findByConvType(convType);
        } else {
            List<Conversation> groups = conversationRepository.findByConvType("TEAM_GROUP");
            List<Conversation> privates = conversationRepository.findByConvType("TASK_PRIVATE");
            all = new ArrayList<>();
            all.addAll(groups);
            all.addAll(privates);
        }

        all.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));

        int total = all.size();
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<Conversation> pageContent = from < total ? all.subList(from, to) : List.of();

        Map<Long, User> userCache = new HashMap<>();
        List<ConversationManagementDTO> items = pageContent.stream()
                .map(c -> toConvManagementDTO(c, userCache))
                .collect(Collectors.toList());

        return new PageResult<>(items, (long) total, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<MessageDTO> getConversationMessages(Long adminUserId, Long convId,
                                                           int page, int size) {
        requireAdmin(adminUserId);
        if (!conversationRepository.existsById(convId)) {
            throw new BusinessException(404, "会话不存在");
        }

        Page<Message> msgPage = messageRepository.findByConvIdOrderByCreatedAtDesc(
                convId, PageRequest.of(page - 1, size));

        Map<Long, String> nameCache = new HashMap<>();
        List<MessageDTO> items = msgPage.getContent().stream()
                .map(m -> toMessageDetailDTO(m, nameCache))
                .collect(Collectors.toList());

        return new PageResult<>(items, msgPage.getTotalElements(), page, size);
    }

    private ConversationManagementDTO toConvManagementDTO(Conversation c, Map<Long, User> userCache) {
        int memberCount = (int) conversationMemberRepository.countByConvId(c.getId());

        List<ConversationMember> members = conversationMemberRepository.findByConvId(c.getId());
        List<String> memberNames = members.stream().map(m -> {
            User u = userCache.computeIfAbsent(m.getUserId(),
                    id -> userRepository.findById(id).orElse(null));
            return u != null ? (u.getNickname() != null ? u.getNickname() : u.getName()) : "用户#" + m.getUserId();
        }).collect(Collectors.toList());

        Message lastMsg = messageRepository.findLastMessage(c.getId());
        String lastContent = null;
        String lastSenderName = null;
        LocalDateTime lastTime = null;
        if (lastMsg != null) {
            lastContent = lastMsg.getContent();
            if (lastContent.length() > 50) {
                lastContent = lastContent.substring(0, 50) + "...";
            }
            if (lastMsg.getSenderId() == 0L) {
                lastSenderName = "系统通知";
            } else {
                User sender = userCache.computeIfAbsent(lastMsg.getSenderId(),
                        id -> userRepository.findById(id).orElse(null));
                lastSenderName = sender != null
                        ? (sender.getNickname() != null ? sender.getNickname() : sender.getName())
                        : "未知";
            }
            lastTime = lastMsg.getCreatedAt();
        }

        return ConversationManagementDTO.builder()
                .id(c.getId()).convType(c.getConvType())
                .taskId(c.getTaskId()).orderId(c.getOrderId())
                .groupName(c.getGroupName()).memberCount(memberCount)
                .memberNames(memberNames)
                .lastMessageContent(lastContent)
                .lastMessageSenderName(lastSenderName)
                .lastMessageTime(lastTime)
                .lastMessageIsRecalled(lastMsg != null && Boolean.TRUE.equals(lastMsg.getIsRecalled()))
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .isDeleted(c.getIsDeleted())
                .build();
    }

    private MessageDTO toMessageDetailDTO(Message m, Map<Long, String> nameCache) {
        String senderName;
        if (m.getSenderId() == 0L) {
            senderName = "系统通知";
        } else {
            senderName = nameCache.computeIfAbsent(m.getSenderId(), id -> {
                User u = userRepository.findById(id).orElse(null);
                return u != null ? (u.getNickname() != null ? u.getNickname() : u.getName()) : "未知";
            });
        }
        return MessageDTO.builder()
                .id(m.getId()).convId(m.getConvId())
                .senderId(m.getSenderId())
                .senderName(senderName)
                .msgType(m.getMsgType())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .isRecalled(m.getIsRecalled())
                .build();
    }

    // ==================== DTO 转换 ====================

    private UserManagementDTO toUserDTO(User u) {
        String phoneMasked = "";
        if (u.getPhone() != null && u.getPhone().length() >= 7) {
            phoneMasked = u.getPhone().substring(0, 3) + "****"
                    + u.getPhone().substring(u.getPhone().length() - 4);
        }
        return UserManagementDTO.builder()
                .id(u.getId()).name(u.getName()).nickname(u.getNickname())
                .email(u.getEmail()).phoneMasked(phoneMasked)
                .role(u.getRole() != null ? u.getRole().name() : "")
                .creditScore(u.getCreditScore()).status(u.getStatus())
                .createTime(u.getCreateTime()).build();
    }

    private TaskManagementDTO toTaskDTO(Task t) {
        String providerSid = null;
        String comment = null;

        // 查询任务关联的订单和评价
        List<Order> orders = orderRepository.findByTaskId(t.getId());
        if (!orders.isEmpty()) {
            Order order = orders.get(0);
            if (order.getProvider() != null) {
                providerSid = order.getProvider().getStudentId();
            }

            // 查询关联订单的评价
            java.util.Optional<Review> reviewOpt = reviewRepository.findByOrder_Id(order.getId());
            if (reviewOpt.isPresent()) {
                comment = reviewOpt.get().getComment();
            }
        }

        return TaskManagementDTO.builder()
                .id(t.getId()).title(t.getTitle()).description(t.getDescription())
                .categoryName(t.getCategory() != null ? t.getCategory().getName() : "")
                .reward(t.getReward())
                .status(t.getStatus() != null ? t.getStatus().name() : "")
                .campus(t.getCampus())
                .location(t.getLocation()).deadline(t.getDeadline())
                .publishedAt(t.getPublishedAt())
                .requesterId(t.getRequester() != null ? t.getRequester().getId() : null)
                .requesterName(t.getRequester() != null ? t.getRequester().getName() : "")
                .requesterEmail(t.getRequester() != null ? t.getRequester().getEmail() : "")
                .providerStudentId(providerSid) // 新增：接单者学号
                .requesterComment(comment)      // 新增：发布者评价
                .build();
    }
}
