package com.campushub.task;

import com.campushub.category.Category;
import com.campushub.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求基类（抽象），采用 JOINED 类表继承策略.
 * <p>
 * 子类：{@code ExpressTask}（跑腿）、{@code TutoringTask}（辅导）、
 * {@code SecondHandTask}（二手）、{@code TeamingTask}（组队）。
 * 辨别者字段 {@code task_type} 由 JPA 自动写入，值为 RUNNING/TUTORING/FLEA/TEAM。
 * </p>
 */
@Entity
@Table(name = "tasks")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "task_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    /** 报酬金额（基数保留，向后兼容 OrderService） */
    @Column(precision = 10, scale = 2)
    private BigDecimal reward;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('pending_review','published','in_progress','completed','cancelled')")
    private TaskStatus status = TaskStatus.pending_review;

    /** 所属校区 */
    @Column(length = 20)
    private String campus;

    /** 地点（基数保留，向后兼容） */
    @Column(length = 100)
    private String location;

    private LocalDateTime deadline;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum TaskStatus {
        pending_review,
        published,
        in_progress,
        completed,
        cancelled
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (publishedAt == null) publishedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
