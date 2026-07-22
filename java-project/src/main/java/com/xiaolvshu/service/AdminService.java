package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.dto.AdminListRequest;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.entity.Admin;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 管理员服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService extends ServiceImpl<AdminMapper, Admin> {
    
    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 获取管理员列表（分页）
     */
    public PageResult<Admin> getAdminList(AdminListRequest request) {
        // 验证排序字段
        List<String> allowedSortFields = Arrays.asList("username", "created_at");
        String sortField = request.getSortField();
        String sortOrder = request.getSortOrder();
        
        if (sortField == null || !allowedSortFields.contains(sortField)) {
            sortField = "created_at";
        }
        if (sortOrder == null || (!sortOrder.equalsIgnoreCase("ASC") && !sortOrder.equalsIgnoreCase("DESC"))) {
            sortOrder = "DESC";
        }
        
        // 构建查询条件
        LambdaQueryWrapper<Admin> queryWrapper = new LambdaQueryWrapper<>();
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            queryWrapper.like(Admin::getUsername, request.getUsername());
        }
        
        // 排序
        if ("username".equals(sortField)) {
            queryWrapper.orderBy(true, "ASC".equalsIgnoreCase(sortOrder), Admin::getUsername);
        } else {
            queryWrapper.orderBy(true, "ASC".equalsIgnoreCase(sortOrder), Admin::getCreatedAt);
        }
        
        // 分页查询
        Page<Admin> page = new Page<>(request.getPage(), request.getLimit());
        Page<Admin> result = adminMapper.selectPage(page, queryWrapper);
        
        // 移除密码字段
        result.getRecords().forEach(admin -> admin.setPassword(null));
        
        return new PageResult<>(
            result.getRecords(),
            request.getPage(),
            request.getLimit(),
            result.getTotal()
        );
    }
    
    /**
     * 创建管理员
     */
    @Transactional
    public Long createAdmin(String username, String password) {
        // 检查用户名是否已存在
        boolean exists = adminMapper.exists(
            new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, username)
        );
        
        if (exists) {
            throw new BusinessException("账号已存在");
        }
        
        // 创建管理员
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        adminMapper.insert(admin);
        
        log.info("创建管理员成功 - 用户名: {}", username);
        return admin.getId();
    }
    
    /**
     * 更新管理员密码
     */
    @Transactional
    public void updateAdminPassword(String username, String password) {
        // 检查管理员是否存在
        Admin admin = adminMapper.selectOne(
            new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, username)
        );
        
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        
        // 更新密码
        admin.setPassword(passwordEncoder.encode(password));
        adminMapper.updateById(admin);
        
        log.info("更新管理员密码成功 - 用户名: {}", username);
    }
    
    /**
     * 删除管理员
     */
    @Transactional
    public void deleteAdmin(String username) {
        // 检查管理员是否存在
        Admin admin = adminMapper.selectOne(
            new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, username)
        );
        
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        
        // 删除管理员
        adminMapper.deleteById(admin.getId());
        
        log.info("删除管理员成功 - 用户名: {}", username);
    }
    
    /**
     * 根据ID重置管理员密码
     */
    @Transactional
    public void resetAdminPasswordById(Long id, String password) {
        // 检查管理员是否存在
        Admin admin = adminMapper.selectById(id);
        
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        
        // 更新密码
        admin.setPassword(passwordEncoder.encode(password));
        adminMapper.updateById(admin);
        
        log.info("重置管理员密码成功 - ID: {}", id);
    }
}
