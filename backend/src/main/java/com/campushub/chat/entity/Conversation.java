package com.campushub.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conv_type", nullable = false, length = 20)
    private String convType;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "group_name", length = 50)
    private String groupName;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "keep_group")
    private Integer keepGroup;

    @Column(name = "keep_asked_at")
    private LocalDateTime keepAskedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
