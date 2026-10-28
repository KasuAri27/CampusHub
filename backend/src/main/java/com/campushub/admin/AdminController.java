package com.campushub.admin;

import com.campushub.chat.dto.MessageDTO;
import com.campushub.common.ApiResponse;
import com.campushub.common.PageResult;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ==================== 仪表盘 ====================

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDTO> getStats() {
        return ApiResponse.success(adminService.getDashboardStats(UserContext.getCurrentUserId()));
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public ApiResponse<PageResult<UserManagementDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {
        return ApiResponse.success(
                adminService.listUsers(UserContext.getCurrentUserId(), page, size, keyword, role));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserManagementDTO> getUserDetail(@PathVariable Long id) {
        return ApiResponse.success(
                adminService.getUserDetail(UserContext.getCurrentUserId(), id));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> toggleUserStatus(@PathVariable Long id) {
        adminService.toggleUserStatus(UserContext.getCurrentUserId(), id);
        return ApiResponse.success("操作成功", null);
    }

    @PutMapping("/users/{id}/credit")
    public ApiResponse<Void> adjustCredit(@PathVariable Long id,
                                           @RequestBody CreditAdjustRequest req) {
        adminService.adjustCredit(UserContext.getCurrentUserId(), id, req.getDelta());
        return ApiResponse.success("信用分调整成功", null);
    }

    // ==================== 需求管理 ====================

    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskManagementDTO>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                adminService.listAllTasks(UserContext.getCurrentUserId(), page, size, status, keyword));
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        adminService.deleteTask(UserContext.getCurrentUserId(), id);
        return ApiResponse.success("删除成功", null);
    }

    @PutMapping("/tasks/{id}/approve")
    public ApiResponse<TaskManagementDTO> approveTask(@PathVariable Long id) {
        return ApiResponse.success(
                adminService.approveTask(UserContext.getCurrentUserId(), id));
    }

    @PutMapping("/tasks/{id}/reject")
    public ApiResponse<TaskManagementDTO> rejectTask(@PathVariable Long id,
                                                      @RequestBody(required = false) RejectRequest req) {
        return ApiResponse.success(
                adminService.rejectTask(UserContext.getCurrentUserId(), id,
                        req != null ? req.getReason() : null));
    }

    // ==================== 分类管理 ====================

    @GetMapping("/categories")
    public ApiResponse<List<CategoryManagementDTO>> listCategories() {
        return ApiResponse.success(adminService.listCategories(UserContext.getCurrentUserId()));
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryManagementDTO> createCategory(@RequestBody CategoryUpsertRequest req) {
        return ApiResponse.success(
                adminService.createCategory(UserContext.getCurrentUserId(), req));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryManagementDTO> updateCategory(@PathVariable Integer id,
                                                              @RequestBody CategoryUpsertRequest req) {
        return ApiResponse.success(
                adminService.updateCategory(UserContext.getCurrentUserId(), id, req));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Integer id) {
        adminService.deleteCategory(UserContext.getCurrentUserId(), id);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 系统通知 ====================

    @GetMapping("/notifications")
    public ApiResponse<PageResult<NotificationDTO>> listNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int size) {
        return ApiResponse.success(
                adminService.listNotifications(UserContext.getCurrentUserId(), page, size));
    }

    @PostMapping("/notifications")
    public ApiResponse<Void> sendNotification(@RequestBody SystemNotificationRequest req) {
        adminService.sendNotification(UserContext.getCurrentUserId(), req);
        return ApiResponse.success("发送成功", null);
    }

    // ==================== 聊天管理 ====================

    @GetMapping("/conversations")
    public ApiResponse<PageResult<ConversationManagementDTO>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int size,
            @RequestParam(required = false) String convType) {
        return ApiResponse.success(
                adminService.listAllConversations(UserContext.getCurrentUserId(), page, size, convType));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<PageResult<MessageDTO>> getConversationMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "50") int size) {
        return ApiResponse.success(
                adminService.getConversationMessages(UserContext.getCurrentUserId(), id, page, size));
    }
}
