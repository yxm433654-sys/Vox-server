package com.vox.controller.session;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionResponse {
    private Long id;
    private Long peerId;
    private String peerUsername;
    private String peerAvatarUrl;
    private Long lastMessageId;
    private String lastMessageType;
    private String lastMessagePreview;
    private Integer unreadCount;
    private LocalDateTime updatedAt;
}
