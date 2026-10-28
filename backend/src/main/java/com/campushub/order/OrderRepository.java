package com.campushub.order;

import com.campushub.order.Order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM TaskOrder o " +
            "JOIN FETCH o.task t " +
            "JOIN FETCH o.requester " +
            "JOIN FETCH o.provider " +
            "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(Long id);

    @Query("SELECT o FROM TaskOrder o " +
            "JOIN FETCH o.task t " +
            "JOIN FETCH o.requester " +
            "JOIN FETCH o.provider " +
            "WHERE o.provider.id = :providerId " +
            "AND (:status IS NULL OR o.status = :status) " +
            "ORDER BY o.acceptedAt DESC")
    List<Order> findAcceptHistory(Long providerId, OrderStatus status);

    Optional<Order> findFirstByTaskIdOrderByIdDesc(Long taskId);
    List<Order> findByTaskId(Long taskId);

    @Query("SELECT o FROM TaskOrder o WHERE o.status = :status AND o.completedAt <= :threshold AND o.task.category.hasReview = true AND NOT EXISTS (SELECT r FROM Review r WHERE r.order.id = o.id)")
    List<Order> findOrdersPendingAutoReview(@Param("status") Order.OrderStatus status, @Param("threshold") LocalDateTime threshold);
}