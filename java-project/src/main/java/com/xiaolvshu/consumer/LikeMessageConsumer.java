package com.xiaolvshu.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.config.RabbitMQConfig;
import com.xiaolvshu.dto.LikeMessage;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞消息消费者
 * 异步消费 RabbitMQ 中的点赞/取消点赞消息，持久化到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeMessageConsumer {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final NotificationMapper notificationMapper;

    @RabbitListener(queues = RabbitMQConfig.LIKE_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void handleLikeMessage(LikeMessage message) {
        log.info("收到点赞消息: userId={}, targetId={}, targetType={}, action={}",
                message.getUserId(), message.getTargetId(), message.getTargetType(), message.getAction());

        if (LikeMessage.ACTION_LIKE.equals(message.getAction())) {
            handleLike(message);
        } else if (LikeMessage.ACTION_UNLIKE.equals(message.getAction())) {
            handleUnlike(message);
        } else {
            /*
             * 未知动作不能当成成功消息静默 ACK。抛出异常后，数据库事务回滚，
             * AUTO acknowledge 会依据 default-requeue-rejected=false 将消息送入死信队列。
             */
            throw new IllegalArgumentException("未知的点赞操作类型: " + message.getAction());
        }

        log.info("点赞消息处理成功: userId={}, targetId={}, action={}",
                message.getUserId(), message.getTargetId(), message.getAction());
    }

    /**
     * 处理点赞
     */
    private void handleLike(LikeMessage message) {
        Long userId = message.getUserId();
        Long targetId = message.getTargetId();
        Integer targetType = message.getTargetType();

        // 幂等性检查：如果数据库中已存在该点赞记录，则跳过
        Like existingLike = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getTargetId, targetId)
                .eq(Like::getTargetType, targetType));
        if (existingLike != null) {
            log.info("点赞记录已存在，跳过: userId={}, targetId={}, targetType={}", userId, targetId, targetType);
            return;
        }

        // 插入点赞记录
        Like newLike = new Like();
        newLike.setUserId(userId);
        newLike.setTargetId(targetId);
        newLike.setTargetType(targetType);
        likeMapper.insert(newLike);

        // 更新目标的点赞计数
        if (targetType == Like.TARGET_TYPE_POST) {
            Post post = postMapper.selectById(targetId);
            if (post != null) {
                postMapper.adjustLikeCount(targetId, 1);
                // 更新作者的获赞数
                User author = userMapper.selectById(post.getUserId());
                if (author != null) {
                    userMapper.adjustLikeCount(author.getId(), 1);
                }
                // 发送通知（不给自己发通知）
                if (!userId.equals(post.getUserId())) {
                    Notification notification = new Notification();
                    notification.setUserId(post.getUserId());
                    notification.setSenderId(userId);
                    notification.setTargetId(targetId);
                    notification.setType(Notification.TYPE_LIKE_POST);
                    notification.setTitle("赞了你的笔记");
                    notificationMapper.insert(notification);
                }
            }
        } else if (targetType == Like.TARGET_TYPE_COMMENT) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment != null) {
                commentMapper.adjustLikeCount(targetId, 1);
                // 发送通知（不给自己发通知）
                if (!userId.equals(comment.getUserId())) {
                    Notification notification = new Notification();
                    notification.setUserId(comment.getUserId());
                    notification.setSenderId(userId);
                    notification.setTargetId(comment.getPostId());
                    notification.setCommentId(comment.getId());
                    notification.setType(Notification.TYPE_LIKE_COMMENT);
                    notification.setTitle("赞了你的评论");
                    notificationMapper.insert(notification);
                }
            }
        }
    }

    /**
     * 处理取消点赞
     */
    private void handleUnlike(LikeMessage message) {
        Long userId = message.getUserId();
        Long targetId = message.getTargetId();
        Integer targetType = message.getTargetType();

        // 幂等性检查：如果数据库中不存在该点赞记录，则跳过
        Like existingLike = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getTargetId, targetId)
                .eq(Like::getTargetType, targetType));
        if (existingLike == null) {
            log.info("点赞记录不存在，跳过取消: userId={}, targetId={}, targetType={}", userId, targetId, targetType);
            return;
        }

        // 删除点赞记录
        likeMapper.deleteById(existingLike.getId());

        // 减少目标的点赞计数
        if (targetType == Like.TARGET_TYPE_POST) {
            Post post = postMapper.selectById(targetId);
            if (post != null) {
                postMapper.adjustLikeCount(targetId, -1);
                // 减少作者的获赞数
                User author = userMapper.selectById(post.getUserId());
                if (author != null) {
                    userMapper.adjustLikeCount(author.getId(), -1);
                }
            }
        } else if (targetType == Like.TARGET_TYPE_COMMENT) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment != null) {
                commentMapper.adjustLikeCount(targetId, -1);
            }
        }
    }
}
