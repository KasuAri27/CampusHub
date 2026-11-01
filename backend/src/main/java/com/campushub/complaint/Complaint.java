package com.campushub.complaint;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 投诉发起人 */
    @Column(name = "complainant_id", nullable = false)
    private Long complainantId;

    /** 被投诉的订单 */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 被投诉人 */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 投诉理由 */
    @Column(nullable = false)
    private String reason;

    /** 详细描述 */
    @Column(length = 500)
    private String description;

    /** 证据图片URL */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 正式异议由管理员处理，保留原投诉、裁决理由和处理人供人工审查。 */
    @Builder.Default
    private boolean resolved = false;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    @Column(length = 500)
    private String resolution;
}
