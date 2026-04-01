package com.vox.controller.message;

import lombok.Data;

@Data
public class MessagePaginationResponse {
    private int page;
    private int size;
    private long total;
}
