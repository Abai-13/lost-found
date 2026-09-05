package com.lostfound.controller;

import com.lostfound.config.JwtUtil;
import com.lostfound.dto.LoginResponse;
import com.lostfound.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 接口测试（MockMvc）。
 * <p>
 * 和昨天的 JUnit 单测（测 Service）不同，这里测的是「HTTP 接口这一层」：
 * 只加载 Controller + 拦截器 + 全局异常处理器，不连 MySQL/Redis。
 * Service 用 @MockBean 换成假的，专注验证：路由对不对、参数校验有没有生效、返回体格式对不对。
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    /** MockMvc：模拟发 HTTP 请求的工具（Spring 注入） */
    @Autowired
    private MockMvc mockMvc;

    /** 把 UserService 换成假的 —— 不真去数据库注册/登录，只验证 Controller 怎么调它 */
    @MockBean
    private UserService userService;

    /**
     * JwtInterceptor 是 web 层组件，会被 @WebMvcTest 加载；
     * 但它依赖 JwtUtil（普通 @Component，不会被加载），所以必须 @MockBean 补上。
     * 否则 Spring 启动时报「找不到 JwtUtil bean」。
     */
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("注册：参数合法 → 200，返回新用户 id")
    void register_success() throws Exception {
        // 预设 Service 行为：无论传什么，都返回 userId=1
        when(userService.register(any())).thenReturn(1L);

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"baichen\",\"password\":\"123456\",\"nickname\":\"小白\"}"))
                .andExpect(status().isOk())                       // HTTP 状态码 200
                .andExpect(jsonPath("$.code").value(200))         // 返回体里的业务 code
                .andExpect(jsonPath("$.data.userId").value(1L));  // data.userId == 1
    }

    @Test
    @DisplayName("注册：用户名太短（@Valid 校验）→ 400，被全局异常处理器拦截")
    void register_usernameTooShort_shouldReturn400() throws Exception {
        // username 只有 2 位，违反 @Size(min=3)
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())               // HTTP 400
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists());       // 校验错误信息非空
    }

    @Test
    @DisplayName("登录：成功 → 200，返回 token 和用户名")
    void login_success() throws Exception {
        when(userService.login("baichen", "123456"))
                .thenReturn(new LoginResponse("fake-token", 1L, "baichen", "小白"));

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"baichen\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("fake-token"))
                .andExpect(jsonPath("$.data.username").value("baichen"));
    }

    @Test
    @DisplayName("登录：密码为空（@NotBlank）→ 400")
    void login_missingPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"baichen\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
