package com.campushub.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

/** 日期主键在数据库层仲裁多实例竞争，同一事务失败不会留下半次恢复。 */
public interface CreditRecoveryRunRepository extends JpaRepository<CreditRecoveryRun, LocalDate> { }
