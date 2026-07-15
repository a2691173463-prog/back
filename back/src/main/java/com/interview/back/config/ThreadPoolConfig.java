package com.interview.back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean("aiInterviewExecutor")
    public Executor aiInterviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(50);
        // 缓冲队列容量
        executor.setQueueCapacity(200);
        // 线程空闲存活时间
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀
        executor.setThreadNamePrefix("ai-interview-pool-");
        // 拒绝策略：由调用者线程直接执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }
}
