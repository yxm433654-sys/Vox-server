package com.vox.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        sessions.remove(userId, session);
    }

    public Optional<WebSocketSession> get(Long userId) {
        return Optional.ofNullable(sessions.get(userId));
    }
}
