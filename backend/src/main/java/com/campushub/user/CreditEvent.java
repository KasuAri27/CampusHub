package com.campushub.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 信用变化审计记录。唯一业务键防止同一笔逾期、评价或管理员操作重复生效。 */
@Entity
@Table(name = "credit_events")
@Getter @Setter
public class CreditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String eventKey;
    private Long userId;
    private int previousScore;
    private int resultingScore;
    private int requestedDelta;
    @Column(length = 1000)
    private String reason;
    /** 流水发生时间；库表列名统一为 create_time（M003），字段名保持不变以免影响调用方。 */
    @Column(name = "create_time")
    private LocalDateTime createdAt;
}
