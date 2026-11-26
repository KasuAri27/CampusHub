package com.campushub.task;

import java.time.LocalDateTime;

/**
 * 需求的聊天只读视图（模块契约 §3.6，交易核心 → 消息集群）。
 * 消息集群只读取这里的标量字段，不再引用 Task 实体、TaskRepository 或订单状态。
 *
 * @param taskId              需求 ID
 * @param team                是否组队需求；按子类（辨别列 TEAM）判断，而不是靠标题或最大人数猜
 * @param title               需求标题
 * @param status              {@link Task.TaskStatus} 名称
 * @param requesterId         发布者用户 ID
 * @param maxMembers          组队人数上限（含发起人）；非组队为 null
 * @param deadline            截止时间，业务时区（Asia/Shanghai）的无时区时间，可为空
 * @param updatedAt           需求最近更新时间；群聊清理按它计算闲置时长
 * @param hasOpenObligations  该需求下存在订单处于争议中、待付款，或已付款但未登记结算；
 *                            为 true 时会话必须保留以便留证，由交易核心解释订单状态机
 */
public record TaskChatView(
        Long taskId,
        boolean team,
        String title,
        String status,
        Long requesterId,
        Integer maxMembers,
        LocalDateTime deadline,
        LocalDateTime updatedAt,
        boolean hasOpenObligations) {
}
