package com.vox.infrastructure.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketRealtimePushGateway implements RealtimePushGateway {

    private final WebSocketPushService webSocketPushService;

    @Override
    public void pushNewMessage(Long userId, Object data) {
        webSocketPushService.pushNewMessage(userId, data);
    }

    @Override
    public void pushSessionUpdated(Long userId, Object data) {
        webSocketPushService.pushSessionUpdated(userId, data);
    }

    @Override
    public void pushSessionListChanged(Long userId) {
        webSocketPushService.pushSessionListChanged(userId);
    }

    @Override
    public void pushMessageRead(Long userId, Object data) {
        webSocketPushService.pushMessageRead(userId, data);
    }
}
