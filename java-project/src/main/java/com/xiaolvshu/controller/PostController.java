package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.service.CollectionService;
import com.xiaolvshu.service.CommentService;
import com.xiaolvshu.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {
    
    private final PostService postService;
    private final CommentService commentService;
    private final CollectionService collectionService;
    
    /**
     * 创建笔记
     */
    @PostMapping
    public Result<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        log.info("创建帖子 - 标题: {}, 内容长度: {}, 类型: {}, 是否为草稿: {}", request.getTitle(), request.getContent() != null ? request.getContent().length() : 0, request.getType(), request.isDraft());
        PostResponse post = postService.createPost(request);
        return Result.success("发布成功", post);
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{id}")
    public Result<PostResponse> updatePost(@PathVariable Long id, @Valid @RequestBody CreatePostRequest request) {
        log.info("更新帖子 - ID: {}, 标题: {}, 内容长度: {}, 类型: {}, 是否为草稿: {}", id, request.getTitle(), request.getContent() != null ? request.getContent().length() : 0, request.getType(), request.isDraft());
        PostResponse post;
        try {
            post = postService.updatePost(id, request);
        } catch (Exception e) {
            log.error("更新帖子失败 - ID: {}, 错误信息: {}", id, e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
        
        return Result.success("更新成功", post);
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        log.info("删除笔记 - ID: {}", id);
        postService.deletePost(id);
        return Result.success("删除成功", null);
    }

    /**
     * 获取帖子列表
     */
    @GetMapping
    public Result<PageResult<PostResponse>> getPosts(PostRequest request) {
        log.info("获取帖子列表 - 分类: {}, 用户ID: {}, 是否草稿: {}, 类型: {}, 页码: {}, 每页: {}, 排序: {}", request.getCategory(), request.getUserId(), request.getIsDraft(), request.getType(), request.getPage(), request.getLimit(), request.getSort());
        PageResult<PostResponse> posts = postService.getPosts(request);
        return Result.success(posts);
    }

    /**
     * 获取帖子详情
     */
    @GetMapping("/{id}")
    public Result<PostResponse> getPostById(@PathVariable Long id, @RequestParam(value = "skipViewCount", required = false) Boolean skipViewCount) {
        log.info("获取帖子详情 - ID: {}", id);
        PostResponse post = postService.getPostById(id, skipViewCount);
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
     * 搜索帖子
     */
    @GetMapping("/search")
    public Result<PageResult<PostResponse>> searchPosts(PostSearchRequest request) {
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            return Result.error("请输入搜索关键词");
        }
        log.info("搜索笔记 - 关键词: {}, 页码: {}, 每页: {}", request.getKeyword(), request.getPage(), request.getLimit());
        PageResult<PostResponse> posts = postService.searchPosts(request.getKeyword(), request.getPage(), request.getLimit());
        return Result.success(posts);
    }
    
    /**
     * 收藏/取消收藏笔记
     * POST /posts/{id}/collect
     */
    @PostMapping("/{id}/collect")
    public Result<CollectResponse> collectPost(@PathVariable Long id) {
        log.info("收藏/取消收藏笔记 - 笔记ID: {}", id);
        CollectResponse response = collectionService.toggleCollect(id);
        String message = response.isCollected() ? "收藏成功" : "取消收藏成功";
        return Result.success(message, response);
    }
}
