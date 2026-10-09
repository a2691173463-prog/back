package com.interview.back.mq;

import com.interview.back.entity.Resume;
import com.interview.back.entity.ResumeParseOutbox;
import com.interview.back.mapper.ResumeMapper;
import com.interview.back.mapper.ResumeParseOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Component
public class ResumeOutboxPublisher {

    private final ResumeParseOutboxMapper outboxMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeMqProducer resumeMqProducer;

    @Value("${mq.resume.outbox-max-attempts:10}")
    private int outboxMaxAttempts;

    public ResumeOutboxPublisher(
            ResumeParseOutboxMapper outboxMapper,
            ResumeMapper resumeMapper,
            ResumeMqProducer resumeMqProducer
    ) {
        this.outboxMapper = outboxMapper;
        this.resumeMapper = resumeMapper;
        this.resumeMqProducer = resumeMqProducer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(Long outboxId) {
        if (outboxMapper.claim(outboxId) == 0) {
            return false;
        }

        ResumeParseOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null) {
            return false;
        }

        try {
            long createdAt = outbox.getCreateTime() == null
                    ? System.currentTimeMillis()
                    : outbox.getCreateTime().getTime();
            resumeMqProducer.sendParseTask(new ResumeParseMessage(
                    outbox.getMessageId(),
                    outbox.getResumeId(),
                    0,
                    createdAt
            ));
            outboxMapper.markSent(outboxId);
            log.info(
                    "Resume outbox published, outboxId={}, messageId={}, resumeId={}",
                    outboxId,
                    outbox.getMessageId(),
                    outbox.getResumeId()
            );
            return true;
        } catch (Exception e) {
            int retryCount = outbox.getRetryCount() + 1;
            boolean exhausted = retryCount >= Math.max(1, outboxMaxAttempts);
            long backoffSeconds = Math.min(60L, 1L << Math.min(retryCount, 6));
            String error = abbreviate(e.getMessage(), 1000);
            outboxMapper.markPublishFailure(
                    outboxId,
                    exhausted ? 2 : 0,
                    retryCount,
                    new Date(System.currentTimeMillis() + backoffSeconds * 1000),
                    error
            );
            if (exhausted) {
                Resume resume = resumeMapper.selectById(outbox.getResumeId());
                if (resume != null && Integer.valueOf(0).equals(resume.getStatus())) {
                    resume.setStatus(2);
                    resumeMapper.updateById(resume);
                }
            }
            log.error(
                    "Resume outbox publish failed, outboxId={}, resumeId={}, retryCount={}",
                    outboxId,
                    outbox.getResumeId(),
                    retryCount,
                    e
            );
            return false;
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
