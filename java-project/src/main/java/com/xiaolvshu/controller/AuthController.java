package com.xiaolvshu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.common.ResponseCode;
import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.AuthResponse;
import com.xiaolvshu.dto.LoginRequest;
import com.xiaolvshu.dto.RefreshTokenRequest;
import com.xiaolvshu.dto.RegisterRequest;
import com.xiaolvshu.dto.UserDTO;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.AuthService;
import com.xiaolvshu.dto.CaptchaResponse;
import com.xiaolvshu.service.CaptchaService;
import com.xiaolvshu.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final CaptchaService captchaService;
    private final UserService userService;
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, httpRequest);
        return Result.success("注册成功", response);
    }

    /**
     * 生成图形验证码
     */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> generateCaptcha() {
        CaptchaResponse response = captchaService.generateCaptcha();
        return Result.success("验证码生成成功", response);
    }

    /**
     * 检查用户ID是否存在
     */
    @GetMapping("/check-user-id")
    public Result<Map<String, Object>> checkUserId(@RequestParam("user_id") String userId) {
        if (userId == null) {
            return Result.error(ResponseCode.VALIDATION_ERROR, "用户ID不能为空");
        }
        boolean exists = userService.exists(new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        String message = exists ? "小旅书号已存在" : "小旅书号可用";
        Map<String, Object> data = new HashMap<>();
        data.put("isUnique", !exists);
        return Result.success(message, data);
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        return Result.success("登录成功", response);
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser() {
        UserDTO user = authService.getCurrentUser();
        return Result.success(user);
    }
    
    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<AuthResponse.TokensDTO> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        AuthResponse.TokensDTO tokens = authService.refreshToken(request.getRefreshToken(), httpRequest);
        return Result.success("令牌刷新成功", tokens);
    }
    
    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        // 从请求头中获取token
        String authHeader = httpRequest.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authService.logout(token);
        return Result.success("退出成功");
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }
}
