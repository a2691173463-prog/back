package com.interview.back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

@Configuration
public class ResumeParseAsyncConfig {

    @Bean("resumeParseExecutor")
    public ThreadPoolTaskExecutor resumeParseExecutor(
            @Value("${mq.resume.parse-executor-threads:3}") int threads,
            @Value("${mq.resume.parse-executor-queue-capacity:12}") int queueCapacity
    ) {
        if (threads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Resume parse executor threads and queue capacity must be positive");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("resume-parse-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        // Apply bounded backpressure instead of dropping a claimed message when
        // a continuation arrives while all workers and queue slots are occupied.
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("Resume parse executor is shutting down");
            }
            task.run();
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(130);
        return executor;
    }
}
