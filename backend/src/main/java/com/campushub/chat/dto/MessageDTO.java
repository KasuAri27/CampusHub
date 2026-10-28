package com.campushub.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private Long convId;
    private Long senderId;
    private String senderName;
    private String msgType;
    private String content;
    private LocalDateTime createdAt;
    private Boolean isRecalled;
}
