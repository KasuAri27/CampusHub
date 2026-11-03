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
/** 管理端会话监管视图，展示群聊/私聊成员信息、最后一条发言摘要及撤回状态。 */
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
