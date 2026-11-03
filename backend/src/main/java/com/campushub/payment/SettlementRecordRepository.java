package com.campushub.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** 保留人工付款凭证和操作人，以供人工核账，而不是存放公开图片URL。 */
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, Long> {
    Optional<SettlementRecord> findByOrderId(Long orderId);
}
