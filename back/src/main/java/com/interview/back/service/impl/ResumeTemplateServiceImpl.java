package com.interview.back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.entity.ResumeTemplate;
import com.interview.back.mapper.ResumeTemplateMapper;
import com.interview.back.service.ResumeTemplateService;
import org.springframework.stereotype.Service;

@Service
public class ResumeTemplateServiceImpl extends ServiceImpl<ResumeTemplateMapper, ResumeTemplate> implements ResumeTemplateService {
}
