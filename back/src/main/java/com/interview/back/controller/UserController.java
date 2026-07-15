package com.interview.back.controller;

import cn.hutool.core.util.StrUtil;
import com.interview.back.common.Result;
import com.interview.back.dto.UserDto;
import com.interview.back.service.UserService;
import com.interview.back.utils.RedisConstants;
import com.interview.back.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.interview.back.service.EnergyService energyService;

    public UserController(UserService userService, StringRedisTemplate stringRedisTemplate, com.interview.back.service.EnergyService energyService) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.energyService = energyService;
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        return userService.register(username, password);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        return userService.login(username, password);
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            // Remove prefix if present (e.g. "Bearer ")
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        }
        return Result.success("登出成功");
    }

    @GetMapping("/me")
    public Result<UserDto> me() {
        UserDto user = UserHolder.getUser();
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(user);
    }

    @PostMapping("/sign")
    public Result<String> sign() {
        Long userId = UserHolder.getUser().getId();
        java.time.LocalDate now = java.time.LocalDate.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        int offset = now.getDayOfMonth() - 1;

        Boolean hasSigned = stringRedisTemplate.opsForValue().getBit(key, offset);
        if (Boolean.TRUE.equals(hasSigned)) {
            return Result.error(400, "您今日已签到过了哦，明天再来吧！");
        }

        stringRedisTemplate.opsForValue().setBit(key, offset, true);
        return Result.success("签到成功！已为您免费赠送今日 200 EP 算力能量上限");
    }

    @GetMapping("/sign/status")
    public Result<Map<String, Object>> getSignStatus() {
        Long userId = UserHolder.getUser().getId();
        java.time.LocalDate now = java.time.LocalDate.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        int offset = dayOfMonth - 1;

        // 1. 今日是否签到
        Boolean hasSignedToday = stringRedisTemplate.opsForValue().getBit(key, offset);
        if (hasSignedToday == null) {
            hasSignedToday = false;
        }

        // 2. 计算本月截止到今天的连续签到天数（位运算）
        int continuousSignDays = 0;
        try {
            java.util.List<Long> result = stringRedisTemplate.opsForValue().bitField(
                    key,
                    org.springframework.data.redis.connection.BitFieldSubCommands.create()
                            .get(org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                            .valueAt(0)
            );

            if (result != null && !result.isEmpty() && result.get(0) != null) {
                long num = result.get(0);
                while (true) {
                    if ((num & 1) == 1) {
                        continuousSignDays++;
                    } else {
                        break;
                    }
                    num >>>= 1;
                }
            }
        } catch (Exception e) {
            // Ignore bitfield calculation errors
        }

        // 3. 获取能量消耗与剩余额度详情
        int maxEnergy = energyService.getMaxEnergyLimit(userId);
        int usedEnergy = energyService.getUsedEnergy(userId);

        Map<String, Object> map = new java.util.HashMap<>();
        map.put("hasSignedToday", hasSignedToday);
        map.put("continuousSignDays", continuousSignDays);
        map.put("usedEnergy", usedEnergy);
        map.put("maxEnergy", maxEnergy);

        return Result.success(map);
    }
}
