package com.campushub.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private long totalUsers;
    private long bannedUsers;
    private long totalTasks;
    private long publishedTasks;
    private long inProgressTasks;
    private long completedTasks;
    /** 被取消的订单/需求数量 */
    private long totalOrders;
    private long todayNewTasks;
    // 聊天统计
    private long totalConversations;
    private long totalMessages;
    private long todaysMessages;
}
