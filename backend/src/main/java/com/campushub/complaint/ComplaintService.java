package com.campushub.complaint;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.OrderRepository;
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

    @Transactional
    public void submitComplaint(Long complainantId, ComplaintRequest req) {
        if (!orderRepository.existsById(req.getOrderId())) {
            throw new BusinessException(404, "订单不存在");
        }
        if (complaintRepository.existsByOrderIdAndComplainantId(req.getOrderId(), complainantId)) {
            throw new BusinessException(409, "您已对该订单提交过投诉");
        }

        Complaint complaint = Complaint.builder()
                .complainantId(complainantId)
                .orderId(req.getOrderId())
                .targetId(req.getTargetId())
                .reason(req.getReason())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .build();
        complaintRepository.save(complaint);

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

    public List<Complaint> getByOrderId(Long orderId) {
        return complaintRepository.findByOrderId(orderId);
    }

    public Complaint getById(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "投诉不存在"));
    }
}
