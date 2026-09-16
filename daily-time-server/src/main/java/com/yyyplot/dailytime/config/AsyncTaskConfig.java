package com.yyyplot.dailytime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncTaskConfig {
    private static final int MEDIA_EXECUTOR_POOL_SIZE = 2;
    private static final int MEDIA_EXECUTOR_QUEUE_CAPACITY = 10;

    @Bean("mediaTaskExecutor")
    public ThreadPoolTaskExecutor mediaTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(MEDIA_EXECUTOR_POOL_SIZE);
        taskExecutor.setMaxPoolSize(MEDIA_EXECUTOR_POOL_SIZE);
        taskExecutor.setQueueCapacity(MEDIA_EXECUTOR_QUEUE_CAPACITY);
        taskExecutor.setThreadNamePrefix("daily-time-");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(false);
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        taskExecutor.initialize();
        return taskExecutor;
    }
}
