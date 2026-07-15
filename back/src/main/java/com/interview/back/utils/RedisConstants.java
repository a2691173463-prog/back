package com.interview.back.utils;

public class RedisConstants {
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L; // 30 minutes

    // 分布式锁与防刷 Key
    public static final String LOCK_INTERVIEW_INIT_KEY = "lock:interview:init:user:";
    public static final String LOCK_INTERVIEW_END_KEY = "lock:interview:end:";

    // 签到与算力能量槽 Key
    public static final String USER_SIGN_KEY = "user:sign:";
    public static final String USER_ENERGY_USED_KEY = "user:energy:used:";

    // 题库与模板缓存 Key
    public static final String CACHE_QUESTION_KEY = "cache:question:";
    public static final Long CACHE_QUESTION_TTL = 30L; // 30 minutes
    public static final String LOCK_QUESTION_KEY = "lock:question:";
    public static final Long LOCK_QUESTION_TTL = 10L; // 10 seconds
    public static final String QUESTION_VIEWS_KEY = "question:views:";
    
    // 排行榜 Key
    public static final String RANK_QUESTION_INTERVIEW = "rank:question:interview";

    // 缓存穿透空值 TTL
    public static final String CACHE_NULL_KEY = "cache:null:";
    public static final Long CACHE_NULL_TTL = 2L; // 2 minutes
}

