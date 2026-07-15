package com.interview.back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.back.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService extends IService<Resume> {
    Long uploadAndParse(MultipartFile file, Long userId);
}
