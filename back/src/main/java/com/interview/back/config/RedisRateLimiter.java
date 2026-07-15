package com.interview.back.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // Lua script for sliding window rate limiting
    private static final String LUA_SCRIPT = 
        "local key = KEYS[1] " +
        "local limit = tonumber(ARGV[1]) " +
        "local window = tonumber(ARGV[2]) " +
        "local current_time = tonumber(ARGV[3]) " +
        "redis.call('ZREMRANGEBYSCORE', key, 0, current_time - window) " +
        "local count = redis.call('ZCARD', key) " +
        "if count >= limit then " +
        "   return 0 " +
        "else " +
        "   redis.call('ZADD', key, current_time, current_time) " +
        "   redis.call('EXPIRE', key, window) " +
        "   return 1 " +
        "end";

    /**
     * @param key Redis key (e.g. "rate_limit:ip:127.0.0.1")
     * @param limit Maximum requests allowed
     * @param windowInSeconds Time window in seconds
     * @return true if allowed, false if rejected (rate limited)
     */
    public boolean isAllowed(String key, int limit, int windowInSeconds) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        long currentTime = System.currentTimeMillis();
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), 
                String.valueOf(limit), String.valueOf(windowInSeconds), String.valueOf(currentTime));
        return result != null && result == 1L;
    }
}
