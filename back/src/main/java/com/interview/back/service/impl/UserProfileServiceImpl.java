package com.interview.back.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.entity.UserProfile;
import com.interview.back.mapper.UserProfileMapper;
import com.interview.back.service.UserProfileService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {

    @Override
    public void saveOrUpdateSkill(Long userId, String skillName, double newScore, String comment, Long interviewId) {
        UserProfile profile = this.getOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .eq(UserProfile::getSkillName, skillName));
        
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setSkillName(skillName);
            profile.setScore(BigDecimal.valueOf(newScore).setScale(2, RoundingMode.HALF_UP));
            profile.setComment(comment);
            profile.setSourceInterviewId(interviewId);
            profile.setLastUpdate(new Date());
            this.save(profile);
        } else {
            // 指数滑动平均平滑更新 (0.7 * 旧得分 + 0.3 * 新得分)
            double oldScore = profile.getScore().doubleValue();
            double smoothedScore = 0.7 * oldScore + 0.3 * newScore;
            
            profile.setScore(BigDecimal.valueOf(smoothedScore).setScale(2, RoundingMode.HALF_UP));
            profile.setComment(comment);
            profile.setSourceInterviewId(interviewId);
            profile.setLastUpdate(new Date());
            this.updateById(profile);
        }
    }

    @Override
    public List<UserProfile> getTopKSkills(Long userId) {
        List<UserProfile> allSkills = this.list(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        
        if (allSkills.isEmpty()) {
            return allSkills;
        }

        // 1. 评分最低的 3 项技能
        List<UserProfile> weakSkills = allSkills.stream()
                .sorted((a, b) -> a.getScore().compareTo(b.getScore()))
                .limit(3)
                .collect(Collectors.toList());

        // 2. 最近更新的 3 项技能 (且排除已作为薄弱的技能)
        List<UserProfile> recentSkills = allSkills.stream()
                .filter(s -> !weakSkills.contains(s))
                .sorted((a, b) -> b.getLastUpdate().compareTo(a.getLastUpdate()))
                .limit(3)
                .collect(Collectors.toList());

        // 3. 合并并返回
        return Stream.concat(weakSkills.stream(), recentSkills.stream())
                .collect(Collectors.toList());
    }
}
