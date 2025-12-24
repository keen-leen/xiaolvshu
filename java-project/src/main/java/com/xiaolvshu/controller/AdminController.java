package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.entity.Admin;
import com.xiaolvshu.service.AdminService;
import com.xiaolvshu.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员控制器
 */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final AuthService authService;
    private final AdminService adminService;
    
    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Result<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminAuthResponse response = authService.adminLogin(request);
        return Result.success("登录成功", response);
    }
    
    /**
     * 获取当前管理员信息
     */
    @GetMapping("/me")
    public Result<AdminDTO> getCurrentAdmin() {
        AdminDTO admin = authService.getCurrentAdmin();
        return Result.success(admin);
    }
    
    /**
     * 获取管理员列表
     */
    @GetMapping("/admins")
    public Result<PageResult<Admin>> getAdminList(AdminListRequest request) {
        PageResult<Admin> result = adminService.getAdminList(request);
        return Result.success("success", result);
    }
    
    /**
     * 创建管理员
     */
    @PostMapping("/admins")
    public Result<Long> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        Long id = adminService.createAdmin(request.getUsername(), request.getPassword());
        return Result.success("创建管理员成功", id);
    }
    
    /**
     * 更新管理员信息（通过用户名）
     */
    @PutMapping("/admins/{username}")
    public Result<Void> updateAdmin(
            @PathVariable String username,
            @Valid @RequestBody UpdateAdminRequest request) {
        adminService.updateAdminPassword(username, request.getPassword());
        return Result.success("更新管理员信息成功");
    }
    
    /**
     * 删除管理员（通过用户名）
     */
    @DeleteMapping("/admins/{username}")
    public Result<Void> deleteAdmin(@PathVariable String username) {
        adminService.deleteAdmin(username);
        return Result.success("删除管理员成功");
    }
    
    /**
     * 重置管理员密码（通过ID）
     */
    @PutMapping("/admins/{id}/password")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAdminRequest request) {
        adminService.resetAdminPasswordById(id, request.getPassword());
        return Result.success("重置密码成功");
    }
}
