package com.campushub.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByOrderId(Long orderId);
    boolean existsByOrderIdAndComplainantId(Long orderId, Long complainantId);
}
