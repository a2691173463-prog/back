package com.interview.back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.back.common.Result;
import com.interview.back.entity.User;

public interface UserService extends IService<User> {
    Result<String> register(String username, String password);
    Result<String> login(String username, String password);
}
