package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.common.constant.RedisExpireConstant;
import com.xiaolvshu.config.RabbitMQConfig;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.LikeMessage;
import com.xiaolvshu.dto.LikeRequest;
import com.xiaolvshu.dto.LikeResponse;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.mapper.*;
import com.xiaolvshu.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 点赞服务
 * 策略：先更新 Redis 缓存，再通过 RabbitMQ 异步更新数据库
 * <p>
 * 缓存设计（per-user SET）：
 * - 帖子点赞：xiaolvshu:post:user_likes:{userId} -> SET of postId
 * - 评论点赞：xiaolvshu:comment:user_likes:{userId} -> SET of commentId
 * - 初始化标记：xiaolvshu:like:user_init:{targetType}:{userId} -> "1"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService extends ServiceImpl<LikeMapper, Like> {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final RedisService redisService;
    private final CacheService cacheService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 点赞/取消点赞
     * 1. 先更新 Redis 缓存
     * 2. 再通过 RabbitMQ 异步更新数据库
     */
    public LikeResponse likes(LikeRequest likeRequest) {
        Long targetId = likeRequest.getTargetId();
        Integer targetType = likeRequest.getTargetType();
        Long currentUserId = UserContext.getUserId();

        // 验证目标是否存在
        validateTarget(targetId, targetType);

        // 确保该用户的点赞缓存已初始化
        initUserLikeCacheIfAbsent(currentUserId, targetType);

        String likeSetKey = getLikeSetKey(currentUserId, targetType);
        LikeResponse response = new LikeResponse();

        String likeCountKey;
        if (targetType == Like.TARGET_TYPE_POST) {
            initPostLikeCountCacheIfAbsent(targetId);
            likeCountKey = RedisKeyUtil.getPostLikeCountKey(targetId);
        } else {
            initCommentLikeCountCacheIfAbsent(targetId);
            likeCountKey = RedisKeyUtil.getCommentLikeCountKey(targetId);
        }

        // 使用 Lua 原子更新「用户点赞关系 + 点赞数缓存」
        long op = toggleLikeAtomically(likeSetKey, likeCountKey, targetId);
        if (op > 0) {
            response.setLiked(true);
            sendLikeMessage(currentUserId, targetId, targetType, LikeMessage.ACTION_LIKE);
            log.info("用户 {} 点赞 targetType={}, targetId={}", currentUserId, targetType, targetId);
        } else if (op < 0) {
            response.setLiked(false);
            sendLikeMessage(currentUserId, targetId, targetType, LikeMessage.ACTION_UNLIKE);
            log.info("用户 {} 取消点赞 targetType={}, targetId={}", currentUserId, targetType, targetId);
        } else {
            response.setLiked(cacheService.isInUserSet(likeSetKey, targetId));
            log.info("用户 {} 点赞状态无变化 targetType={}, targetId={}, liked={}",
                    currentUserId, targetType, targetId, response.isLiked());
        }

        return response;
    }

    /**
     * 查询用户是否点赞了某个目标（从 Redis 缓存查询）
     */
    public boolean isLiked(Long userId, Long targetId, Integer targetType) {
        initUserLikeCacheIfAbsent(userId, targetType);
        String likeSetKey = getLikeSetKey(userId, targetType);
        return cacheService.isInUserSet(likeSetKey, targetId);
    }

    /**
     * 批量查询用户点赞的目标ID集合（从 Redis 缓存查询）
     * 从用户的点赞 SET 中获取全部已点赞 ID，再与传入的 targetIds 取交集
     */
    public Set<Long> getLikedTargetIds(Long userId, List<Long> targetIds, Integer targetType) {
        initUserLikeCacheIfAbsent(userId, targetType);
        String likeSetKey = getLikeSetKey(userId, targetType);
        Set<Object> members = cacheService.getUserSet(likeSetKey);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        // 将缓存中的值转为 Long 集合
        Set<Long> allLikedIds = members.stream()
                .filter(m -> m instanceof Number)
                .map(m -> ((Number) m).longValue())
                .collect(Collectors.toSet());
        // 取交集：只保留当前页面需要的 targetIds
        allLikedIds.retainAll(new HashSet<>(targetIds));
        return allLikedIds;
    }

    /**
     * 获取帖子点赞数（优先缓存，未命中则回源数据库并回填）
     */
    public int getPostLikeCount(Long postId) {
        String countKey = RedisKeyUtil.getPostLikeCountKey(postId);
        Integer count = cacheService.getOrLoad(
                countKey,
                Integer.class,
                RedisExpireConstant.LIKE_SET_EXPIRE,
                () -> {
                    Post post = postMapper.selectById(postId);
                    return post == null || post.getLikeCount() == null ? 0 : post.getLikeCount();
                });
        return count == null ? 0 : count;
    }

    /**
     * 批量获取帖子点赞数
     */
    public Map<Long, Integer> getPostLikeCountMap(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, getPostLikeCount(postId));
        }
        return result;
    }

    /**
     * 获取评论点赞数（优先缓存，未命中则回源数据库并回填）
     */
    public int getCommentLikeCount(Long commentId) {
        String countKey = RedisKeyUtil.getCommentLikeCountKey(commentId);
        Integer count = cacheService.getOrLoad(
                countKey,
                Integer.class,
                RedisExpireConstant.LIKE_SET_EXPIRE,
                () -> {
                    Comment comment = commentMapper.selectById(commentId);
                    return comment == null || comment.getLikeCount() == null ? 0 : comment.getLikeCount();
                });
        return count == null ? 0 : count;
    }

    /**
     * 批量获取评论点赞数
     */
    public Map<Long, Integer> getCommentLikeCountMap(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Long commentId : commentIds) {
            result.put(commentId, getCommentLikeCount(commentId));
        }
        return result;
    }

    // ============ 私有方法 ============

    /**
     * 根据用户ID和目标类型获取点赞集合的 Redis Key
     * 复用 RedisKeyUtil 中已定义的 key 规范
     */
    private String getLikeSetKey(Long userId, Integer targetType) {
        if (targetType == Like.TARGET_TYPE_POST) {
            return RedisKeyUtil.getUserPostLikesKey(userId);
        } else {
            return RedisKeyUtil.getUserCommentLikesKey(userId);
        }
    }

    /**
     * 验证目标是否存在
     */
    private void validateTarget(Long targetId, Integer targetType) {
        if (targetType == Like.TARGET_TYPE_POST) {
            if (postMapper.selectById(targetId) == null) {
                throw new IllegalArgumentException("目标不存在");
            }
        } else if (targetType == Like.TARGET_TYPE_COMMENT) {
            if (commentMapper.selectById(targetId) == null) {
                throw new IllegalArgumentException("目标不存在");
            }
        } else {
            throw new IllegalArgumentException("不支持的目标类型");
        }
    }

    /**
     * 初始化用户点赞缓存（如果不存在）
     * 从数据库加载该用户的所有点赞记录到 Redis SET
     */
    private void initUserLikeCacheIfAbsent(Long userId, Integer targetType) {
        String initKey = RedisKeyUtil.getLikeUserInitKey(userId, targetType);
        if (redisService.hasKey(initKey)) {
            return;
        }

        // 从数据库加载该用户此类型的全部点赞记录
        List<Like> likes = likeMapper.selectList(
                new LambdaQueryWrapper<Like>()
                        .eq(Like::getUserId, userId)
                        .eq(Like::getTargetType, targetType)
                        .select(Like::getTargetId));

        String likeSetKey = getLikeSetKey(userId, targetType);
        if (!likes.isEmpty()) {
            for (Like like : likes) {
                cacheService.addToUserSet(likeSetKey, like.getTargetId());
            }
            redisService.expire(likeSetKey, RedisExpireConstant.LIKE_SET_EXPIRE);
        }

        // 设置初始化标记（与点赞集合相同的过期时间）
        redisService.set(initKey, "1", RedisExpireConstant.LIKE_SET_EXPIRE);
        log.debug("用户点赞缓存初始化完成: userId={}, targetType={}, count={}", userId, targetType, likes.size());
    }

    /**
     * 初始化帖子点赞数缓存（如果不存在）
     */
    private void initPostLikeCountCacheIfAbsent(Long postId) {
        String countKey = RedisKeyUtil.getPostLikeCountKey(postId);
        if (redisService.hasKey(countKey)) {
            return;
        }
        Post post = postMapper.selectById(postId);
        int dbLikeCount = post == null || post.getLikeCount() == null ? 0 : post.getLikeCount();
        cacheService.setCount(countKey, dbLikeCount, RedisExpireConstant.LIKE_SET_EXPIRE);
    }

    /**
     * 初始化评论点赞数缓存（如果不存在）
     */
    private void initCommentLikeCountCacheIfAbsent(Long commentId) {
        String countKey = RedisKeyUtil.getCommentLikeCountKey(commentId);
        if (redisService.hasKey(countKey)) {
            return;
        }
        Comment comment = commentMapper.selectById(commentId);
        int dbLikeCount = comment == null || comment.getLikeCount() == null ? 0 : comment.getLikeCount();
        cacheService.setCount(countKey, dbLikeCount, RedisExpireConstant.LIKE_SET_EXPIRE);
    }

    /**
     * Lua 原子切换点赞状态
     * KEYS[1]: 用户点赞集合 key
     * KEYS[2]: 点赞数 key（帖子或评论）
     * ARGV[1]: targetId
     * ARGV[2]: ttlSeconds
     * 返回值：1=点赞成功，-1=取消点赞成功，0=状态未变化
     */
    private long toggleLikeAtomically(String likeSetKey, String likeCountKey, Long targetId) {
        String script = """
                local setKey = KEYS[1]
                local countKey = KEYS[2]
                local targetId = ARGV[1]
                local ttl = tonumber(ARGV[2])

                local exists = redis.call('SISMEMBER', setKey, targetId)
                if exists == 1 then
                    redis.call('SREM', setKey, targetId)
                    local current = tonumber(redis.call('GET', countKey) or '0')
                    if current > 0 then
                        redis.call('DECR', countKey)
                    end
                    redis.call('EXPIRE', setKey, ttl)
                    redis.call('EXPIRE', countKey, ttl)
                    return -1
                else
                    redis.call('SADD', setKey, targetId)
                    redis.call('INCR', countKey)
                    redis.call('EXPIRE', setKey, ttl)
                    redis.call('EXPIRE', countKey, ttl)
                    return 1
                end
                """;
        return redisService.evalLong(
                script,
            Arrays.asList(likeSetKey, likeCountKey),
                String.valueOf(targetId),
                String.valueOf(RedisExpireConstant.LIKE_SET_EXPIRE));
    }

    /**
     * 发送点赞消息到 RabbitMQ
     */
    private void sendLikeMessage(Long userId, Long targetId, Integer targetType, String action) {
        LikeMessage message = LikeMessage.builder()
                .userId(userId)
                .targetId(targetId)
                .targetType(targetType)
                .action(action)
                .build();

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.LIKE_EXCHANGE,
                    RabbitMQConfig.LIKE_ROUTING_KEY,
                    message);
            log.debug("点赞消息发送成功: {}", message);
        } catch (Exception e) {
            log.error("点赞消息发送失败，尝试直接写库: {}", e.getMessage(), e);
            // 降级：MQ 发送失败时，直接同步写数据库以保证数据不丢
            fallbackDirectDbUpdate(message);
        }
    }

    /**
     * 降级策略：MQ 不可用时直接同步写数据库
     */
    private void fallbackDirectDbUpdate(LikeMessage message) {
        try {
            if (LikeMessage.ACTION_LIKE.equals(message.getAction())) {
                Like existingLike = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                        .eq(Like::getUserId, message.getUserId())
                        .eq(Like::getTargetId, message.getTargetId())
                        .eq(Like::getTargetType, message.getTargetType()));
                if (existingLike == null) {
                    Like newLike = new Like();
                    newLike.setUserId(message.getUserId());
                    newLike.setTargetId(message.getTargetId());
                    newLike.setTargetType(message.getTargetType());
                    likeMapper.insert(newLike);
                }
            } else {
                likeMapper.delete(new LambdaQueryWrapper<Like>()
                        .eq(Like::getUserId, message.getUserId())
                        .eq(Like::getTargetId, message.getTargetId())
                        .eq(Like::getTargetType, message.getTargetType()));
            }
            log.info("降级直接写库成功: userId={}, targetId={}", message.getUserId(), message.getTargetId());
        } catch (Exception ex) {
            log.error("降级直接写库也失败: {}", ex.getMessage(), ex);
        }
    }
}
