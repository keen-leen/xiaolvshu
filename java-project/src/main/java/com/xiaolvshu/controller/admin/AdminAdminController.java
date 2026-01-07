package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.Admin;
import com.xiaolvshu.service.AdminService;
import com.xiaolvshu.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端管理员管理控制器
 */
@RestController
@RequestMapping("/admin/admins")
@RequiredArgsConstructor
public class AdminAdminController {

    private final AdminService adminService;

    /**
     * 分页查询管理员列表
     */
    @GetMapping
    public AdminResult<?> getAdminList(AdminAdminQueryDTO queryDTO) {
        Page<Admin> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        
        // 用户名模糊搜索
        if (queryDTO.getUsername() != null && !queryDTO.getUsername().trim().isEmpty()) {
            wrapper.like(Admin::getUsername, queryDTO.getUsername().trim());
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("username", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "username":
                    wrapper.orderBy(true, isAsc, Admin::getUsername);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Admin::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Admin::getCreatedAt);
        }
        
        IPage<Admin> result = adminService.page(pageParam, wrapper);
        
        // 隐藏密码
        for (Admin admin : result.getRecords()) {
            admin.setPassword(null);
        }
        
        return AdminResult.success(result.getRecords(), result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个管理员详情
     */
    @GetMapping("/{id}")
    public AdminResult<Admin> getAdminById(@PathVariable Long id) {
        Admin admin = adminService.getById(id);
        if (admin == null) {
            return AdminResult.notFound("管理员不存在");
        }
        admin.setPassword(null); // 隐藏密码
        return AdminResult.success("操作成功", admin);
    }

    /**
     * 创建管理员
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createAdmin(@RequestBody Admin admin) {
        // 验证必填字段
        if (admin.getUsername() == null || admin.getUsername().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: username");
        }
        if (admin.getPassword() == null || admin.getPassword().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: password");
        }
        
        // 检查用户名是否已存在
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUsername, admin.getUsername().trim());
        if (adminService.count(wrapper) > 0) {
            return AdminResult.conflict("用户名已存在");
        }
        
        admin.setUsername(admin.getUsername().trim());
        // 对密码进行哈希加密
        admin.setPassword(PasswordUtil.sha256(admin.getPassword()));
        
        adminService.save(admin);
        return AdminResult.success("管理员创建成功", Map.of("id", admin.getId()));
    }

    /**
     * 更新管理员（通过ID）
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateAdmin(@PathVariable Long id, @RequestBody Admin admin) {
        Admin existingAdmin = adminService.getById(id);
        if (existingAdmin == null) {
            return AdminResult.notFound("管理员不存在");
        }
        
        // 如果更新密码，进行哈希加密
        if (admin.getPassword() != null && !admin.getPassword().trim().isEmpty()) {
            existingAdmin.setPassword(PasswordUtil.sha256(admin.getPassword()));
        }
        
        adminService.updateById(existingAdmin);
        return AdminResult.success("管理员更新成功");
    }

    /**
     * 删除单个管理员
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteAdmin(@PathVariable Long id) {
        Admin admin = adminService.getById(id);
        if (admin == null) {
            return AdminResult.notFound("管理员不存在");
        }
        
        adminService.removeById(id);
        return AdminResult.success("管理员删除成功");
    }

    /**
     * 批量删除管理员
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteAdmins(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = adminService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "个管理员", Map.of("deletedCount", deletedCount));
    }
}
