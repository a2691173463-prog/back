package com.interview.back.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.entity.Resume;
import com.interview.back.mapper.ResumeMapper;
import com.interview.back.mq.ResumeMqProducer;
import com.interview.back.service.ResumeService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Service
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {

    private final ResumeMqProducer resumeMqProducer;
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads" + File.separator;

    public ResumeServiceImpl(ResumeMqProducer resumeMqProducer) {
        this.resumeMqProducer = resumeMqProducer;
        FileUtil.mkdir(UPLOAD_DIR);
    }

    @Override
    public Long uploadAndParse(MultipartFile file, Long userId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = FileUtil.extName(originalFilename);
            String newFileName = IdUtil.fastSimpleUUID() + "." + suffix;
            File dest = new File(UPLOAD_DIR + newFileName);
            file.transferTo(dest);

            Resume resume = new Resume();
            resume.setUserId(userId);
            resume.setFileName(originalFilename);
            resume.setFileUrl(dest.getAbsolutePath());
            resume.setStatus(0);
            this.save(resume);

            resumeMqProducer.sendParseTask(resume.getId());

            return resume.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload resume", e);
        }
    }
}
