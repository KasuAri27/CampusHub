package com.campushub.user;

import org.springframework.data.jpa.repository.JpaRepository;

/** 信用事件的唯一键与用户行锁共同保证去重和并发更新正确。 */
public interface CreditEventRepository extends JpaRepository<CreditEvent, Long> {
    boolean existsByEventKey(String eventKey);
    java.util.Optional<CreditEvent> findByEventKey(String eventKey);
}
