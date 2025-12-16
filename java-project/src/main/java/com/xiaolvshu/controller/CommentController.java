package com.xiaolvshu.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.CreateCommentRequest;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.dto.PostCommentRequest;
import com.xiaolvshu.dto.PostCommentResponse;
import com.xiaolvshu.service.CommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;

    /**
     * 获取评论回复列表
     */
    @GetMapping("/{commentId}/replies")
    public Result<PageResult<PostCommentResponse>> getRepliesByCommentId(@PathVariable Long commentId, PostCommentRequest request) {
        log.info("获取评论回复列表 - 评论ID: {}, 页码: {}, 每页: {}, 排序: {}", commentId, request.getPage(), request.getLimit(), request.getSort());
        PageResult<PostCommentResponse> replies = commentService.getRepliesByCommentId(commentId, request);
        return Result.success(replies);
    }

    /**
     * 创建评论
     */
    @PostMapping
    public Result<PostCommentResponse> createComment(@RequestBody @Valid CreateCommentRequest request) {
        log.info("创建评论 - 笔记ID: {}, 内容: {}, 父评论ID: {}", request.getPostId(), request.getContent(), request.getParentId());
        PostCommentResponse response = commentService.createComment(request);
        return Result.success(response);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        log.info("删除评论 - 评论ID: {}", id);
        commentService.deleteComment(id);
        return Result.success();
    }
    
}
