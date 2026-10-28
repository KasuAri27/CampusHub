package com.campushub.admin;

import lombok.Data;

/**
 * 管理员发送系统通知请求.
 */
@Data
public class SystemNotificationRequest {
    /** 接收者用户ID，为空则发送给全体用户 */
    private Long receiverId;
    private String title;
    private String content;
    private String type;
}
