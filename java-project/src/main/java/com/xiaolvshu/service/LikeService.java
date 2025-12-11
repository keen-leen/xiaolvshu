package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.LikeRequest;
import com.xiaolvshu.dto.LikeResponse;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞服务
 */
@Service
@RequiredArgsConstructor
public class LikeService extends ServiceImpl<LikeMapper, Like> {
    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final NotificationMapper notificationMapper;

    /**
     * 点赞/取消点赞
     */
    @Transactional
    public LikeResponse likes(LikeRequest likeRequest) {
        Long targetId = likeRequest.getTargetId();
        Integer targetType = likeRequest.getTargetType();
        Long currentUserId = UserContext.getUserId();
        Post post = new Post();
        Comment comment = new Comment();
        User user = new User();
        if (targetType == 1) {
            // 目标类型为笔记
            post = postMapper.selectById(targetId);
            if (post == null) {
                throw new IllegalArgumentException("目标不存在");
            }
            user = userMapper.selectById(post.getUserId());
        } else if (targetType == 2) {
            // 目标类型为评论
            comment = commentMapper.selectById(targetId);
            if (comment == null) {
                throw new IllegalArgumentException("目标不存在");
            }
            user = userMapper.selectById(comment.getUserId());
        } else {
            throw new IllegalArgumentException("不支持的目标类型");
        }

        // 检查是否已经点赞
        Like like = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, currentUserId)
                .eq(Like::getTargetId, targetId)
                .eq(Like::getTargetType, targetType));
        LikeResponse response = new LikeResponse();

        if (like != null) {
            // 已点赞，执行取消点赞
            likeMapper.deleteById(like.getId());
            response.setLiked(false);
            // 减少目标和用户的点赞计数
            if (targetType == 1) {
                post.setLikeCount(post.getLikeCount() - 1);
                postMapper.updateById(post);
                user.setLikeCount(user.getLikeCount() - 1);
                userMapper.updateById(user);
            } else {
                comment.setLikeCount(comment.getLikeCount() - 1);
                commentMapper.updateById(comment);
            }
        } else {
            // 未点赞，执行点赞
            Like newLike = new Like();
            newLike.setUserId(currentUserId);
            newLike.setTargetId(targetId);
            newLike.setTargetType(targetType);
            likeMapper.insert(newLike);
            response.setLiked(true);
            // 增加目标和用户的点赞计数
            if (targetType == 1) {
                post.setLikeCount(post.getLikeCount() + 1);
                postMapper.updateById(post);
                user.setLikeCount(user.getLikeCount() + 1);
                userMapper.updateById(user);
            } else {
                comment.setLikeCount(comment.getLikeCount() + 1);
                commentMapper.updateById(comment);
            }
            // 点赞发送通知
            if(!currentUserId.equals(user.getId())){
                Notification notification = new Notification();
                notification.setUserId(user.getId());
                notification.setSenderId(currentUserId);
                if (targetType == 1) {
                    notification.setTargetId(targetId);
                    notification.setType(Notification.TYPE_LIKE_POST);
                    notification.setTitle("赞了你的笔记");
                } else {
                    notification.setTargetId(comment.getPostId());
                    notification.setCommentId(comment.getId());
                    notification.setType(Notification.TYPE_LIKE_COMMENT);
                    notification.setTitle("赞了你的评论");
                }
                notificationMapper.insert(notification);
            }
        }
        return response;
    }
}
