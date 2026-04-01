package com.vox.controller.session;

import com.vox.domain.session.SessionSummary;
import org.springframework.stereotype.Component;

@Component
public class SessionResponseMapper {

    public SessionResponse toResponse(SessionSummary summary) {
        SessionResponse response = new SessionResponse();
        response.setId(summary.getId());
        response.setPeerId(summary.getPeerId());
        response.setPeerUsername(summary.getPeerUsername());
        response.setPeerAvatarUrl(summary.getPeerAvatarUrl());
        response.setLastMessageId(summary.getLastMessageId());
        response.setLastMessageType(summary.getLastMessageType());
        response.setLastMessagePreview(summary.getLastMessagePreview());
        response.setUnreadCount(summary.getUnreadCount());
        response.setUpdatedAt(summary.getUpdatedAt());
        return response;
    }
}
