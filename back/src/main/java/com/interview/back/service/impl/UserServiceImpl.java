package com.interview.back.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.common.Result;
import com.interview.back.dto.UserDto;
import com.interview.back.entity.User;
import com.interview.back.mapper.UserMapper;
import com.interview.back.service.UserService;
import com.interview.back.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result<String> register(String username, String password) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Result.error(400, "用户名和密码不能为空");
        }

        // 校验用户名是否存在
        User existingUser = query().eq("username", username).one();
        if (existingUser != null) {
            return Result.error(400, "用户名已存在");
        }

        // 加密密码并保存
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword);
        this.save(user);

        return Result.success("注册成功");
    }

    @Override
    public Result<String> login(String username, String password) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Result.error(400, "用户名和密码不能为空");
        }

        // 查询用户
        User user = query().eq("username", username).one();
        if (user == null) {
            return Result.error(400, "用户名或密码错误");
        }

        // 校验密码
        if (!BCrypt.checkpw(password, user.getPassword())) {
            return Result.error(400, "用户名或密码错误");
        }

        // 登录成功，生成 token
        String token = UUID.randomUUID().toString(true);

        // 复制属性到 UserDto
        UserDto userDto = BeanUtil.copyProperties(user, UserDto.class);

        // 转换为 Map，并将所有属性转为 String（符合 StringRedisTemplate 接口要求）
        Map<String, Object> userMap = BeanUtil.beanToMap(userDto, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 保存到 Redis
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 返回 token
        return Result.success(token);
    }
}
