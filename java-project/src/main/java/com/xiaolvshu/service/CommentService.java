package com.xiaolvshu.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.CreateCommentRequest;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.dto.PostCommentRequest;
import com.xiaolvshu.dto.PostCommentResponse;
import com.xiaolvshu.entity.Comment;
import com.xiaolvshu.entity.Like;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.CommentMapper;
import com.xiaolvshu.mapper.LikeMapper;
import com.xiaolvshu.mapper.NotificationMapper;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.UserMapper;
import com.xiaolvshu.utils.MentionParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PostMapper postMapper;
    private final NotificationMapper notificationMapper;
    
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
    
    public PageResult<PostCommentResponse> getRepliesByCommentId(Long commentId, PostCommentRequest request) {
        Integer page = request.getPage();
        Integer limit = request.getLimit();
        String sort = request.getSort();
        
        // 分页查询子评论（parent_id 为 commentId）
        Page<Comment> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, commentId);
        
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
        
        // 批量获取当前用户的点赞状态
        final Set<Long> likedSet;
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            likedSet = likeMapper.selectList(
                new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, currentUserId)
                    .eq(Like::getTargetType, Like.TARGET_TYPE_COMMENT)
                    .in(Like::getTargetId, comments.stream().map(Comment::getId).toList()))
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
                        0,
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

    /**
     * 创建评论
     */
    @Transactional(rollbackFor = Exception.class)
    public PostCommentResponse createComment(CreateCommentRequest request) {
        Long userId = UserContext.getUserId();
        Long postId = request.getPostId();
        String content = request.getContent();
        Long parentId = request.getParentId();

        // 验证笔记是否存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("笔记不存在");
        }

        // 如果是回复评论，验证父评论是否存在
        if (parentId != null) {
            Comment parentComment = commentMapper.selectById(parentId);
            if (parentComment == null) {
                throw new BusinessException("父评论不存在");
            }
        }

        // 插入评论
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setParentId(parentId);
        commentMapper.insert(comment);

        // 更新笔记评论数
        post.setCommentCount(post.getCommentCount() + 1);
        postMapper.updateById(post);

        // 创建通知
        if (parentId != null) {
            // 回复评论，给被回复的评论作者发通知
            Comment parentComment = commentMapper.selectById(parentId);
            if (parentComment != null) {
                Long parentUserId = parentComment.getUserId();
                // 不给自己发通知
                if (!parentUserId.equals(userId)) {
                    Notification notification = new Notification();
                    notification.setUserId(parentUserId);
                    notification.setSenderId(userId);
                    notification.setType(Notification.TYPE_REPLY_COMMENT);
                    notification.setTitle("回复了你的评论");
                    notification.setTargetId(postId);
                    notification.setCommentId(comment.getId());
                    notificationMapper.insert(notification);
                }
            }
        } else {
            // 评论笔记，给笔记作者发通知
            Long postUserId = post.getUserId();
            // 不给自己发通知
            if (!postUserId.equals(userId)) {
                Notification notification = new Notification();
                notification.setUserId(postUserId);
                notification.setSenderId(userId);
                notification.setType(Notification.TYPE_COMMENT_POST);
                notification.setTitle("评论了你的笔记");
                notification.setTargetId(postId);
                notification.setCommentId(comment.getId());
                notificationMapper.insert(notification);
            }
        }

        // 处理@用户通知
        if (MentionParser.hasMentions(content)) {
            List<MentionParser.MentionedUser> mentionedUsers = MentionParser.extractMentionedUsers(content);
            for (MentionParser.MentionedUser mentionedUser : mentionedUsers) {
                try {
                    // 根据小旅书号查找用户
                    User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, mentionedUser.getUserId()));
                    if (user != null) {
                        Long mentionedUserId = user.getId();
                        // 不给自己发通知
                        if (!mentionedUserId.equals(userId)) {
                            Notification notification = new Notification();
                            notification.setUserId(mentionedUserId);
                            notification.setSenderId(userId);
                            notification.setType(Notification.TYPE_MENTION_COMMENT);
                            notification.setTitle("在评论中@了你");
                            notification.setTargetId(postId);
                            notification.setCommentId(comment.getId());
                            notificationMapper.insert(notification);
                        }
                    }
                } catch (Exception e) {
                    log.error("处理@用户通知失败 - 用户: {}", mentionedUser.getUserId(), e);
                }
            }
        }

        // 返回完整的评论信息
        User user = userMapper.selectById(userId);
        return convertToResponse(comment, user, 0, false);
    }

    /**
     * 删除评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        Long userId = UserContext.getUserId();
        
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己发布的评论");
        }
        
        // 递归删除评论及其子评论，获取删除的评论总数
        int deletedCount = deleteCommentRecursive(commentId);
        
        // 更新笔记评论数
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - deletedCount));
            postMapper.updateById(post);
        }
    }
    
    /**
     * 递归删除评论及其子评论
     */
    private int deleteCommentRecursive(Long commentId) {
        int deletedCount = 0;
        
        // 获取所有子评论
        List<Comment> children = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getParentId, commentId));
        
        // 递归删除子评论
        for (Comment child : children) {
            deletedCount += deleteCommentRecursive(child.getId());
        }
        
        // 删除当前评论的点赞记录
        likeMapper.delete(new LambdaQueryWrapper<Like>()
                .eq(Like::getTargetType, Like.TARGET_TYPE_COMMENT)
                .eq(Like::getTargetId, commentId));
                
        // 删除当前评论
        commentMapper.deleteById(commentId);
        
        deletedCount += 1;
        return deletedCount;
    }
}
