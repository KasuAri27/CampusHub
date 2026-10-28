package com.campushub.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ConversationDTO {
    private Long id;
    private String convType;
    private Long taskId;
    private Long orderId;
    private String groupName;
    private Long ownerId;
    private Integer maxMembers;
    private int memberCount;
    private MessageDTO lastMessage;
    private long unreadCount;
    private String otherUserName;
    private String taskTitle;
    private String taskStatus;
    private int keepGroup;
    private boolean canDismiss;
    private boolean isOwner;
    private boolean isDeleted;
}
