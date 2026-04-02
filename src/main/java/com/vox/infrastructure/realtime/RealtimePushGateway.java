package com.vox.infrastructure.realtime;

public interface RealtimePushGateway {
    void pushNewMessage(Long userId, Object data);

    void pushSessionUpdated(Long userId, Object data);

    void pushSessionListChanged(Long userId);

    /**
     * Notifies the message sender that a specific message has been read.
     * Sends a READ_RECEIPT event distinct from NEW_MESSAGE so clients can update
     * delivery indicators without misinterpreting it as a new incoming message.
     */
    void pushMessageRead(Long userId, Object data);
}
