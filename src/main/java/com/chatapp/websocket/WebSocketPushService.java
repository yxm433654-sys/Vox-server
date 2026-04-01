package com.chatapp.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public void pushNewMessage(Long userId, Object data) {
        sessionManager.get(userId).ifPresent(session -> send(session, Map.of("type", "NEW_MESSAGE", "data", data)));
    }

    public void pushSessionUpdated(Long userId, Object data) {
        sessionManager.get(userId).ifPresent(session -> send(session, Map.of("type", "SESSION_UPDATED", "data", data)));
    }

    public void pushSessionListChanged(Long userId) {
        sessionManager.get(userId).ifPresent(session -> send(session, Map.of("type", "SESSION_LIST_CHANGED")));
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            if (!session.isOpen()) {
                return;
            }
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (Exception ignored) {
        }
    }
}
