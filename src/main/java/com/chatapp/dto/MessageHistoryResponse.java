package com.chatapp.dto;

import lombok.Data;

import java.util.List;

@Data
public class MessageHistoryResponse {
    private List<MessageDto> data;
    private Pagination pagination;
}
