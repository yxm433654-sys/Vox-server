package com.vox.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public void pushNewMessage(Long userId, Object data) {
        broadcast(userId, Map.of("type", "NEW_MESSAGE", "data", data));
    }

    public void pushSessionUpdated(Long userId, Object data) {
        broadcast(userId, Map.of("type", "SESSION_UPDATED", "data", data));
    }

    public void pushSessionListChanged(Long userId) {
        broadcast(userId, Map.of("type", "SESSION_LIST_CHANGED"));
    }

    /**
     * Sends a READ_RECEIPT event to the original message sender.
     * Clients use this to update the delivery/read indicator on sent messages.
     */
    public void pushMessageRead(Long userId, Object data) {
        broadcast(userId, Map.of("type", "READ_RECEIPT", "data", data));
    }

    private void broadcast(Long userId, Object payload) {
        for (WebSocketSession session : sessionRegistry.getAll(userId)) {
            send(session, payload);
        }
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            if (!session.isOpen()) return;
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ignored) {
        }
    }
}
