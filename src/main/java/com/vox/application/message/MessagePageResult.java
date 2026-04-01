package com.vox.application.message;

import lombok.Data;

@Data
public class MessagePageResult {
    private int page;
    private int size;
    private long total;
}
