package com.interview.back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.entity.ResumeDraft;
import com.interview.back.mapper.ResumeDraftMapper;
import com.interview.back.service.ResumeDraftService;
import org.springframework.stereotype.Service;

@Service
public class ResumeDraftServiceImpl extends ServiceImpl<ResumeDraftMapper, ResumeDraft>
        implements ResumeDraftService {
}
