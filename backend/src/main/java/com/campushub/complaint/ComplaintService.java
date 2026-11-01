package com.campushub.complaint;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.OrderRepository;
import com.campushub.order.OrderLifecycleService;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final OrderLifecycleService lifecycle;
    private final com.campushub.order.OrderLocks locks;

    @Transactional
    public void submitComplaint(Long complainantId, ComplaintRequest req) {
        var order = locks.lock(req.getOrderId());
        boolean requester = order.getRequester().getId().equals(complainantId);
        boolean provider = order.getProvider().getId().equals(complainantId);
        if (!requester && !provider) throw new BusinessException(403, "只有订单参与者可以投诉");
        Long targetId = requester ? order.getProvider().getId() : order.getRequester().getId();
        if (req.getTargetId() != null && !req.getTargetId().equals(targetId)) throw new BusinessException(400, "被投诉人须为订单另一方");
        if (req.getReason() == null || req.getReason().isBlank() || req.getReason().length() > 100
                || req.getDescription() != null && req.getDescription().length() > 500)
            throw new BusinessException(400, "请填写合法投诉理由和描述");
        if (complaintRepository.existsByOrderIdAndComplainantId(req.getOrderId(), complainantId)) {
            throw new BusinessException(409, "您已对该订单提交过投诉");
        }

        Complaint complaint = Complaint.builder()
                .complainantId(complainantId)
                .orderId(req.getOrderId())
                .targetId(targetId)
                .reason(req.getReason())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .build();
        complaintRepository.save(complaint);
        // 需求验收方提出履约/质量正式异议才暂停计时；服务方催款投诉不能反而暂停追缴。
        if (java.util.Objects.equals(order.getPayerId(), complainantId)
                && ("服务未完成".equals(req.getReason()) || "质量不符".equals(req.getReason())))
            lifecycle.openDispute(order.getId(), complainantId, req.getDescription() == null ? req.getReason() : req.getDescription());

        // 通知所有管理员
        User complainant = userRepository.findById(complainantId).orElse(null);
        String complainantName = complainant != null ? complainant.getName() : "用户";
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.admin)
                .collect(Collectors.toList());
        StringBuilder content = new StringBuilder();
        content.append(complainantName).append(" 提交了投诉\n");
        content.append("理由：").append(req.getReason()).append("\n");
        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            content.append("描述：").append(req.getDescription()).append("\n");
        }
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            content.append("图片：http://localhost:8080").append(req.getImageUrl());
        }
        for (User admin : admins) {
            notificationService.createNotification(admin.getId(),
                    "新投诉", content.toString(), "COMPLAINT");
        }
    }

    @Transactional(readOnly = true)
    public List<Complaint> getByOrderId(Long orderId, Long actorId) {
        requireAccess(orderId, actorId);
        return complaintRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Complaint getById(Long id, Long actorId) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "投诉不存在"));
        requireAccess(complaint.getOrderId(), actorId);
        return complaint;
    }
    private void requireAccess(Long orderId, Long actorId) {
        var order = orderRepository.findById(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
        boolean participant = order.getRequester().getId().equals(actorId) || order.getProvider().getId().equals(actorId);
        boolean administrator = userRepository.findById(actorId).filter(u -> u.getRole() == User.Role.admin && u.getStatus() == 1).isPresent();
        if (!participant && !administrator) throw new BusinessException(403, "无权读取此订单的投诉");
    }
}
