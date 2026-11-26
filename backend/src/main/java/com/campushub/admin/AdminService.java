package com.campushub.admin;

import com.campushub.category.Category;
import com.campushub.category.CategoryRepository;
import com.campushub.category.CategoryService;
import com.campushub.chat.dto.MessageDTO;
import com.campushub.chat.entity.Conversation;
import com.campushub.chat.entity.ConversationMember;
import com.campushub.chat.entity.Message;
import com.campushub.chat.repository.ConversationMemberRepository;
import com.campushub.chat.repository.ConversationRepository;
import com.campushub.chat.repository.MessageRepository;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import com.campushub.notification.Notification;
import com.campushub.notification.NotificationRepository;
import com.campushub.order.OrderRepository;
import com.campushub.review.ReviewRepository;
import com.campushub.task.Task;
import com.campushub.task.TaskService;
import com.campushub.task.TaskRepository;
import com.campushub.task.TeamingTask;
import com.campushub.user.CreditService;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import com.campushub.user.UserService;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final CreditService creditService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final com.campushub.notification.NotificationService notificationService;

    /** 校验当前操作人是否具备已激活的管理员权限；非管理员或已冻结账号直接拒绝访问。 */
    private User requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (user.getRole() != User.Role.admin || user.getStatus() != 1) {
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

    /**
     * 切换用户封禁状态。后台只做管理员鉴权，状态写入与悲观写锁由
     * {@link UserService#toggleStatus} 负责，AdminService 不再直写 User.status。
     */
    @Transactional
    public void toggleUserStatus(Long adminUserId, Long targetUserId) {
        requireAdmin(adminUserId);
        userService.toggleStatus(targetUserId);
    }

    /**
     * 管理员调整信用分。分数写入、行锁、[0,100] 钳制与 CreditEvent 审计流水统一由
     * {@link CreditService#adjust} 负责，后台不再直接 setCreditScore。
     * 在 100 分时继续上调不再报 400，而是钳制后记一条流水（2026-11 用户确认的规则）。
     * 幂等键：eventKey = {@code admin:{targetUserId}:{operationId}}（模块契约 §3.2）。
     * 客户端带 operationId 时，同一编号重复提交只扣一次且不重复通知；同编号但参数不一致由
     * CreditService 抛 409。未带时服务端生成 UUID，每次调用都是独立流水（无幂等保护，与旧行为一致）。
     * 降至70分以下时触发权限限制警告，通知用户发单受限。
     * 事务内先锁用户（CreditService 内 findLockedById）再写流水，与其他信用分写入口的锁顺序一致。
     *
     * @param operationId 可选的客户端操作编号，仅允许字母、数字、下划线、连字符，长度 ≤36
     */
    @Transactional
    public void adjustCredit(Long adminUserId, Long targetUserId, int delta, String operationId) {
        User admin = requireAdmin(adminUserId);
        String opId = (operationId == null || operationId.isBlank()) ? UUID.randomUUID().toString() : operationId;
        // 校验放在写入之前；eventKey 列为 VARCHAR(100)，编号上限 36 保证拼接后不超长
        if (!opId.matches("[A-Za-z0-9_-]{1,36}")) {
            throw new BusinessException(400, "operationId 只能包含字母、数字、下划线和连字符，且不超过36个字符");
        }
        // 保留原有的"目标用户不存在"文案；仅作存在性校验，写入仍由 CreditService 加锁完成
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "目标用户不存在"));
        String adminName = admin.getName();
        String eventKey = "admin:" + targetUserId + ":" + opId;
        boolean applied = creditService.adjust(targetUserId, delta, eventKey,
                "管理员 " + adminName + " 调整信用分 " + (delta >= 0 ? "+" : "") + delta);
        // 重复提交：分数与通知在首次调用时已处理，直接按成功返回
        if (!applied) {
            return;
        }
        // 同一事务内读回已更新实体，取调整后的分数用于通知文案
        int newScore = userRepository.findById(targetUserId).map(User::getCreditScore).orElse(0);

        String action = delta >= 0 ? "增加了 " + Math.abs(delta) + " 分" : "扣除了 " + Math.abs(delta) + " 分";
        String title = "信用分变动通知";
        String content = "管理员 " + adminName + " " + action + "，当前信用分：" + newScore;
        Notification n = new Notification();
        n.setReceiverId(targetUserId);
        n.setTitle(title);
        n.setContent(content);
        n.setType("CREDIT_CHANGE");
        notificationService.createNotification(n.getReceiverId(), n.getTitle(), n.getContent(), n.getType());

        if (newScore < 70) {
            Notification warn = new Notification();
            warn.setReceiverId(targetUserId);
            warn.setTitle("信用分不足提醒");
            warn.setContent("您的信用分已降至 " + newScore + "（低于70），发布权限已被限制。如需申诉，请联系管理员 " + adminName + "。");
            warn.setType("CREDIT_WARNING");
            notificationService.createNotification(warn.getReceiverId(), warn.getTitle(), warn.getContent(), warn.getType());
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

    /**
     * 管理员物理删除需求。后台只负责确认操作人是管理员，需求、订单、评价的删除规则与加锁顺序
     * 由 {@link TaskService#deleteByAdmin} 统一裁决，AdminService 不再直接写这三张表。
     */
    @Transactional
    public void deleteTask(Long adminUserId, Long taskId) {
        requireAdmin(adminUserId);
        taskService.deleteByAdmin(taskId);
    }

    /** 审核通过需求；状态迁移、组队群创建与通知均在 TaskService 同一事务内完成，这里只做鉴权与展示组装。 */
    @Transactional
    public TaskManagementDTO approveTask(Long adminUserId, Long taskId) {
        requireAdmin(adminUserId);
        return toTaskDTO(taskService.approveByAdmin(taskId));
    }

    /** 驳回待审需求；终态迁移与驳回通知由 TaskService 负责。 */
    @Transactional
    public TaskManagementDTO rejectTask(Long adminUserId, Long taskId, String reason) {
        requireAdmin(adminUserId);
        return toTaskDTO(taskService.rejectByAdmin(taskId, reason));
    }

    // ==================== 分类管理 ====================

    @Transactional(readOnly = true)
    public List<CategoryManagementDTO> listCategories(Long adminUserId) {
        requireAdmin(adminUserId);
        return categoryRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(this::toCategoryDTO)
                .collect(Collectors.toList());
    }

    /** 新增分类：管理员鉴权后交由交易核心的 {@link CategoryService} 写入，后台不再直写 categories 表。 */
    @Transactional
    public CategoryManagementDTO createCategory(Long adminUserId, CategoryUpsertRequest req) {
        requireAdmin(adminUserId);
        return toCategoryDTO(categoryService.create(
                req.getName(), req.getIcon(), req.getHasReview(), req.getSortOrder()));
    }

    /** 局部更新分类，null 字段保持原值，规则同 {@link CategoryService#update}。 */
    @Transactional
    public CategoryManagementDTO updateCategory(Long adminUserId, Integer categoryId,
                                                 CategoryUpsertRequest req) {
        requireAdmin(adminUserId);
        return toCategoryDTO(categoryService.update(
                categoryId, req.getName(), req.getIcon(), req.getHasReview(), req.getSortOrder()));
    }

    /** 删除分类；分类仍被需求引用时由 CategoryService 返回 409 并说明原因，这里直接透传。 */
    @Transactional
    public void deleteCategory(Long adminUserId, Integer categoryId) {
        requireAdmin(adminUserId);
        categoryService.delete(categoryId);
    }

    private CategoryManagementDTO toCategoryDTO(Category c) {
        return CategoryManagementDTO.builder()
                .id(c.getId()).name(c.getName()).icon(c.getIcon())
                .hasReview(c.getHasReview()).sortOrder(c.getSortOrder()).build();
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

    /** 系统通知分发：根据 receiverId 判定是精准下发给特定用户还是批量下发全员。 */
    @Transactional
    public void sendNotification(Long adminUserId, SystemNotificationRequest req) {
        requireAdmin(adminUserId);
        if (req.getReceiverId() != null) {
            Notification n = new Notification();
            n.setReceiverId(req.getReceiverId());
            n.setTitle(req.getTitle());
            n.setContent(req.getContent());
            n.setType(req.getType() != null ? req.getType() : "SYSTEM");
            notificationService.createNotification(n.getReceiverId(), n.getTitle(), n.getContent(), n.getType());
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
            batch.forEach(n -> notificationService.createNotification(n.getReceiverId(), n.getTitle(), n.getContent(), n.getType()));
        }
    }

    // ==================== 聊天管理 ====================

    @Transactional(readOnly = true)
    public PageResult<ConversationManagementDTO> listAllConversations(Long adminUserId,
                                                                      int page, int size,
                                                                      String convType) {
        requireAdmin(adminUserId);

        List<Conversation> all;
        // 未限定类型时，合并组队群聊与任务私聊两大类进行联合监管排序
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

    /** 将任务实体转换为管理端视图，根据多态类型推断 taskType，并关联查出首笔订单的履约人与评价内容。 */
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
                .taskType(t instanceof TeamingTask ? "TEAM" : t instanceof com.campushub.task.SecondHandTask ? "FLEA" : "SERVICE")
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
                // 最近一次驳回理由，仅 rejected 状态有值（模块契约 §7 / TODO §1.1）
                .rejectReason(t.getRejectReason())
                .build();
    }
}
