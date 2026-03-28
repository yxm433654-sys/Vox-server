package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageSendRequest {
    @NotNull
    private Long senderId;

    @NotNull
    private Long receiverId;

    @NotNull
    private String type;

    @Size(max = 10000)
    private String content;

    private Long resourceId;
    private Long videoResourceId;
}
