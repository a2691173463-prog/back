package com.interview.back.config;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.interview.back.dto.UserDto;
import com.interview.back.utils.RedisConstants;
import com.interview.back.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头或请求参数中的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // 兼容 EventSource (SSE) 连接无法传 header，从 url 参数获取
        if (StrUtil.isBlank(token)) {
            token = request.getParameter("token");
        }

        if (StrUtil.isBlank(token)) {
            return true;
        }

        // 2. 基于 token 获取 Redis 中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3. 判断用户是否存在
        if (userMap.isEmpty()) {
            return true;
        }

        // 4. 将查询到的 Hash 数据转为 UserDto 对象
        UserDto userDto = BeanUtil.fillBeanWithMap(userMap, new UserDto(), false);

        // 5. 存在，保存用户信息到 UserHolder
        UserHolder.saveUser(userDto);

        // 6. 刷新 token 有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 7. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，避免内存泄漏
        UserHolder.removeUser();
    }
}
