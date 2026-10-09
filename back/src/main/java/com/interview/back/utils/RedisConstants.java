package com.interview.back.utils;

public class RedisConstants {
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L; // 30 minutes

    // 分布式锁与防刷 Key
    public static final String LOCK_INTERVIEW_INIT_KEY = "lock:interview:init:user:";
    public static final String LOCK_INTERVIEW_END_KEY = "lock:interview:end:";
    public static final String LOCK_CACHE_KEY = "lock:cache:";
    public static final Long LOCK_CACHE_TTL = 10L;

    // 签到与算力能量槽 Key
    public static final String USER_SIGN_KEY = "user:sign:";
    public static final String USER_ENERGY_USED_KEY = "user:energy:used:";

    // 缓存穿透空值 TTL
    public static final String CACHE_NULL_KEY = "cache:null:";
    public static final Long CACHE_NULL_TTL = 2L; // 2 minutes
}
