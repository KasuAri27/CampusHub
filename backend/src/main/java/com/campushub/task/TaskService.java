package com.campushub.task;

import com.campushub.category.Category;
import com.campushub.category.CategoryRepository;
import com.campushub.chat.service.ChatIntegrationService;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import com.campushub.common.PageResult.Pagination;
import com.campushub.complaint.ComplaintRepository;
import com.campushub.order.Order;
import com.campushub.order.OrderRepository;
import com.campushub.payment.PaymentAttemptRepository;
import com.campushub.review.ReviewRepository;
import com.campushub.task.Task.TaskStatus;
import com.campushub.user.User;
import com.campushub.user.User.Role;
import com.campushub.user.UserRepository;
import com.campushub.util.UserContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 互助需求领域业务服务。
 * 负责多形态任务创建（辅导按时长计价/跑腿/二手买卖/组队）、多维度列表过滤（未过期置顶）、可见性控制与安全删除。
 */
public class TaskService {

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final com.campushub.notification.NotificationService notificationService;
    private final ChatIntegrationService chatIntegrationService;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ComplaintRepository complaintRepository;
    private final PaymentAttemptRepository paymentAttempts;
    /** 业务时钟（Asia/Shanghai）；发布时间不得再取 JVM 默认时区。 */
    private final Clock clock;

    /**
     * 创建需求业务流程：
     * 校验发布资格（普通用户、账号正常且信用分不低于70分；管理员只做审核，不发布需求）；
     * 根据 taskType 实例化对应的多态实体，计算价格或初始化群人数；
     * 状态置为 pending_review，触发全量管理员审核站内通知。
     */
    @Transactional
    public TaskDTO createTask(TaskCreateRequest request, Long userId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (requester.getStatus() != 1) throw new BusinessException(403, "账号已被禁用");

        if (requester.getCreditScore() < 70) {
            throw new BusinessException(403, "您的信用分低于70分，暂无发布需求权限！");
        }

        // 角色合并后普通用户都能发布；管理员负责审核需求，不能自己发布再自己审核。
        if (requester.getRole() == Role.admin) {
            throw new BusinessException(403, "管理员不能发布需求");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));

        Task task = newTaskOfType(request.getTaskType());
        applyRequest(task, request);
        task.setRequester(requester);
        task.setCategory(category);
        task.setStatus(TaskStatus.pending_review);
        task = taskRepository.save(task);

        notifyAdminsForReview(task, false);

