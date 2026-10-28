package com.campushub.task;

import com.campushub.category.Category;
import com.campushub.category.CategoryRepository;
import com.campushub.chat.service.ChatIntegrationService;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import com.campushub.common.PageResult.Pagination;
import com.campushub.notification.Notification;
import com.campushub.notification.NotificationRepository;
import com.campushub.task.Task.TaskStatus;
import com.campushub.user.User;
import com.campushub.user.User.Role;
import com.campushub.user.UserRepository;
import com.campushub.util.UserContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ChatIntegrationService chatIntegrationService;
    private final NotificationRepository notificationRepository;

    @Transactional
    public TaskDTO createTask(TaskCreateRequest request, Long userId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (requester.getCreditScore() < 70) {
            throw new BusinessException(403, "您的信用分低于70分，暂无发布需求权限！");
        }

        if (requester.getRole() != Role.requester) {
            throw new BusinessException(403, "仅需求方可以发布需求");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));

        Task task;
        switch (request.getTaskType()) {
            case "RUNNING" -> {
                ExpressTask et = new ExpressTask();
                et.setPickupLocation(requireNonBlank(request.getPickupLocation(), "取货地点"));
                et.setDeliveryLocation(requireNonBlank(request.getDeliveryLocation(), "送达地点"));
                et.setPickupCode(request.getPickupCode());
                et.setExpressReward(requireNonNegative(request.getExpressReward(), "跑腿报酬"));
                et.setReward(request.getExpressReward());
                et.setLocation(request.getPickupLocation() + " → " + request.getDeliveryLocation());
                task = et;
            }
            case "TUTORING" -> {
                TutoringTask tt = new TutoringTask();
                tt.setSubject(requireNonBlank(request.getSubject(), "辅导科目"));
                tt.setPricePerHour(requireNonNegative(request.getPricePerHour(), "时薪"));
                tt.setTutoringLocation(requireNonBlank(request.getTutoringLocation(), "辅导地点"));
                tt.setReward(request.getPricePerHour());
                tt.setLocation(request.getTutoringLocation());
                task = tt;
            }
            case "FLEA" -> {
                SecondHandTask st = new SecondHandTask();
                String tradeMode = request.getTradeMode() != null ? request.getTradeMode() : "SELL";
                st.setTradeMode(tradeMode);
                if ("SELL".equals(tradeMode)) {
                    st.setItemPrice(requireNonNegative(request.getItemPrice(), "物品售价"));
                    st.setReward(request.getItemPrice());
                } else {
                    st.setItemPrice(null);
                    st.setReward(null);
                }
                st.setLocation(request.getLocation());
                task = st;
            }
            case "TEAM" -> {
                TeamingTask tm = new TeamingTask();
                tm.setMaxMembers(request.getMaxMembers() > 0 ? request.getMaxMembers() : 2);
                tm.setReward(request.getReward());
                tm.setLocation(request.getLocation());
                task = tm;
            }
            case "CONSULTING" -> {
                ConsultingTask ct = new ConsultingTask();
                String mode = request.getConsultingMode() != null ? request.getConsultingMode() : "ONLINE";
                ct.setConsultingMode(mode);
                ct.setReward(request.getReward()); // 报酬控制免费或收费
                ct.setLocation(request.getLocation());
                task = ct;
            }
            default -> throw new BusinessException(400, "不支持的任务类型: " + request.getTaskType());
        }

        task.setRequester(requester);
        task.setCategory(category);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCampus(request.getCampus());
        task.setDeadline(request.getDeadline());
        task.setStatus(TaskStatus.pending_review);

        if (task.getReward() == null && request.getReward() != null) {
            task.setReward(request.getReward());
        }

        task = taskRepository.save(task);

        notifyAdminsForReview(task);

        return toDTO(task);
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

            // 2. 如果是管理员，则开放所有状态的浏览权限；普通用户仍过滤掉待审核的数据（除非是自己发布的）
            if (!finalIsAdmin) {
                if (currentUserId != null) {
                    predicates.add(cb.or(
                            cb.notEqual(root.get("status"), TaskStatus.pending_review),
                            cb.equal(root.get("requester").get("id"), currentUserId)
                    ));
                } else {
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.pending_review));
                }
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null && !status.isBlank()) {
                try {
                    TaskStatus ts = TaskStatus.valueOf(status);
                    predicates.add(cb.equal(root.get("status"), ts));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("title"), "%" + keyword + "%"));
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
                .map(this::toDTO)
                .toList();

        return new PageResult<>(items, new Pagination(page, size, taskPage.getTotalElements(), taskPage.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public PageResult<TaskDTO> listMyPublishedTasks(Long userId, int page, int size) {
        Specification<Task> spec = (root, query, cb) ->
                cb.equal(root.get("requester").get("id"), userId);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<Task> taskPage = taskRepository.findAll(spec, pageable);

        List<TaskDTO> items = taskPage.getContent().stream()
                .map(this::toDTO)
                .toList();

        return new PageResult<>(items, new Pagination(page, size, taskPage.getTotalElements(), taskPage.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public TaskDTO getTaskDetail(Long id) {
        Task task = taskRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        return toDTO(task);
    }

    @Transactional
    public void deleteOwnTask(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));
        if (!task.getRequester().getId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己发布的需求");
        }
        if (task.getStatus() != TaskStatus.published) {
            throw new BusinessException(400, "只能删除待接单状态的需求");
        }
        taskRepository.delete(task);
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

    private TaskDTO toDTO(Task task) {
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
                   .pickupCode(et.getPickupCode())
                   .expressReward(et.getExpressReward());
        } else if (task instanceof TutoringTask tt) {
            taskType = "TUTORING";
            builder.subject(tt.getSubject())
                   .pricePerHour(tt.getPricePerHour())
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
        return value;
    }

    private void notifyAdminsForReview(Task task) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.admin)
                .toList();
        if (admins.isEmpty()) return;

        String title = "新需求待审核: " + task.getTitle();
        String content = buildReviewNotificationContent(task);

        List<Notification> batch = admins.stream().map(admin -> {
            Notification n = new Notification();
            n.setReceiverId(admin.getId());
            n.setTitle(title);
            n.setContent(content);
            n.setType("TASK_REVIEW");
            return n;
        }).toList();
        notificationRepository.saveAll(batch);
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
