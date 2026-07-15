package com.interview.back.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透的查询方法 (缓存空值)
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 查询
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 判断是否是缓存的空值
        if (json != null) {
            return null;
        }

        // 4. 缓存未命中，查数据库
        R r = dbFallback.apply(id);
        // 5. 数据库中不存在，缓存空值以防穿透
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 数据库中存在，写入缓存
        this.set(key, r, time, timeUnit);
        return r;
    }

    /**
     * 利用互斥锁解决缓存击穿的查询方法
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 查询
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 判断是否是缓存的空值
        if (json != null) {
            return null;
        }

        // 4. 尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_QUESTION_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 未获取到锁，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }

            // 获取到锁，再次进行 Double-Check，防止在等待锁的过程中缓存已经被重建
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }

            // 查询数据库
            r = dbFallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 写入缓存
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockKey);
        }

        return r;
    }

    /**
     * 利用逻辑过期解决缓存击穿的查询方法
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 查询
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 未命中，直接返回空 (逻辑过期需要预热数据)
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 3. 命中，反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return r;
        }

        // 5. 已过期，尝试获取互斥锁进行异步重建
        String lockKey = RedisConstants.LOCK_QUESTION_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 获取锁成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R tmp = dbFallback.apply(id);
                    // 写入缓存并重设逻辑过期时间
                    this.setWithLogicExpire(key, tmp, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 6. 返回旧的（过期）数据
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_QUESTION_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
