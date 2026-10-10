package com.interview.back.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeParseAsyncConfigTest {

    @Test
    void stoppedExecutorRejectsRatherThanSilentlyDroppingWork() {
        ThreadPoolTaskExecutor executor = new ResumeParseAsyncConfig()
                .resumeParseExecutor(1, 1);
        executor.initialize();
        executor.shutdown();

        assertThatThrownBy(() -> executor.execute(() -> {}))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void saturatedExecutorRunsContinuationOnCallerInsteadOfDroppingIt() throws Exception {
        ThreadPoolTaskExecutor executor = new ResumeParseAsyncConfig()
                .resumeParseExecutor(1, 1);
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});
            Thread caller = Thread.currentThread();
            AtomicBoolean ranOnCaller = new AtomicBoolean(false);

            executor.execute(() -> ranOnCaller.set(Thread.currentThread() == caller));

            assertThat(ranOnCaller).isTrue();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
