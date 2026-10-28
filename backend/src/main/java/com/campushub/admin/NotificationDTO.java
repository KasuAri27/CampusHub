package com.campushub.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long receiverId;
    private String title;
    private String content;
    private String type;
    private Boolean isRead;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
