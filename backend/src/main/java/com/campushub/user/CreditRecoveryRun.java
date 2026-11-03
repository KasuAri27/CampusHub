package com.campushub.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/** 一天只能成功执行一次信用恢复，避免应用重启或多实例各自恢复一次。 */
@Entity @Table(name = "credit_recovery_runs") @Getter @Setter
public class CreditRecoveryRun {
    @Id private LocalDate recoveryDay;
    @jakarta.persistence.Version private Long version;
}
