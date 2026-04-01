package com.vox.infrastructure.realtime;

public interface RealtimePushGateway {
    void pushNewMessage(Long userId, Object data);

    void pushSessionUpdated(Long userId, Object data);

    void pushSessionListChanged(Long userId);
}
