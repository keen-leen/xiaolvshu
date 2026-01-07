package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端会话管理控制器
 */
@RestController
@RequestMapping("/admin/sessions")
@RequiredArgsConstructor
public class AdminSessionController {

    private final UserSessionService userSessionService;
    private final UserService userService;

    /**
     * 分页查询会话列表
     */
    @GetMapping
    public AdminResult<?> getSessionList(AdminSessionQueryDTO queryDTO) {
        Page<UserSession> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        
        // 是否激活搜索
        if (queryDTO.getIsActive() != null) {
            wrapper.eq(UserSession::getIsActive, queryDTO.getIsActive());
        }
        
        // 用户显示ID搜索
        if (queryDTO.getUserDisplayId() != null && !queryDTO.getUserDisplayId().trim().isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getUserId, queryDTO.getUserDisplayId().trim());
            List<User> users = userService.list(userWrapper);
            if (users.isEmpty()) {
                return AdminResult.success(new ArrayList<>(), 0L, queryDTO.getPage(), queryDTO.getLimit());
            }
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            wrapper.in(UserSession::getUserId, userIds);
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "is_active", "expires_at", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, UserSession::getId);
                    break;
                case "is_active":
                    wrapper.orderBy(true, isAsc, UserSession::getIsActive);
                    break;
                case "expires_at":
                    wrapper.orderBy(true, isAsc, UserSession::getExpiresAt);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, UserSession::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(UserSession::getCreatedAt);
        }
        
        IPage<UserSession> result = userSessionService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminSessionDTO> sessionDTOs = new ArrayList<>();
        for (UserSession session : result.getRecords()) {
            AdminSessionDTO dto = new AdminSessionDTO();
            dto.setId(session.getId());
            dto.setUserId(session.getUserId());
            dto.setRefreshToken(session.getRefreshToken());
            dto.setUserAgent(session.getUserAgent());
            dto.setIsActive(session.getIsActive());
            dto.setExpiresAt(session.getExpiresAt());
            dto.setCreatedAt(session.getCreatedAt());
            
            // 获取用户信息
            User user = userService.getById(session.getUserId());
            if (user != null) {
                dto.setNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId() != null ? user.getUserId() : "user" + String.format("%03d", user.getId()));
            }
            
            sessionDTOs.add(dto);
        }
        
        return AdminResult.success(sessionDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/{id}")
    public AdminResult<UserSession> getSessionById(@PathVariable Long id) {
        UserSession session = userSessionService.getById(id);
        if (session == null) {
            return AdminResult.notFound("会话不存在");
        }
        return AdminResult.success("操作成功", session);
    }

    /**
     * 创建会话
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createSession(@RequestBody UserSession session) {
        // 验证必填字段
        if (session.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        
        // 检查用户是否存在
        User user = userService.getById(session.getUserId());
        if (user == null) {
            return AdminResult.badRequest("用户不存在");
        }
        
        // 生成refresh_token
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        session.setRefreshToken(sb.toString());
        
        // 设置过期时间（30天）
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        
        // 设置默认值
        if (session.getUserAgent() == null) session.setUserAgent("");
        if (session.getIsActive() == null) session.setIsActive(1);
        
        userSessionService.save(session);
        
        return AdminResult.success("会话创建成功", Map.of("id", session.getId()));
    }

    /**
     * 更新会话
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateSession(@PathVariable Long id, @RequestBody UserSession session) {
        UserSession existingSession = userSessionService.getById(id);
        if (existingSession == null) {
            return AdminResult.notFound("会话不存在");
        }
        
        if (session.getUserAgent() != null) existingSession.setUserAgent(session.getUserAgent());
        if (session.getIsActive() != null) existingSession.setIsActive(session.getIsActive());
        
        userSessionService.updateById(existingSession);
        return AdminResult.success("会话更新成功");
    }

    /**
     * 删除单个会话
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteSession(@PathVariable Long id) {
        UserSession session = userSessionService.getById(id);
        if (session == null) {
            return AdminResult.notFound("会话不存在");
        }
        
        userSessionService.removeById(id);
        return AdminResult.success("会话删除成功");
    }

    /**
     * 批量删除会话
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteSessions(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = userSessionService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条会话", Map.of("deletedCount", deletedCount));
    }
}
