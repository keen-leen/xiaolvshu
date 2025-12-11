package com.xiaolvshu.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.dto.PostCommentRequest;
import com.xiaolvshu.dto.PostCommentResponse;
import com.xiaolvshu.entity.Comment;
import com.xiaolvshu.entity.Like;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.CommentMapper;
import com.xiaolvshu.mapper.LikeMapper;
import com.xiaolvshu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService extends ServiceImpl<CommentMapper, Comment> {
    
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final LikeMapper likeMapper;
    
    /**
     * 获取笔记评论列表（顶级评论）
     */
    public PageResult<PostCommentResponse> getCommentsByPostId(Long postId, PostCommentRequest request) {
        Integer page = request.getPage();
        Integer limit = request.getLimit();
        String sort = request.getSort();
        
        // 分页查询顶级评论（parent_id 为 NULL）
        Page<Comment> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .isNull(Comment::getParentId);
        
        // 排序
        if ("asc".equalsIgnoreCase(sort)) {
            wrapper.orderByAsc(Comment::getCreatedAt);
        } else {
            wrapper.orderByDesc(Comment::getCreatedAt);
        }
        
        IPage<Comment> result = commentMapper.selectPage(pageParam, wrapper);
        List<Comment> comments = result.getRecords();
        
        if (comments.isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        // 批量获取用户信息
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        
        // 批量获取子评论数量
        List<Long> commentIds = comments.stream()
                .map(Comment::getId)
                .collect(Collectors.toList());
        Map<Long, Long> replyCountMap = getReplyCountMap(commentIds);
        
        // 批量获取当前用户的点赞状态
        final Set<Long> likedSet;
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            likedSet = likeMapper.selectList(
                new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, currentUserId)
                    .eq(Like::getTargetType, Like.TARGET_TYPE_COMMENT)
                    .in(Like::getTargetId, commentIds))
                .stream()
                .map(Like::getTargetId)
                .collect(Collectors.toSet());
        } else {
            likedSet = Set.of();
        }
        
        // 转换为响应对象
        List<PostCommentResponse> responseList = comments.stream()
                .map(comment -> convertToResponse(
                        comment,
                        userMap.get(comment.getUserId()),
                        replyCountMap.getOrDefault(comment.getId(), 0L).intValue(),
                        likedSet.contains(comment.getId())))
                .toList();
        
        return new PageResult<>(responseList, page, limit, result.getTotal());
    }
    
    /**
     * 批量获取子评论数量
     */
    private Map<Long, Long> getReplyCountMap(List<Long> parentIds) {
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        
        List<Comment> childComments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .in(Comment::getParentId, parentIds)
                        .select(Comment::getParentId)
        );
        
        return childComments.stream()
                .collect(Collectors.groupingBy(Comment::getParentId, Collectors.counting()));
    }
    
    /**
     * 转换为响应对象
     */
    private PostCommentResponse convertToResponse(Comment comment, User user, Integer replyCount, Boolean liked) {
        PostCommentResponse response = new PostCommentResponse();
        BeanUtil.copyProperties(comment, response);
        
        // 用户信息
        if (user != null) {
            response.setNickname(user.getNickname());
            response.setUserAvatar(user.getAvatar());
            response.setUserAutoId(user.getId());
            response.setUserDisplayId(user.getUserId());
            response.setUserLocation(user.getLocation());
            response.setVerified(user.getVerified());
        }
        
        // 统计信息
        response.setReplyCount(replyCount != null ? replyCount : 0);
        response.setLiked(liked != null ? liked : false);
        
        return response;
    }
}
