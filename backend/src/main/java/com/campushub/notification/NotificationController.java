package com.campushub.notification;

import com.campushub.common.ApiResponse;
import com.campushub.common.PageResult;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResult<Notification>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Boolean unreadOnly) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(notificationService.getUserNotifications(userId, pageNum, pageSize, unreadOnly));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(UserContext.getCurrentUserId(), id);
        return ApiResponse.success("已标记为已读", null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead(UserContext.getCurrentUserId());
        return ApiResponse.success("全部已读", null);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.success(notificationService.getUnreadCount(UserContext.getCurrentUserId()));
    }
}