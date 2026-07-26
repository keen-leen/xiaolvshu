package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.AdminResult;
import com.xiaolvshu.dto.admin.AdminUserQueryDTO;
import com.xiaolvshu.dto.admin.BatchDeleteDTO;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 管理端用户管理控制器
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public AdminResult<?> getUserList(AdminUserQueryDTO queryDTO) {
        Page<User> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 搜索条件
        if (queryDTO.getUserId() != null && !queryDTO.getUserId().trim().isEmpty()) {
            wrapper.like(User::getUserId, queryDTO.getUserId().trim());
        }
        if (queryDTO.getNickname() != null && !queryDTO.getNickname().trim().isEmpty()) {
            wrapper.like(User::getNickname, queryDTO.getNickname().trim());
        }
        if (queryDTO.getLocation() != null && !queryDTO.getLocation().trim().isEmpty()) {
            wrapper.like(User::getLocation, queryDTO.getLocation().trim());
        }
        if (queryDTO.getIsActive() != null) {
            wrapper.eq(User::getIsActive, queryDTO.getIsActive());
        }

        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "fans_count", "like_count", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, User::getId);
                    break;
                case "fans_count":
                    wrapper.orderBy(true, isAsc, User::getFansCount);
                    break;
                case "like_count":
                    wrapper.orderBy(true, isAsc, User::getLikeCount);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, User::getCreatedAt);
                    break;
            }
        } else {
            // 默认按创建时间倒序
            wrapper.orderByDesc(User::getCreatedAt);
        }

        IPage<User> result = userService.page(pageParam, wrapper);
        return AdminResult.success(result.getRecords(), result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个用户详情
     */
    @GetMapping("/{id}")
    public AdminResult<User> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return AdminResult.notFound("用户不存在");
        }
        return AdminResult.success("操作成功", user);
    }

    /**
     * 创建用户
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createUser(@RequestBody User user) {
        // 验证必填字段
        if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        if (user.getNickname() == null || user.getNickname().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: nickname");
        }

        // 检查user_id是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUserId, user.getUserId());
        if (userService.count(wrapper) > 0) {
            return AdminResult.conflict("user_id已存在");
        }

        // 普通用户登录同样使用 PasswordEncoder.matches；后台创建路径必须写入 BCrypt，
        // 不能再生成与运行时认证协议不兼容的 SHA-256 值。
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode("123456"));
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // 设置默认值
        if (user.getAvatar() == null) user.setAvatar("");
        if (user.getBio() == null) user.setBio("");
        if (user.getLocation() == null) user.setLocation("");
        if (user.getIsActive() == null) user.setIsActive(1);

        userService.save(user);
        return AdminResult.success("用户创建成功", Map.of("id", user.getId()));
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateUser(@PathVariable Long id, @RequestBody User user) {
        User existingUser = userService.getById(id);
        if (existingUser == null) {
            return AdminResult.notFound("用户不存在");
        }

        // 更新允许的字段
        if (user.getUserId() != null && !user.getUserId().trim().isEmpty()) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUserId, user.getUserId()).ne(User::getId, id);
            if (userService.count(wrapper) > 0) {
                return AdminResult.conflict("user_id已存在");
            }
            existingUser.setUserId(user.getUserId());
        }
        if (user.getNickname() != null) existingUser.setNickname(user.getNickname());
        if (user.getAvatar() != null) existingUser.setAvatar(user.getAvatar());
        if (user.getBio() != null) existingUser.setBio(user.getBio());
        if (user.getLocation() != null) existingUser.setLocation(user.getLocation());
        if (user.getIsActive() != null) existingUser.setIsActive(user.getIsActive());
        if (user.getGender() != null) existingUser.setGender(user.getGender());
        if (user.getZodiacSign() != null) existingUser.setZodiacSign(user.getZodiacSign());
        if (user.getMbti() != null) existingUser.setMbti(user.getMbti());
        if (user.getEducation() != null) existingUser.setEducation(user.getEducation());
        if (user.getMajor() != null) existingUser.setMajor(user.getMajor());
        if (user.getInterests() != null) existingUser.setInterests(user.getInterests());
        if (user.getVerified() != null) existingUser.setVerified(user.getVerified());

        userService.updateById(existingUser);
        return AdminResult.success("用户更新成功");
    }

    /**
     * 删除单个用户
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return AdminResult.notFound("用户不存在");
        }
        userService.removeById(id);
        return AdminResult.success("用户删除成功");
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteUsers(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = userService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "个用户", Map.of("deletedCount", deletedCount));
    }
}
