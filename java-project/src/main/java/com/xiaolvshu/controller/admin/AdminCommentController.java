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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端评论管理控制器
 */
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;
    private final UserService userService;
    private final PostService postService;

    /**
     * 分页查询评论列表
     */
    @GetMapping
    public AdminResult<?> getCommentList(AdminCommentQueryDTO queryDTO) {
        Page<Comment> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        
        // 笔记ID搜索
        if (queryDTO.getPostId() != null) {
            wrapper.eq(Comment::getPostId, queryDTO.getPostId());
        }
        
        // 评论内容模糊搜索
        if (queryDTO.getContent() != null && !queryDTO.getContent().trim().isEmpty()) {
            wrapper.like(Comment::getContent, queryDTO.getContent().trim());
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
            wrapper.in(Comment::getUserId, userIds);
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "like_count", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Comment::getId);
                    break;
                case "like_count":
                    wrapper.orderBy(true, isAsc, Comment::getLikeCount);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Comment::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Comment::getCreatedAt);
        }
        
        IPage<Comment> result = commentService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminCommentDTO> commentDTOs = new ArrayList<>();
        for (Comment comment : result.getRecords()) {
            AdminCommentDTO dto = new AdminCommentDTO();
            dto.setId(comment.getId());
            dto.setContent(comment.getContent());
            dto.setParentId(comment.getParentId());
            dto.setLikeCount(comment.getLikeCount());
            dto.setCreatedAt(comment.getCreatedAt());
            dto.setUserId(comment.getUserId());
            dto.setPostId(comment.getPostId());
            
            // 获取用户信息
            User user = userService.getById(comment.getUserId());
            if (user != null) {
                dto.setNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId() != null ? user.getUserId() : "user" + String.format("%03d", user.getId()));
            }
            
            // 获取笔记信息
            Post post = postService.getById(comment.getPostId());
            if (post != null) {
                dto.setPostTitle(post.getTitle());
            }
            
            commentDTOs.add(dto);
        }
        
        return AdminResult.success(commentDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个评论详情
     */
    @GetMapping("/{id}")
    public AdminResult<Comment> getCommentById(@PathVariable Long id) {
        Comment comment = commentService.getById(id);
        if (comment == null) {
            return AdminResult.notFound("评论不存在");
        }
        return AdminResult.success("操作成功", comment);
    }

    /**
     * 创建评论
     */
    @PostMapping
    @Transactional
    public AdminResult<Map<String, Long>> createComment(@RequestBody Comment comment) {
        // 验证必填字段
        if (comment.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        if (comment.getPostId() == null) {
            return AdminResult.badRequest("缺少必填字段: post_id");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: content");
        }
        
        // 检查用户是否存在
        User user = userService.getById(comment.getUserId());
        if (user == null) {
            return AdminResult.badRequest("用户不存在");
        }
        
        // 检查笔记是否存在
        Post post = postService.getById(comment.getPostId());
        if (post == null) {
            return AdminResult.badRequest("笔记不存在");
        }
        
        // 如果有父评论，检查父评论是否存在
        if (comment.getParentId() != null) {
            Comment parent = commentService.getById(comment.getParentId());
            if (parent == null) {
                return AdminResult.badRequest("父评论不存在");
            }
        }
        
        comment.setLikeCount(0);
        commentService.save(comment);
        
        return AdminResult.success("评论创建成功", Map.of("id", comment.getId()));
    }

    /**
     * 更新评论
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateComment(@PathVariable Long id, @RequestBody Comment comment) {
        Comment existingComment = commentService.getById(id);
        if (existingComment == null) {
            return AdminResult.notFound("评论不存在");
        }
        
        if (comment.getContent() != null) {
            existingComment.setContent(comment.getContent());
        }
        
        commentService.updateById(existingComment);
        return AdminResult.success("评论更新成功");
    }

    /**
     * 删除单个评论
     */
    @DeleteMapping("/{id}")
    @Transactional
    public AdminResult<Void> deleteComment(@PathVariable Long id) {
        Comment comment = commentService.getById(id);
        if (comment == null) {
            return AdminResult.notFound("评论不存在");
        }
        
        commentService.removeById(id);
        return AdminResult.success("评论删除成功");
    }

    /**
     * 批量删除评论
     */
    @DeleteMapping
    @Transactional
    public AdminResult<Map<String, Integer>> deleteComments(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = commentService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条评论", Map.of("deletedCount", deletedCount));
    }
}