        return toDTO(task, userId, false, false);
    }

    /** 按发布类型实例化对应的多态子类；类型与分类的对应关系由前端和契约约定，这里只认类型。 */
    private static Task newTaskOfType(String taskType) {
        return switch (taskType) {
            case "RUNNING" -> new ExpressTask();
            case "TUTORING" -> new TutoringTask();
            case "FLEA" -> new SecondHandTask();
            case "TEAM" -> new TeamingTask();
            case "CONSULTING" -> new ConsultingTask();
            default -> throw new BusinessException(400, "不支持的任务类型: " + taskType);
        };
    }

    /** 需求实体对应的发布类型（与 {@link #newTaskOfType} 互逆）；先还原代理再判断。 */
    private static String taskTypeOf(Task loaded) {
        Task task = (Task) Hibernate.unproxy(loaded);
        if (task instanceof ExpressTask) return "RUNNING";
        if (task instanceof TutoringTask) return "TUTORING";
        if (task instanceof SecondHandTask) return "FLEA";
        if (task instanceof TeamingTask) return "TEAM";
        if (task instanceof ConsultingTask) return "CONSULTING";
        return null;
    }

    /**
     * 把发布请求写入需求实体：公共字段加类型专属字段，并做类型专属的金额与必填校验。
     * 发布与“修改后重新提交”共用同一份规则，task 必须已是 request.taskType 对应的子类实例。
     * 修改是整体替换：请求里没填的可选字段会被清空，而不是保留旧值。
     */
    private void applyRequest(Task task, TaskCreateRequest request) {
        switch (request.getTaskType()) {
            case "RUNNING" -> {
                ExpressTask et = (ExpressTask) task;
                et.setPickupLocation(requireNonBlank(request.getPickupLocation(), "取货地点"));
                et.setDeliveryLocation(requireNonBlank(request.getDeliveryLocation(), "送达地点"));
                et.setPickupCode(request.getPickupCode());
                et.setExpressReward(requireNonNegative(request.getExpressReward(), "跑腿报酬"));
                et.setReward(request.getExpressReward());
                et.setLocation(request.getPickupLocation() + " → " + request.getDeliveryLocation());
            }
            case "TUTORING" -> {
                TutoringTask tt = (TutoringTask) task;
                tt.setSubject(requireNonBlank(request.getSubject(), "辅导科目"));
                tt.setPricePerHour(requireNonNegative(request.getPricePerHour(), "时薪"));
                BigDecimal hours = request.getDurationHours();
                if (hours == null || hours.signum() <= 0 || hours.compareTo(new BigDecimal("1000")) > 0 || hours.scale() > 2)
                    throw new BusinessException(400, "请填写0至1000小时之间、最多两位小数的约定辅导时长");
                tt.setDurationHours(hours);
                tt.setTutoringLocation(requireNonBlank(request.getTutoringLocation(), "辅导地点"));
                // 按人民币分四舍五入；不能接受前端另报一个更低的总金额。
                BigDecimal total = request.getPricePerHour().multiply(hours).setScale(2, java.math.RoundingMode.HALF_UP);
                if (total.compareTo(new BigDecimal("99999999.99")) > 0) throw new BusinessException(400, "辅导总价超出范围");
                tt.setReward(total);
                tt.setLocation(request.getTutoringLocation());
            }
            case "FLEA" -> {
                SecondHandTask st = (SecondHandTask) task;
                String tradeMode = request.getTradeMode() != null ? request.getTradeMode() : "SELL";
                st.setTradeMode(tradeMode);
                if (!"SELL".equals(tradeMode) && !"BUY".equals(tradeMode)) throw new BusinessException(400, "二手交易类型不合法");
                st.setItemPrice(requireNonNegative(request.getItemPrice(), "约定交易总价"));
                st.setReward(request.getItemPrice());
                st.setLocation(request.getLocation());
            }
            case "TEAM" -> {
                TeamingTask tm = (TeamingTask) task;
                tm.setMaxMembers(request.getMaxMembers() > 0 ? request.getMaxMembers() : 2);
                // 组队只做无偿招募，不将历史通用 reward 字段当作活动收费。
                tm.setReward(null);
                tm.setLocation(request.getLocation());
            }
            case "CONSULTING" -> {
                ConsultingTask ct = (ConsultingTask) task;
                String mode = request.getConsultingMode() != null ? request.getConsultingMode() : "ONLINE";
                ct.setConsultingMode(mode);
                ct.setReward(request.getReward()); // 报酬控制免费或收费
                ct.setLocation(request.getLocation());
            }
            default -> throw new BusinessException(400, "不支持的任务类型: " + request.getTaskType());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCampus(request.getCampus());
        task.setDeadline(request.getDeadline());

        if (!(task instanceof TeamingTask) && task.getReward() == null && request.getReward() != null) {
            task.setReward(request.getReward());
        }
    }

    /**
     * 发布者修改并重新提交需求（API 契约 3.6，pending_review 或 rejected → pending_review）。
     * 身份：userId 来自登录态，只有该需求发布者本人可调；账号需正常且信用分不低于 70（与发布同一门槛）。
     * 并发：先对任务加悲观写锁再判状态（“需求→订单”锁序的第一层），与管理员审核通过/驳回互斥，
     * 管理员刚通过的需求会在这里读到 published 而得到 409，不会被改回待审核。
     * 不可变：需求类型与分类不能改（子表按类型分表存储），不一致返回 400。
     * 成功后清空驳回理由与时间，并通知全体管理员重新审核。
     */
    @Transactional
    public TaskDTO updateOwnTask(Long userId, Long taskId, TaskCreateRequest request) {
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (!task.getRequester().getId().equals(userId)) {
            throw new BusinessException(403, "只能修改自己发布的需求");
        }
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (requester.getStatus() != 1) throw new BusinessException(403, "账号已被禁用");
        if (requester.getCreditScore() < 70) {
            throw new BusinessException(403, "您的信用分低于70分，暂无发布需求权限！");
        }
        if (task.getStatus() != TaskStatus.pending_review && task.getStatus() != TaskStatus.rejected) {
            throw new BusinessException(409, "只有待审核或被驳回的需求可以修改");
        }
        if (!request.getTaskType().equals(taskTypeOf(task))) {
            throw new BusinessException(400, "需求类型不能修改");
        }
        if (!request.getCategoryId().equals(task.getCategory().getId())) {
            throw new BusinessException(400, "需求分类不能修改");
        }

        applyRequest(task, request);
        task.setStatus(TaskStatus.pending_review);
        task.setRejectReason(null);
        task.setRejectedAt(null);
        task = taskRepository.save(task);

        notifyAdminsForReview(task, true);
        return toDTO(task, userId, false, false);
    }

    @Transactional(readOnly = true)
    public PageResult<TaskDTO> listTasks(Integer categoryId, String status,
                                         String keyword, String sortBy,
                                         String campus,
                                         int page, int size) {
        Long currentUserId = UserContext.getCurrentUserId();

        // 1. 获取当前用户并判断是否为管理员
        boolean isAdmin = false;
        if (currentUserId != null) {
            User currentUser = userRepository.findById(currentUserId).orElse(null);
            if (currentUser != null && currentUser.getRole() == Role.admin) {
                isAdmin = true;
            }
        }

        final boolean finalIsAdmin = isAdmin;

        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 2. 管理员可浏览所有状态；其他人看不到待审核、被驳回的需求（发布者本人的除外，API 契约 3.2/3.3）。
            if (!finalIsAdmin) {
                Predicate notModerationState = cb.not(root.get("status").in(TaskStatus.pending_review, TaskStatus.rejected));
                if (currentUserId != null) {
                    predicates.add(cb.or(notModerationState, cb.equal(root.get("requester").get("id"), currentUserId)));
                } else {
                    predicates.add(notModerationState);
                }
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            // API 契约 3.2：未传 status 默认只看 published（待接单）。无法识别的取值同样回落到 published，
            // 不能因为解析失败而退化成“不过滤状态”，那会把各种终态与待审需求都放进公共广场。
            predicates.add(cb.equal(root.get("status"), parseListStatus(status)));
            if (keyword != null && !keyword.isBlank()) {
                // 标题与描述任一包含关键词即命中（不区分大小写）。
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (campus != null && !campus.isBlank()) {
                predicates.add(cb.equal(root.get("campus"), campus));
            }
            if ("deadline_asc".equals(sortBy) && Task.class.equals(query.getResultType())) {

                jakarta.persistence.criteria.Expression<Integer> expireStatus = cb.<Integer>selectCase()
                        // 当 截止时间 > 当前时间 (未过期)，赋值 0 (排在前面)
                        .when(cb.greaterThan(root.get("deadline"), java.time.LocalDateTime.now()), 0)
                        // 其他情况 (已过期或无截止时间)，赋值 1 (排在后面)
                        .otherwise(1);

                // 排序规则：先按 expireStatus 升序（未过期的在前，已过期的在后），再按截止时间本身升序
                query.orderBy(cb.asc(expireStatus), cb.asc(root.get("deadline")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = buildSort(sortBy);
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<Task> taskPage = taskRepository.findAll(spec, pageable);

        List<TaskDTO> items = taskPage.getContent().stream()
                .map(t -> toDTO(t, currentUserId, false, finalIsAdmin))
                .toList();

        return new PageResult<>(items, new Pagination(page, size, taskPage.getTotalElements(), taskPage.getTotalPages()));
    }

    /** 广场状态过滤：空或非法取值回落到 published。 */
    private static TaskStatus parseListStatus(String status) {
        if (status == null || status.isBlank()) return TaskStatus.published;
        try {
            return TaskStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            return TaskStatus.published;
        }
    }

    @Transactional(readOnly = true)
    public PageResult<TaskDTO> listMyPublishedTasks(Long userId, int page, int size) {
        Specification<Task> spec = (root, query, cb) ->
                cb.equal(root.get("requester").get("id"), userId);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<Task> taskPage = taskRepository.findAll(spec, pageable);

        List<TaskDTO> items = taskPage.getContent().stream()
                .map(t -> toDTO(t, userId, false, false))
                .toList();

        return new PageResult<>(items, new Pagination(page, size, taskPage.getTotalElements(), taskPage.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public TaskDTO getTaskDetail(Long id, Long viewerId) {
        Task task = taskRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        boolean isRequester = viewerId != null && viewerId.equals(task.getRequester().getId());
        // 发布者本人无需再查是否管理员（管理员不能发布需求），省一次查询。
        boolean viewerIsAdmin = viewerId != null && !isRequester && userRepository.findById(viewerId)
                .map(u -> u.getRole() == Role.admin).orElse(false);
        // 待审核、被驳回的需求只对发布者与管理员可见，其他人按不存在处理（404，不暴露其存在）。
        boolean moderationState = task.getStatus() == TaskStatus.pending_review || task.getStatus() == TaskStatus.rejected;
        if (moderationState && !isRequester && !viewerIsAdmin) {
            throw new BusinessException(404, "需求不存在");
        }
        // 详情页是取件码的唯一展示入口：除发布者外，还要放行该需求的订单参与者。
        return toDTO(task, viewerId, true, viewerIsAdmin);
    }

    /**
     * 需求发布者撤销需求：
     * 校验仅限发布者本人操作且当前处于 published 状态；
     * 若该需求曾产生过订单历史，则软下架为 cancelled 以保留凭证，无任何订单时方可物理删除。
     */
    @Transactional
    public void deleteOwnTask(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (!task.getRequester().getId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己发布的需求");
        }
        // 被驳回的需求没有订单，发布者可直接删除（API 契约 3.5）；待审核的需求请走修改或等待审核。
        if (task.getStatus() != TaskStatus.published && task.getStatus() != TaskStatus.rejected) {
            throw new BusinessException(400, "只能删除待接单或被驳回的需求");
        }
        // 有接单历史时仅下架，保留订单引用与后续财务审计；无历史的新任务仍可删除。
        if (taskRepository.countOrders(taskId) > 0) task.setStatus(TaskStatus.cancelled);
        else taskRepository.delete(task);
    }

    /**
     * 管理员审核通过需求（pending_review → published）。
     * 前置：调用方（AdminService）已确认操作人是有效管理员，本方法不再识别身份。
     * 并发：先对任务加悲观写锁再判状态，两名管理员同时点击时只有一人能迁移成功，
     * 另一人读到 published 后得到 400，不会重复创建组队群或重复通知（锁顺序与 OrderService 的“任务→订单”一致）。
     * 时间：publishedAt 取业务时钟，列表按它倒序，不能随 JVM 时区漂移。
     *
     * @return 已迁移的需求实体，供后台在同一事务内组装展示 DTO
     */
    @Transactional
    public Task approveByAdmin(Long taskId) {
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (task.getStatus() != TaskStatus.pending_review) {
            throw new BusinessException(400, "仅待审核状态的需求可以审核通过");
        }
        task.setStatus(TaskStatus.published);
        task.setPublishedAt(LocalDateTime.now(clock));
        taskRepository.save(task);

        // 组队需求通过审核后才建群；与状态迁移同事务，建群失败则整体回滚，不会出现已发布却无群。
        if (task instanceof TeamingTask) {
            chatIntegrationService.createTeamGroup(task);
        }
        notifyReviewResult(task, true, null);
        return task;
    }

    /**
     * 管理员驳回需求（pending_review → rejected，TODO §1.1）。
     * 驳回理由最长 200 字，保存到需求上供发布者在详情里查看，并同时随站内通知发出；
     * 发布者修改后可重新提交（{@link #updateOwnTask}）。历史上被驳回而置为 cancelled 的数据不转换。
     * 加锁与状态前置判断同 {@link #approveByAdmin}，避免“已通过又被驳回”的丢失更新。
     */
    @Transactional
    public Task rejectByAdmin(Long taskId, String reason) {
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (task.getStatus() != TaskStatus.pending_review) {
            throw new BusinessException(400, "仅待审核状态的需求可以驳回");
        }
        String trimmed = reason == null ? null : reason.trim();
        if (trimmed != null && trimmed.length() > 200) {
            throw new BusinessException(400, "驳回理由不能超过200字");
        }
        task.setStatus(TaskStatus.rejected);
        task.setRejectReason(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        task.setRejectedAt(LocalDateTime.now(clock));
        taskRepository.save(task);
        notifyReviewResult(task, false, task.getRejectReason());
        return task;
    }

    /**
     * 管理员物理删除需求，级联清理评价 → 订单 → 需求。
     * 锁顺序：先锁任务再读订单（“任务→订单”），这样与接单（OrderService.acceptTask 同样先锁任务）互斥：
     * 不会出现管理员刚判定“无订单”、接单事务随后插入订单，最终外键报错或订单失去需求的情况。
     * 保留规则：
     * 1. 有金额快照（amountDue 非空）的订单涉及财务与争议审计，一律拒绝删除；
     * 2. 旧订单（amountDue 为空）若已被投诉或登记过支付单，外键会使删除失败，
     *    这里提前返回 409，而不是让数据库约束冲突冒成 500。
     */
    @Transactional
    public void deleteByAdmin(Long taskId) {
        taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        List<Order> orders = orderRepository.findByTaskId(taskId);
        if (orders.stream().anyMatch(o -> o.getAmountDue() != null)) {
            throw new BusinessException(409, "任务已有新业务订单，须保留财务和争议审计记录，不能物理删除");
        }
        for (Order o : orders) {
            if (!complaintRepository.findByOrderId(o.getId()).isEmpty()
                    || paymentAttempts.existsByOrderId(o.getId())) {
                throw new BusinessException(409, "任务的历史订单已有投诉或支付记录，须保留审计记录，不能物理删除");
            }
        }
        for (Order o : orders) {
            reviewRepository.deleteByOrder_Id(o.getId());
            orderRepository.delete(o);
        }
        taskRepository.deleteById(taskId);
    }

    /** 审核结果通知发布者；类型固定 TASK_REVIEW_RESULT，驳回理由非空时附带。 */
    private void notifyReviewResult(Task task, boolean approved, String reason) {
        String content = "你的需求「" + task.getTitle() + "」" + (approved ? "已通过审核" : "已被驳回") + "。";
        if (reason != null && !reason.isBlank()) {
            content += "\n驳回理由：" + reason;
        }
        notificationService.createNotification(task.getRequester().getId(), "需求审核结果", content, "TASK_REVIEW_RESULT");
    }

    private Sort buildSort(String sortBy) {
        if ("reward".equals(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "reward");
        } else if ("deadline_asc".equals(sortBy)) {
            // 【修改】返回 unsorted，将排序逻辑交给 Specification 处理
            return Sort.unsorted();
        }
        return Sort.by(Sort.Direction.DESC, "publishedAt");
    }

    /**
     * 取件码是取到快递的凭据，只能给发布者和该需求的订单参与者看；公开广场、未登录请求一律不返回。
     * 身份只来自服务端登录态（viewerId 由 UserContext 解析，未登录为 null），不信任任何客户端传参。
     *
     * @param includeParticipants 为 true 时额外查询订单参与者（仅详情页，单条需求一次存在性查询）；
     *                            列表页为 false，避免每行一次查询，列表里只有发布者本人能看到自己需求的取件码。
     */
    private boolean canSeePickupCode(Task task, Long viewerId, boolean includeParticipants) {
        if (viewerId == null) return false;
        if (task.getRequester() != null && viewerId.equals(task.getRequester().getId())) return true;
        // 已取消的订单不再算参与者：接单人退出后不应继续持有取件码。
        return includeParticipants && orderRepository.existsByTask_IdAndProvider_IdAndStatusNot(
                task.getId(), viewerId, Order.OrderStatus.cancelled);
    }

    private TaskDTO toDTO(Task task, Long viewerId, boolean includeParticipants, boolean viewerIsAdmin) {
        User requester = task.getRequester();
        Category category = task.getCategory();

        TaskDTO.TaskDTOBuilder builder = TaskDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .categoryName(category != null ? category.getName() : null)
                .categoryId(category != null ? category.getId() : null)
                .hasReview(category != null && Boolean.TRUE.equals(category.getHasReview()))
                .reward(task.getReward())
                .status(task.getStatus().name())
                // 驳回理由只给发布者与管理员看，公共访问者为空（API 契约 3.3）。
                .rejectReason(viewerIsAdmin || (viewerId != null && viewerId.equals(requester.getId())) ? task.getRejectReason() : null)
                .campus(task.getCampus())
                .location(task.getLocation())
                .deadline(task.getDeadline())
                .viewCount(task.getViewCount())
                .publishedAt(task.getPublishedAt())
                .publisher(TaskDTO.Publisher.builder()
                        .userId(requester.getId())
                        .nickname(requester.getName())
                        .avatar(requester.getAvatar())
                        .creditScore(requester.getCreditScore())
                        .build());

        String taskType;
        if (task instanceof ExpressTask et) {
            taskType = "RUNNING";
            builder.pickupLocation(et.getPickupLocation())
                   .deliveryLocation(et.getDeliveryLocation())
                   .pickupCode(canSeePickupCode(task, viewerId, includeParticipants) ? et.getPickupCode() : null)
                   .expressReward(et.getExpressReward());
        } else if (task instanceof TutoringTask tt) {
            taskType = "TUTORING";
            builder.subject(tt.getSubject())
                   .pricePerHour(tt.getPricePerHour())
                   .durationHours(tt.getDurationHours())
                   .tutoringLocation(tt.getTutoringLocation());
        } else if (task instanceof SecondHandTask st) {
            taskType = "FLEA";
            builder.tradeMode(st.getTradeMode() != null ? st.getTradeMode() : "SELL")
                    .itemPrice(st.getItemPrice());
        } else if (task instanceof TeamingTask tm) {
            taskType = "TEAM";
            builder.maxMembers(tm.getMaxMembers())
                   .chatGroupId(tm.getChatGroupId());
        } else if (task instanceof ConsultingTask ct) {
            taskType = "CONSULTING";
            builder.consultingMode(ct.getConsultingMode());
        } else {
            taskType = null;
        }
        builder.taskType(taskType);
        return builder.build();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, fieldName + "不能小于0");
        }
        if (value.stripTrailingZeros().scale() > 2 || value.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BusinessException(400, fieldName + "最多两位小数且不能超出金额范围");
        return value;
    }

    /** 通知全体管理员审核；resubmitted 为 true 表示发布者修改被驳回的需求后重新提交。 */
    private void notifyAdminsForReview(Task task, boolean resubmitted) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.admin)
                .toList();
        if (admins.isEmpty()) return;

        String title = (resubmitted ? "需求已修改待重新审核: " : "新需求待审核: ") + task.getTitle();
        String content = buildReviewNotificationContent(task);

        admins.forEach(admin -> notificationService.createNotification(admin.getId(), title, content, "TASK_REVIEW"));
    }

    private String buildReviewNotificationContent(Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("【新需求待审核】\n");
        sb.append("需求ID: ").append(task.getId()).append("\n");
        sb.append("标题: ").append(task.getTitle()).append("\n");
        sb.append("描述: ").append(task.getDescription()).append("\n");
        sb.append("分类: ").append(task.getCategory() != null ? task.getCategory().getName() : "").append("\n");
        sb.append("报酬: ").append(task.getReward() != null ? task.getReward().toString() : "面议").append("\n");
        sb.append("地点: ").append(task.getLocation() != null ? task.getLocation() : "未指定").append("\n");
        sb.append("截止时间: ").append(task.getDeadline() != null ? task.getDeadline().toString() : "无").append("\n");
        sb.append("发布者: ").append(task.getRequester() != null ? task.getRequester().getName() : "").append("\n");
        sb.append("类型: ");
        if (task instanceof ExpressTask) sb.append("跑腿");
        else if (task instanceof TutoringTask) sb.append("辅导");
        else if (task instanceof SecondHandTask) sb.append("二手");
        else if (task instanceof TeamingTask) sb.append("组队");
        else if (task instanceof ConsultingTask) sb.append("咨询");
        sb.append("\n");
        if (task instanceof ExpressTask et) {
            sb.append("取货地点: ").append(et.getPickupLocation()).append("\n");
            sb.append("送达地点: ").append(et.getDeliveryLocation()).append("\n");
        } else if (task instanceof TutoringTask tt) {
            sb.append("辅导科目: ").append(tt.getSubject()).append("\n");
            sb.append("时薪: ").append(tt.getPricePerHour()).append("\n");
        } else if (task instanceof SecondHandTask st) {
            sb.append("交易模式: ").append("SELL".equals(st.getTradeMode()) ? "出售" : "求购").append("\n");
            if (st.getItemPrice() != null) sb.append("物品价格: ").append(st.getItemPrice()).append("\n");
        } else if (task instanceof TeamingTask tm) {
            sb.append("最大成员数: ").append(tm.getMaxMembers()).append("\n");
        } else if (task instanceof ConsultingTask ct) {
            sb.append("咨询方式: ").append("ONLINE".equals(ct.getConsultingMode()) ? "线上咨询" : "线下咨询").append("\n");
        }
        return sb.toString();
    }
}
