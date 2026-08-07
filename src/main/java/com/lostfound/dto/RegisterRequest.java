package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度 6-30 位")
    private String password;

    @Size(max = 20, message = "昵称最长 20 位")
    private String nickname;

    @Size(max = 11, message = "手机号最长 11 位")
    private String phone;
}
