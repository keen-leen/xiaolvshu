package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端点赞管理控制器
 */
@RestController
@RequestMapping("/admin/likes")
@RequiredArgsConstructor
public class AdminLikeController {

    private final LikeService likeService;
    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;

    /**
     * 分页查询点赞列表
     */
    @GetMapping
    public AdminResult<?> getLikeList(AdminLikeQueryDTO queryDTO) {
        Page<Like> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        
        // 目标类型搜索
        if (queryDTO.getTargetType() != null) {
            wrapper.eq(Like::getTargetType, queryDTO.getTargetType());
        }
        
        // 目标ID搜索
        if (queryDTO.getTargetId() != null) {
            wrapper.eq(Like::getTargetId, queryDTO.getTargetId());
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
            wrapper.in(Like::getUserId, userIds);
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "user_id", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Like::getId);
                    break;
                case "user_id":
                    wrapper.orderBy(true, isAsc, Like::getUserId);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Like::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Like::getCreatedAt);
        }
        
        IPage<Like> result = likeService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminLikeDTO> likeDTOs = new ArrayList<>();
        for (Like like : result.getRecords()) {
            AdminLikeDTO dto = new AdminLikeDTO();
            dto.setId(like.getId());
            dto.setUserId(like.getUserId());
            dto.setTargetType(like.getTargetType());
            dto.setTargetId(like.getTargetId());
            dto.setCreatedAt(like.getCreatedAt());
            
            // 获取用户信息
            User user = userService.getById(like.getUserId());
            if (user != null) {
                dto.setNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId() != null ? user.getUserId() : "user" + String.format("%03d", user.getId()));
            }
            
            likeDTOs.add(dto);
        }
        
        return AdminResult.success(likeDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个点赞详情
     */
    @GetMapping("/{id}")
    public AdminResult<Like> getLikeById(@PathVariable Long id) {
        Like like = likeService.getById(id);
        if (like == null) {
            return AdminResult.notFound("点赞不存在");
        }
        return AdminResult.success("操作成功", like);
    }

    /**
     * 创建点赞
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createLike(@RequestBody Like like) {
        // 验证必填字段
        if (like.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        if (like.getTargetType() == null) {
            return AdminResult.badRequest("缺少必填字段: target_type");
        }
        if (like.getTargetId() == null) {
            return AdminResult.badRequest("缺少必填字段: target_id");
        }
        
        // 检查用户是否存在
        User user = userService.getById(like.getUserId());
        if (user == null) {
            return AdminResult.badRequest("用户不存在");
        }
        
        // 检查目标是否存在
        if (like.getTargetType() == 1) {
            Post post = postService.getById(like.getTargetId());
            if (post == null) {
                return AdminResult.badRequest("笔记不存在");
            }
        } else if (like.getTargetType() == 2) {
            Comment comment = commentService.getById(like.getTargetId());
            if (comment == null) {
                return AdminResult.badRequest("评论不存在");
            }
        }
        
        likeService.save(like);
        return AdminResult.success("点赞创建成功", Map.of("id", like.getId()));
    }

    /**
     * 更新点赞
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateLike(@PathVariable Long id, @RequestBody Like like) {
        Like existingLike = likeService.getById(id);
        if (existingLike == null) {
            return AdminResult.notFound("点赞不存在");
        }
        
        if (like.getTargetType() != null) existingLike.setTargetType(like.getTargetType());
        if (like.getTargetId() != null) existingLike.setTargetId(like.getTargetId());
        
        likeService.updateById(existingLike);
        return AdminResult.success("点赞更新成功");
    }

    /**
     * 删除单个点赞
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteLike(@PathVariable Long id) {
        Like like = likeService.getById(id);
        if (like == null) {
            return AdminResult.notFound("点赞不存在");
        }
        
        likeService.removeById(id);
        return AdminResult.success("点赞删除成功");
    }

    /**
     * 批量删除点赞
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteLikes(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = likeService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条点赞", Map.of("deletedCount", deletedCount));
    }
}
