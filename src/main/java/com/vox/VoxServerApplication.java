package com.vox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.vox", "com.chatapp"})
@EnableAsync
public class VoxServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoxServerApplication.class, args);
    }
}
