package com.lostfound.service;

import com.lostfound.dto.LoginResponse;
import com.lostfound.dto.RegisterRequest;

public interface UserService {

    /** 注册，返回新用户 ID */
    Long register(RegisterRequest request);

    /** 登录，返回 token 等信息 */
    LoginResponse login(String username, String password);

    /** 根据 ID 查询用户名等信息（用于展示发帖人） */
    String getNicknameById(Long userId);
}
