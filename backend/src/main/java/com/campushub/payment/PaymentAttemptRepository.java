package com.campushub.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/** 支付单保持稳定商户单号；查询与回调都通过同一事务入口记账。 */
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findFirstByOrderIdOrderByIdDesc(Long orderId);
    List<PaymentAttempt> findAllByOrderIdOrderByIdDesc(Long orderId);
    Optional<PaymentAttempt> findByOrderIdAndActiveSlot(Long orderId, Integer activeSlot);
    default Optional<PaymentAttempt> findByOrderId(Long orderId) { return findFirstByOrderIdOrderByIdDesc(orderId); }
    Optional<PaymentAttempt> findByOutTradeNo(String outTradeNo);
    boolean existsByOrderId(Long orderId);
    boolean existsByOrderIdAndStatusIn(Long orderId, java.util.Collection<PaymentAttempt.Status> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p where p.id = :id")
    Optional<PaymentAttempt> findLockedById(Long id);
    List<PaymentAttempt> findByStatusOrderByLastQueriedAtAscIdAsc(PaymentAttempt.Status status, Pageable pageable);
}
