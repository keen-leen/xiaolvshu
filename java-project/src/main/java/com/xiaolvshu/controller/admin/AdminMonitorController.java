package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端监控控制器
 */
@RestController
@RequestMapping("/admin/monitor")
@RequiredArgsConstructor
public class AdminMonitorController {

    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;

    /**
     * 获取最近动态
     */
    @GetMapping("/activities")
    public AdminResult<List<AdminActivityDTO>> getActivities() {
        List<AdminActivityDTO> activities = new ArrayList<>();
        
        // 获取最近10个新注册用户
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.orderByDesc(User::getCreatedAt).last("LIMIT 10");
        List<User> newUsers = userService.list(userWrapper);
        
        for (User user : newUsers) {
            AdminActivityDTO dto = new AdminActivityDTO();
            dto.setId("user_" + user.getId());
            dto.setType("user_register");
            dto.setUserId(user.getUserId());
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
            dto.setTitle("新用户注册");
            dto.setContent("用户 " + user.getNickname() + " (" + user.getUserId() + ") 注册了账号");
            dto.setTargetId(user.getId());
            dto.setCreatedAt(user.getCreatedAt());
            activities.add(dto);
        }
        
        // 获取最近10篇发布的笔记
        LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.eq(Post::getIsDraft, 0).orderByDesc(Post::getCreatedAt).last("LIMIT 10");
        List<Post> newPosts = postService.list(postWrapper);
        
        for (Post post : newPosts) {
            User user = userService.getById(post.getUserId());
            AdminActivityDTO dto = new AdminActivityDTO();
            dto.setId("post_" + post.getId());
            dto.setType("post_publish");
            dto.setUserId(user != null ? user.getUserId() : null);
            dto.setNickname(user != null ? user.getNickname() : null);
            dto.setAvatar(user != null ? user.getAvatar() : null);
            dto.setTitle(post.getTitle());
            dto.setContent((user != null ? user.getNickname() : "用户") + " 发布了笔记《" + post.getTitle() + "》");
            dto.setTargetId(post.getId());
            dto.setCreatedAt(post.getCreatedAt());
            activities.add(dto);
        }
        
        // 获取最近10条评论
        LambdaQueryWrapper<Comment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.orderByDesc(Comment::getCreatedAt).last("LIMIT 10");
        List<Comment> newComments = commentService.list(commentWrapper);
        
        for (Comment comment : newComments) {
            User user = userService.getById(comment.getUserId());
            Post post = postService.getById(comment.getPostId());
            
            AdminActivityDTO dto = new AdminActivityDTO();
            dto.setId("comment_" + comment.getId());
            dto.setType("comment_publish");
            dto.setUserId(user != null ? user.getUserId() : null);
            dto.setNickname(user != null ? user.getNickname() : null);
            dto.setAvatar(user != null ? user.getAvatar() : null);
            dto.setTitle(post != null ? post.getTitle() : null);
            dto.setContent(comment.getContent());
            dto.setDescription((user != null ? user.getNickname() : "用户") + " 在《" + (post != null ? post.getTitle() : "未知") + "》中发表了评论");
            dto.setTargetId(comment.getPostId());
            dto.setCreatedAt(comment.getCreatedAt());
            activities.add(dto);
        }
        
        // 按时间降序排序
        activities.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        
        return AdminResult.success("获取动态成功", activities);
    }
}
