package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端审核管理控制器
 */
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditService auditService;
    private final UserService userService;

    /**
     * 分页查询审核列表
     */
    @GetMapping
    public AdminResult<?> getAuditList(AdminAuditQueryDTO queryDTO) {
        Page<Audit> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Audit> wrapper = new LambdaQueryWrapper<>();
        
        // 用户ID搜索
        if (queryDTO.getUserId() != null) {
            wrapper.eq(Audit::getUserId, queryDTO.getUserId());
        }
        
        // 审核类型搜索
        if (queryDTO.getType() != null) {
            wrapper.eq(Audit::getType, queryDTO.getType());
        }
        
        // 审核状态搜索
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Audit::getStatus, queryDTO.getStatus());
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
            wrapper.in(Audit::getUserId, userIds);
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "created_at", "audit_time", "status");
        String sortField = queryDTO.getSortBy();
        if (sortField != null && allowedSortFields.contains(sortField)) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (sortField) {
                case "id":
                    wrapper.orderBy(true, isAsc, Audit::getId);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Audit::getCreatedAt);
                    break;
                case "audit_time":
                    wrapper.orderBy(true, isAsc, Audit::getAuditTime);
                    break;
                case "status":
                    wrapper.orderBy(true, isAsc, Audit::getStatus);
                    break;
            }
        } else {
            wrapper.orderByDesc(Audit::getCreatedAt);
        }
        
        IPage<Audit> result = auditService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminAuditDTO> auditDTOs = new ArrayList<>();
        for (Audit audit : result.getRecords()) {
            AdminAuditDTO dto = new AdminAuditDTO();
            dto.setId(audit.getId());
            dto.setUserId(audit.getUserId());
            dto.setType(audit.getType());
            dto.setContent(audit.getContent());
            dto.setStatus(audit.getStatus());
            dto.setCreatedAt(audit.getCreatedAt());
            dto.setAuditTime(audit.getAuditTime());
            
            // 获取用户信息
            User user = userService.getById(audit.getUserId());
            if (user != null) {
                dto.setUserDisplayId(user.getUserId());
                dto.setNickname(user.getNickname());
                dto.setAvatar(user.getAvatar());
            }
            
            auditDTOs.add(dto);
        }
        
        return AdminResult.success(auditDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个审核详情
     */
    @GetMapping("/{id}")
    public AdminResult<AdminAuditDTO> getAuditById(@PathVariable Long id) {
        Audit audit = auditService.getById(id);
        if (audit == null) {
            return AdminResult.notFound("认证记录不存在");
        }
        
        AdminAuditDTO dto = new AdminAuditDTO();
        dto.setId(audit.getId());
        dto.setUserId(audit.getUserId());
        dto.setType(audit.getType());
        dto.setContent(audit.getContent());
        dto.setStatus(audit.getStatus());
        dto.setCreatedAt(audit.getCreatedAt());
        dto.setAuditTime(audit.getAuditTime());
        
        // 获取用户信息
        User user = userService.getById(audit.getUserId());
        if (user != null) {
            dto.setUserDisplayId(user.getUserId());
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
        }
        
        return AdminResult.success("获取认证记录成功", dto);
    }

    /**
     * 创建审核记录
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createAudit(@RequestBody Audit audit) {
        // 验证必填字段
        if (audit.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        if (audit.getType() == null) {
            return AdminResult.badRequest("缺少必填字段: type");
        }
        if (audit.getContent() == null || audit.getContent().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: content");
        }
        
        // 检查用户是否存在
        User user = userService.getById(audit.getUserId());
        if (user == null) {
            return AdminResult.badRequest("用户不存在");
        }
        
        audit.setStatus(0); // 默认待审核
        auditService.save(audit);
        
        return AdminResult.success("认证记录创建成功", Map.of("id", audit.getId()));
    }

    /**
     * 更新审核记录
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateAudit(@PathVariable Long id, @RequestBody Audit audit) {
        Audit existingAudit = auditService.getById(id);
        if (existingAudit == null) {
            return AdminResult.notFound("认证记录不存在");
        }
        
        if (audit.getType() != null) existingAudit.setType(audit.getType());
        if (audit.getContent() != null) existingAudit.setContent(audit.getContent());
        if (audit.getStatus() != null) existingAudit.setStatus(audit.getStatus());
        if (audit.getAuditTime() != null) existingAudit.setAuditTime(audit.getAuditTime());
        
        auditService.updateById(existingAudit);
        return AdminResult.success("认证记录更新成功");
    }

    /**
     * 删除单个审核记录
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteAudit(@PathVariable Long id) {
        Audit audit = auditService.getById(id);
        if (audit == null) {
            return AdminResult.notFound("认证记录不存在");
        }
        
        auditService.removeById(id);
        return AdminResult.success("认证记录删除成功");
    }

    /**
     * 批量删除审核记录
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteAudits(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = auditService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条认证记录", Map.of("deletedCount", deletedCount));
    }

    /**
     * 审核通过
     */
    @PutMapping("/{id}/approve")
    @Transactional
    public AdminResult<Void> approveAudit(@PathVariable Long id) {
        Audit audit = auditService.getById(id);
        if (audit == null) {
            return AdminResult.notFound("审核记录不存在");
        }
        
        // 更新审核状态为通过
        audit.setStatus(1);
        audit.setAuditTime(LocalDateTime.now());
        auditService.updateById(audit);
        
        // 根据认证类型更新用户的verified字段
        // type: 1-官方认证, 2-个人认证
        Integer verifiedValue = audit.getType() == 1 ? 1 : (audit.getType() == 2 ? 2 : 0);
        User user = userService.getById(audit.getUserId());
        if (user != null) {
            user.setVerified(verifiedValue);
            userService.updateById(user);
        }
        
        return AdminResult.success("审核通过成功");
    }

    /**
     * 拒绝申请
     */
    @PutMapping("/{id}/reject")
    @Transactional
    public AdminResult<Void> rejectAudit(@PathVariable Long id) {
        Audit audit = auditService.getById(id);
        if (audit == null) {
            return AdminResult.notFound("审核记录不存在");
        }
        
        // 更新审核状态为拒绝
        audit.setStatus(2);
        audit.setAuditTime(LocalDateTime.now());
        auditService.updateById(audit);
        
        // 拒绝认证申请时，将用户的verified字段设置为0（未认证）
        User user = userService.getById(audit.getUserId());
        if (user != null) {
            user.setVerified(0);
            userService.updateById(user);
        }
        
        return AdminResult.success("拒绝申请成功");
    }
}
