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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            
            // 单条 SQL 原子递减，避免并发收藏/取消时基于旧对象覆盖其他请求的结果。
            postMapper.adjustCollectCount(postId, -1);
            
            return new CollectResponse(false);
        } else {
            // 未收藏，执行收藏
            Collection collection = new Collection();
            collection.setUserId(userId);
            collection.setPostId(postId);
            this.save(collection);
            
            // 单条 SQL 原子递增，收藏记录、计数和通知由当前事务共同提交或回滚。
            postMapper.adjustCollectCount(postId, 1);
            
            // 创建收藏通知（不给自己发通知）
            if (!post.getUserId().equals(userId)) {
                Notification notification = new Notification();
                notification.setUserId(post.getUserId());
                notification.setSenderId(userId);
                notification.setType(Notification.TYPE_COLLECT_POST);
                // notifications.title 为 NOT NULL；所有通知创建路径都必须显式提供展示标题。
                notification.setTitle("收藏了你的笔记");
                notification.setTargetId(postId);
                notification.setIsRead(0);
                notificationMapper.insert(notification);
            }
            
            return new CollectResponse(true);
        }
    }

    /**
     * 后台创建收藏。
     *
     * <p>后台接口不能直接调用通用 save，否则收藏关系已经存在而笔记统计仍保持旧值。
     * 该方法把关系写入和计数更新纳入同一事务，数据库唯一索引负责处理并发重复创建。</p>
     */
    @Transactional
    public Collection createForAdmin(Long userId, Long postId) {
        Collection collection = new Collection();
        collection.setUserId(userId);
        collection.setPostId(postId);
        try {
            this.save(collection);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("已经收藏过该笔记");
        }
        postMapper.adjustCollectCount(postId, 1);
        return collection;
    }

    /**
     * 后台修改收藏关联的笔记，同时迁移两篇笔记的收藏计数。
     */
    @Transactional
    public void updatePostForAdmin(Long collectionId, Long newPostId) {
        Collection existing = this.getById(collectionId);
        if (existing == null) {
            throw new BusinessException("收藏不存在");
        }
        Long oldPostId = existing.getPostId();
        if (oldPostId.equals(newPostId)) {
            return;
        }

        existing.setPostId(newPostId);
        try {
            this.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("已经收藏过该笔记");
        }
        postMapper.adjustCollectCount(oldPostId, -1);
        postMapper.adjustCollectCount(newPostId, 1);
    }

    /**
     * 后台删除单条收藏并同步扣减统计。
     *
     * @return 收藏存在且删除成功时返回 true
     */
    @Transactional
    public boolean deleteForAdmin(Long collectionId) {
        Collection existing = this.getById(collectionId);
        if (existing == null || !this.removeById(collectionId)) {
            return false;
        }
        postMapper.adjustCollectCount(existing.getPostId(), -1);
        return true;
    }

    /**
     * 后台批量删除收藏。
     *
     * <p>先一次性读取真实存在的关系，再按笔记分组扣减。这样请求中包含不存在的 ID 时，
     * 返回值与计数变化都以实际删除记录为准，而不是以传入 ID 数量为准。</p>
     */
    @Transactional
    public int deleteBatchForAdmin(List<Long> collectionIds) {
        List<Collection> existingCollections = this.listByIds(collectionIds);
        if (existingCollections.isEmpty()) {
            return 0;
        }

        List<Long> existingIds = existingCollections.stream().map(Collection::getId).toList();
        if (!this.removeByIds(existingIds)) {
            return 0;
        }

        Map<Long, Long> deletedCountByPost = existingCollections.stream()
                .collect(Collectors.groupingBy(Collection::getPostId, Collectors.counting()));
        deletedCountByPost.forEach((postId, count) ->
                postMapper.adjustCollectCount(postId, -Math.toIntExact(count)));
        return existingCollections.size();
    }
}
