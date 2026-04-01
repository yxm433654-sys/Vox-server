package com.vox.application.message;

import lombok.Data;

@Data
public class SendMessageCommand {
    private Long senderId;
    private Long receiverId;
    private String type;
    private String content;
    private Long resourceId;
    private Long videoResourceId;
}
