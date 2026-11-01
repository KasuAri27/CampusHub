package com.campushub.order;

import com.campushub.order.Order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("select o.task.id from TaskOrder o where o.id = :id")
    Optional<Long> findTaskId(@Param("id") Long id);

    /** 所有订单状态写操作使用同一行锁，通知重放与定时扫描不能重复扣分或结算。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TaskOrder o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") Long id);

    List<Order> findByRequester_IdOrProvider_IdOrderByIdDesc(Long requesterId, Long providerId);
    long countByTask_IdAndStatusNot(Long taskId, OrderStatus status);
    boolean existsByTask_IdAndProvider_IdAndStatusNot(Long taskId, Long providerId, OrderStatus status);
    List<Order> findByStatusIn(List<OrderStatus> statuses);

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

    List<Order> findByTaskId(Long taskId);

    @Query("SELECT o FROM TaskOrder o WHERE o.status = :status AND o.reviewEligibleAt <= :threshold AND o.disputed = false AND o.task.category.hasReview = true AND NOT EXISTS (SELECT r FROM Review r WHERE r.order.id = o.id)")
    List<Order> findOrdersPendingAutoReview(@Param("status") Order.OrderStatus status, @Param("threshold") LocalDateTime threshold);
}
