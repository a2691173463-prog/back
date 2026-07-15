package com.interview.back.service;

import com.interview.back.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EnergyService {

    private final StringRedisTemplate stringRedisTemplate;

    public EnergyService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取用户当前周期的最大能量上限
     * 基础额度：1000 EP
     * 今日已签到：1200 EP (送 200 EP)
     */
    public int getMaxEnergyLimit(Long userId) {
        try {
            LocalDate now = LocalDate.now();
            String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
            int offset = now.getDayOfMonth() - 1;
            Boolean hasSigned = stringRedisTemplate.opsForValue().getBit(key, offset);
            if (Boolean.TRUE.equals(hasSigned)) {
                return 1200;
            }
        } catch (Exception e) {
            // Fallback to base limit on error
        }
        return 1000;
    }

    /**
     * 获取用户当前周期的已消耗能量
     */
    public int getUsedEnergy(Long userId) {
        String key = RedisConstants.USER_ENERGY_USED_KEY + userId;
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val == null) {
            return 0;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取用户当前的剩余可用能量
     */
    public int getAvailableEnergy(Long userId) {
        int max = getMaxEnergyLimit(userId);
        int used = getUsedEnergy(userId);
        int available = max - used;
        return Math.max(available, 0);
    }

    /**
     * 校验用户是否有足够能量进行当前操作
     */
    public boolean hasEnoughEnergy(Long userId, int estimatedCost) {
        int available = getAvailableEnergy(userId);
        return available >= estimatedCost;
    }
}
