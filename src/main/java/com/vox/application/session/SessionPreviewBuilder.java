package com.vox.application.session;

import com.vox.infrastructure.persistence.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class SessionPreviewBuilder {

    public String build(Message message) {
        if (message == null) {
            return "";
        }
        return switch (message.getType()) {
            case TEXT -> message.getContent() == null ? "" : message.getContent();
            case IMAGE -> "[Image]";
            case VIDEO -> "[Video]";
            case DYNAMIC_PHOTO -> "[Dynamic Photo]";
            case FILE -> "[File]";
        };
    }
}

