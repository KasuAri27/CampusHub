package com.campushub.chat.handler;

import com.campushub.chat.dto.MessageDTO;
import com.campushub.chat.service.ConversationService;
import com.campushub.chat.service.MessageService;
import com.campushub.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** userId → List<WebSocketSession> */
    private final Map<Long, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;
    private final MessageService msgService;
    private final ConversationService convService;

    public ChatWebSocketHandler(JwtUtil jwtUtil, MessageService msgService, ConversationService convService) {
        this.jwtUtil = jwtUtil;
        this.msgService = msgService;
        this.convService = convService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId == null) {
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) {}
            return;
        }
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        session.getAttributes().put("userId", userId);
        log.debug("WS connected: userId={}", userId);
    }

    private void broadcastRecall(Long convId, MessageDTO dto) {
        List<Long> memberIds = convService.getMemberIds(convId);
        String json;
        try {
            // 发送给前端的 type 为 RECALL
            json = mapper.writeValueAsString(Map.of("type", "RECALL", "convId", convId, "message", dto));
        } catch (Exception e) {
            return;
        }
        for (Long uid : memberIds) {
            List<WebSocketSession> userSessions = sessions.get(uid);
            if (userSessions != null) {
                for (WebSocketSession s : userSessions) {
                    if (s.isOpen()) {
                        try { s.sendMessage(new TextMessage(json)); } catch (IOException ignored) {}
                    }
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;

        JsonNode json = mapper.readTree(message.getPayload());
        String type = json.has("type") ? json.get("type").asText() : "";

        switch (type) {
            case "PING" -> sendJson(session, "{\"type\":\"PONG\"}");
            case "SEND" -> {
                Long convId = json.get("convId").asLong();
                String content = json.get("content").asText();
                // 读取新增的 msgType，默认 TEXT
                String msgType = json.has("msgType") ? json.get("msgType").asText() : "TEXT";
                MessageDTO dto = msgService.saveAndGet(convId, userId, content, msgType);
                broadcast(convId, dto);
            }
            case "RECALL" -> {
                Long msgId = json.get("msgId").asLong();
                Long convId = json.get("convId").asLong();
                try {
                    MessageDTO dto = msgService.recallMessage(msgId, userId);
                    // 广播撤回事件，类型设为 RECALL
                    broadcastRecall(convId, dto);
                } catch (Exception e) {
                    log.error("撤回失败", e);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            List<WebSocketSession> list = sessions.get(userId);
            if (list != null) {
                list.remove(session);
                if (list.isEmpty()) sessions.remove(userId);
            }
        }
    }

    private void broadcast(Long convId, MessageDTO dto) {
        List<Long> memberIds = convService.getMemberIds(convId);
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("type", "MSG", "convId", convId, "message", dto));
        } catch (Exception e) {
            return;
        }
        for (Long uid : memberIds) {
            sendToUser(uid, json);
        }
    }

    /**
     * 对外暴露的广播方法，供 HTTP 发送消息端点调用，
     * 确保通过 REST API 发送的消息也能实时推送给会话内其他在线用户。
     */
    public void broadcastNewMessage(Long convId, MessageDTO dto) {
        broadcast(convId, dto);
    }

    private void sendToUser(Long userId, String json) {
        List<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            for (WebSocketSession s : userSessions) {
                if (s.isOpen()) {
                    try { s.sendMessage(new TextMessage(json)); } catch (IOException ignored) {}
                }
            }
        }
    }

    private void sendJson(WebSocketSession session, String json) {
        try { session.sendMessage(new TextMessage(json)); } catch (IOException ignored) {}
    }

    private Long getUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && "token".equals(pair[0])) {
                try {
                    return jwtUtil.getUserIdFromToken(pair[1]);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}
