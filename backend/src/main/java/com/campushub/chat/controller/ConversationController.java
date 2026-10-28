package com.campushub.chat.controller;

import com.campushub.chat.dto.ConversationDTO;
import com.campushub.chat.dto.MessageDTO;
import com.campushub.chat.handler.ChatWebSocketHandler;
import com.campushub.chat.service.ConversationService;
import com.campushub.chat.service.MessageService;
import com.campushub.common.ApiResponse;
import com.campushub.common.PageResult;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService convService;
    private final MessageService msgService;
    private final ChatWebSocketHandler wsHandler;

    @GetMapping
    public ApiResponse<List<ConversationDTO>> list() {
        Long userId = UserContext.getCurrentUserId();
        convService.getOrCreateSystemNotify(userId);
        return ApiResponse.success(convService.listConversations(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDTO> detail(@PathVariable Long id) {
        return ApiResponse.success(
                convService.listConversations(UserContext.getCurrentUserId()).stream()
                        .filter(c -> c.getId().equals(id)).findFirst().orElse(null));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<PageResult<?>> getMessages(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(msgService.getHistory(id, page, size));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        convService.markAsRead(id, UserContext.getCurrentUserId());
        return ApiResponse.success("已标记已读", null);
    }

    @PostMapping("/{id}/keep")
    public ApiResponse<Void> keepGroup(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        var conv = convService.getById(id);
        // == 修复：采用全新的复合权限判定规则，防止空指针，完美支持私聊双方及群主/需求方默认兜底 ==
        if (!convService.hasOperatePermission(conv, userId)) {
            return ApiResponse.error(403, "仅限会话相关负责人操作");
        }
        convService.keepGroup(id);
        return ApiResponse.success("已确认保留会话", null);
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<MessageDTO> sendMessage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = UserContext.getCurrentUserId();
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ApiResponse.error(400, "消息内容不能为空");
        }
        String msgType = body.getOrDefault("msgType", "TEXT");
        MessageDTO dto = msgService.saveAndGet(id, userId, content, msgType);
        wsHandler.broadcastNewMessage(id, dto);
        return ApiResponse.success(dto);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> dismiss(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        var conv = convService.getById(id);
        // == 修复：同上 ==
        if (!convService.hasOperatePermission(conv, userId)) {
            return ApiResponse.error(403, "您没有权限解散或删除此会话");
        }
        convService.dismissGroup(id);
        return ApiResponse.success("会话已顺利解散/删除", null);
    }
}
