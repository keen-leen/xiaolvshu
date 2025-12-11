package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.service.CommentService;
import com.xiaolvshu.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子控制器
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {
    
    private final PostService postService;
    private final CommentService commentService;
    
    /**
     * 创建帖子
     */
    @PostMapping
    public Result<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PostResponse post = postService.createPost(userId, request);
        return Result.success("发布成功", post);
    }
    
    /**
     * 获取帖子列表
     */
    @GetMapping
    public Result<PageResult<PostResponse>> getPosts(PostRequest request) {
        PageResult<PostResponse> posts = postService.getPosts(request);
        return Result.success(posts);
    }
    
    /**
     * 获取帖子详情
     */
    @GetMapping("/{id}")
    public Result<PostResponse> getPostById(@PathVariable Long id) {
        PostResponse post = postService.getPostById(id);
        return Result.success(post);
    }
    
    /**
     * 获取帖子评论列表
     */
    @GetMapping("/{postId}/comments")
    public Result<PageResult<PostCommentResponse>> getCommentsByPostId(@PathVariable Long postId, PostCommentRequest request) {
        log.info("获取笔记评论列表 - 笔记ID: {}, 页码: {}, 每页: {}, 排序: {}", postId, request.getPage(), request.getLimit(), request.getSort());
        PageResult<PostCommentResponse> comments = commentService.getCommentsByPostId(postId, request);
        return Result.success(comments);
    }
    /**
     * 删除帖子
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        postService.deletePost(userId, id);
        return Result.success("删除成功", null);
    }
    
    /**
     * 搜索帖子
     */
    @GetMapping("/search")
    public Result<PageResult<PostResponse>> searchPosts(PostSearchRequest request, Authentication authentication) {
        Long currentUserId = null;
        if (authentication != null) {
            currentUserId = (Long) authentication.getPrincipal();
        }
        PageResult<PostResponse> posts = postService.searchPosts(request.getKeyword(), request.getPage(), request.getLimit(), currentUserId);
        return Result.success(posts);
    }
}
