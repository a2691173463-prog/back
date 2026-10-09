package com.interview.back.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.entity.Resume;
import com.interview.back.entity.ResumeParseOutbox;
import com.interview.back.mapper.ResumeMapper;
import com.interview.back.mapper.ResumeParseOutboxMapper;
import com.interview.back.mq.ResumeOutboxPublisher;
import com.interview.back.mq.ResumeParseMessage;
import com.interview.back.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Date;

@Slf4j
@Service
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {

    private final ResumeParseOutboxMapper outboxMapper;
    private final ResumeOutboxPublisher outboxPublisher;
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads" + File.separator;

    public ResumeServiceImpl(
            ResumeParseOutboxMapper outboxMapper,
            ResumeOutboxPublisher outboxPublisher
    ) {
        this.outboxMapper = outboxMapper;
        this.outboxPublisher = outboxPublisher;
        FileUtil.mkdir(UPLOAD_DIR);
    }

    @Override
    @Transactional
    public Long uploadAndParse(MultipartFile file, Long userId) {
        File dest = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = FileUtil.extName(originalFilename);
            String newFileName = IdUtil.fastSimpleUUID() + "." + suffix;
            dest = new File(UPLOAD_DIR + newFileName);
            file.transferTo(dest);

            Resume resume = new Resume();
            resume.setUserId(userId);
            resume.setFileName(originalFilename);
            resume.setFileUrl(dest.getAbsolutePath());
            resume.setStatus(0);
            this.save(resume);

            ResumeParseMessage task = ResumeParseMessage.initial(resume.getId());
            ResumeParseOutbox outbox = new ResumeParseOutbox();
            outbox.setMessageId(task.messageId());
            outbox.setResumeId(resume.getId());
            outbox.setStatus(0);
            outbox.setRetryCount(0);
            outbox.setNextRetryTime(new Date());
            outboxMapper.insert(outbox);

            Long outboxId = outbox.getId();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                if (!outboxPublisher.publish(outboxId)) {
                                    log.warn(
                                            "Initial resume task publish deferred to outbox scheduler, outboxId={}",
                                            outboxId
                                    );
                                }
                            } catch (Exception e) {
                                log.error(
                                        "Initial resume task publish failed; outbox scheduler will retry, outboxId={}",
                                        outboxId,
                                        e
                                );
                            }
                        }
                    }
            );

            return resume.getId();
        } catch (Exception e) {
            if (dest != null && dest.exists()) {
                FileUtil.del(dest);
            }
            throw new RuntimeException("Failed to upload resume", e);
        }
    }
}
