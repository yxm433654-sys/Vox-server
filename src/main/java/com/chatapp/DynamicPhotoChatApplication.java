package com.chatapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 跨平台动态图片兼容即时通信系统
 * 主应用入口
 */
@SpringBootApplication
@EnableAsync
public class DynamicPhotoChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicPhotoChatApplication.class, args);
    }
}
