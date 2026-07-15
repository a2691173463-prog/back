package com.interview.back.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    public WebConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 拦截所有请求，刷新 token 缓存的有效期 (order 0)
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 2. 拦截需要登录校验的接口，放行登录和注册接口 (order 1)
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register",
                        "/api/questions/list",
                        "/api/questions/{\\d+}",
                        "/api/questions/hot",
                        "/api/templates/list"
                )
                .order(1);

        // 3. 拦截管理员专有接口 (order 2)
        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/api/admin/**")
                .order(2);
    }
}

