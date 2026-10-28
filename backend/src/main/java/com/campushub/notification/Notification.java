package com.campushub.notification;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long receiverId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String type;   // ORDER, SYSTEM, EVALUATION

    private Boolean isRead = false;

    @CreationTimestamp
    private LocalDateTime createTime;

    private LocalDateTime readTime;
}