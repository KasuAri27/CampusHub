package com.campushub.task;

import com.campushub.common.ApiResponse;
import com.campushub.common.PageResult;
import com.campushub.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskDTO> createTask(@Valid @RequestBody TaskCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(taskService.createTask(request, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        taskService.deleteOwnTask(userId, id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping
    public ApiResponse<PageResult<TaskDTO>> listTasks(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String campus,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int size) {
        return ApiResponse.success(taskService.listTasks(categoryId, status, keyword, sort, campus, page, size));
    }

    @GetMapping("/mine/published")
    public ApiResponse<PageResult<TaskDTO>> listMyPublishedTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int size) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(taskService.listMyPublishedTasks(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskDTO> getTaskDetail(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskDetail(id));
    }
}
