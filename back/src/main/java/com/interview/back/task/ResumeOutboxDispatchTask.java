package com.interview.back.task;

import com.interview.back.mapper.ResumeParseOutboxMapper;
import com.interview.back.mq.ResumeOutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "mq.resume.outbox-dispatch-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ResumeOutboxDispatchTask {

    private final ResumeParseOutboxMapper outboxMapper;
    private final ResumeOutboxPublisher outboxPublisher;

    public ResumeOutboxDispatchTask(
            ResumeParseOutboxMapper outboxMapper,
            ResumeOutboxPublisher outboxPublisher
    ) {
        this.outboxMapper = outboxMapper;
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(
            fixedDelayString = "${mq.resume.outbox-scan-interval-ms:10000}",
            initialDelayString = "${mq.resume.outbox-initial-delay-ms:10000}"
    )
    public void dispatchPending() {
        Date staleBefore = new Date(System.currentTimeMillis() - 60_000);
        int released = outboxMapper.releaseStaleClaims(staleBefore);
        if (released > 0) {
            log.warn("Released {} stale resume outbox claims", released);
        }

        List<Long> pendingIds = outboxMapper.selectPendingIds(50);
        for (Long pendingId : pendingIds) {
            outboxPublisher.publish(pendingId);
        }
    }
}
