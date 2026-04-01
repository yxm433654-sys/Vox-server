package com.vox.application.message;

import lombok.Data;

import java.util.List;

@Data
public class MessageHistoryResult {
    private List<MessageView> data;
    private MessagePageResult pagination;
}
