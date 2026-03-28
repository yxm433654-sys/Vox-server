package com.chatapp.websocket;

import com.chatapp.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final JwtService jwtService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        Long userId = parseLong(params.get("userId"));
        String token = params.get("token");

        if (userId == null || token == null || token.isBlank() || !jwtService.validateForUser(token, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put("userId", userId);
        sessionManager.register(userId, session);
        log.info("WebSocket connected: userId={}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
        log.info("WebSocket closed: userId={}, status={}", userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
        session.close(CloseStatus.SERVER_ERROR);
    }

    private static Map<String, String> parseQueryParams(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getRawQuery() == null) {
            return Map.of();
        }

        String query = session.getUri().getRawQuery();
        Map<String, String> result = new HashMap<>();
        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }
}
