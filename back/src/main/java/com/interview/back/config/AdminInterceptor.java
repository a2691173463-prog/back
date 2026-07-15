package com.interview.back.config;

import com.interview.back.dto.UserDto;
import com.interview.back.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDto user = UserHolder.getUser();
        if (user == null || !"admin".equals(user.getRole())) {
            response.setStatus(403);
            return false;
        }
        return true;
    }
}
