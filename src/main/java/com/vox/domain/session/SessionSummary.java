package com.vox.domain.session;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class SessionSummary {
    Long id;
    Long peerId;
    String peerUsername;
    String peerAvatarUrl;
    Long lastMessageId;
    String lastMessageType;
    String lastMessagePreview;
    Integer unreadCount;
    LocalDateTime updatedAt;
}
