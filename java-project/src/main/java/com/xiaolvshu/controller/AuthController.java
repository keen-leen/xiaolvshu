package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.AuthResponse;
import com.xiaolvshu.dto.LoginRequest;
import com.xiaolvshu.dto.RegisterRequest;
import com.xiaolvshu.dto.UserDTO;
import com.xiaolvshu.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return Result.success("注册成功", response);
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
        Long currentUserId = UserContext.getUserId();
        UserDTO user = authService.getCurrentUser();
        return Result.success(user);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }
}
