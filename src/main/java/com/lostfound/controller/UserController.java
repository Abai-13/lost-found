package com.lostfound.controller;

import com.lostfound.common.Result;
import com.lostfound.dto.LoginRequest;
import com.lostfound.dto.LoginResponse;
import com.lostfound.dto.RegisterRequest;
import com.lostfound.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 注册 */
    @PostMapping("/register")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.ok(Map.of("userId", userId));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse resp = userService.login(request.getUsername(), request.getPassword());
        return Result.ok("登录成功", resp);
    }
}
