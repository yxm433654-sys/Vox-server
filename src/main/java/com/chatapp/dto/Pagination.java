package com.chatapp.dto;

import lombok.Data;

@Data
public class Pagination {
    private int page;
    private int size;
    private long total;
}
