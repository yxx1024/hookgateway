package com.example.hookgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core pool size: threads to keep alive
        executor.setCorePoolSize(10);
        // Max pool size: max threads to allow
        executor.setMaxPoolSize(50);
        // Queue capacity: tasks to buffer
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}
