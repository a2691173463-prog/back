package com.interview.back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.back.entity.UserProfile;
import java.util.List;

public interface UserProfileService extends IService<UserProfile> {
    void saveOrUpdateSkill(Long userId, String skillName, double score, String comment, Long interviewId);
    List<UserProfile> getTopKSkills(Long userId);
}
