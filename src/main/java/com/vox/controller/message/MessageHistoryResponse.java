package com.vox.controller.message;

import lombok.Data;

import java.util.List;

@Data
public class MessageHistoryResponse {
    private List<MessageResponse> data;
    private MessagePaginationResponse pagination;
}
