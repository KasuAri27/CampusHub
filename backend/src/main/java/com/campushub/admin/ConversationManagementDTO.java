package com.campushub.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationManagementDTO {
    private Long id;
    private String convType;
    private Long taskId;
    private Long orderId;
    private String groupName;
    private Integer memberCount;
    private List<String> memberNames;
    private String lastMessageContent;
    private String lastMessageSenderName;
    private LocalDateTime lastMessageTime;
    private Boolean lastMessageIsRecalled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}
