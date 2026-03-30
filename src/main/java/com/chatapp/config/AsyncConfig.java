package com.chatapp.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {
  /**
   * 限制 @Async 任务并发，避免同时触发多个 FFmpeg 封面抽取导致 CPU 抖动与接口体感卡顿。
   *   * 目前项目中 @Async 仅用于 VideoCoverService.regenerateCover。
   */
  @Bean(name = "taskExecutor")
  public ThreadPoolTaskExecutor taskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("ffmpeg-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}

