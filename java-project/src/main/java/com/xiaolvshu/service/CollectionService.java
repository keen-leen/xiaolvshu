package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.CollectResponse;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.CollectionMapper;
import com.xiaolvshu.mapper.NotificationMapper;
import com.xiaolvshu.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏服务
 */
@Service
@RequiredArgsConstructor
public class CollectionService extends ServiceImpl<CollectionMapper, Collection> {
    
    private final PostMapper postMapper;
    private final NotificationMapper notificationMapper;
    
    /**
     * 收藏/取消收藏笔记
     *
     * @param postId 笔记ID
     * @return 收藏响应
     */
    @Transactional
    public CollectResponse toggleCollect(Long postId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        
        // 验证笔记是否存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("笔记不存在");
        }
        
        // 检查是否已经收藏
        Collection existingCollection = this.getOne(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getUserId, userId)
                .eq(Collection::getPostId, postId));
        
        if (existingCollection != null) {
            // 已收藏，执行取消收藏
            this.removeById(existingCollection.getId());
            
            // 更新笔记收藏数
            post.setCollectCount(Math.max(0, post.getCollectCount() - 1));
            postMapper.updateById(post);
            
            return new CollectResponse(false);
        } else {
            // 未收藏，执行收藏
            Collection collection = new Collection();
            collection.setUserId(userId);
            collection.setPostId(postId);
            this.save(collection);
            
            // 更新笔记收藏数
            post.setCollectCount(post.getCollectCount() + 1);
            postMapper.updateById(post);
            
            // 创建收藏通知（不给自己发通知）
            if (!post.getUserId().equals(userId)) {
                Notification notification = new Notification();
                notification.setUserId(post.getUserId());
                notification.setSenderId(userId);
                notification.setType(Notification.TYPE_COLLECT_POST);
                notification.setTargetId(postId);
                notification.setIsRead(0);
                notificationMapper.insert(notification);
            }
            
            return new CollectResponse(true);
        }
    }
}
