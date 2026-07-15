package com.interview.back.config;

import com.interview.back.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 判断是否需要拦截（ThreadLocal 中是否有用户）
        if (UserHolder.getUser() == null) {
            // 没有用户，进行拦截，并设置状态码为 401
            response.setStatus(401);
            return false;
        }
        // 有用户，放行
        return true;
    }
}
