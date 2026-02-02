package com.xiaolvshu.service;

import com.xiaolvshu.common.constant.RedisExpireConstant;
import com.xiaolvshu.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 缓存服务类
 * 提供业务相关的缓存操作封装
 * 包含缓存穿透、缓存击穿的处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisService redisService;

    /**
     * 缓存空值，防止缓存穿透
     */
    private static final String NULL_VALUE = "NULL";
    
    /**
     * 空值缓存过期时间：5分钟
     */
    private static final long NULL_EXPIRE = RedisExpireConstant.FIVE_MINUTES;

    // ==================== 通用缓存操作 ====================

    /**
     * 获取缓存，如果不存在则从数据库加载并缓存
     * 包含缓存穿透防护
     *
     * @param key        缓存 key
     * @param clazz      返回类型
     * @param expireTime 过期时间（秒）
     * @param dbFallback 数据库回调函数
     * @return 缓存值
     */
    public <T> T getOrLoad(String key, Class<T> clazz, long expireTime, Supplier<T> dbFallback) {
        // 1. 先从缓存获取
        Object cached = redisService.get(key);
        
        // 2. 如果是空值标记，说明数据库中也没有，返回 null（防止缓存穿透）
        if (NULL_VALUE.equals(cached)) {
            return null;
        }
        
        // 3. 如果缓存命中，直接返回
        if (cached != null && clazz.isInstance(cached)) {
            return clazz.cast(cached);
        }
        
        // 4. 缓存未命中，从数据库加载
        T value = dbFallback.get();
        
        // 5. 如果数据库也没有，缓存空值防止穿透
        if (value == null) {
            redisService.set(key, NULL_VALUE, NULL_EXPIRE);
            return null;
        }
        
        // 6. 缓存数据
        redisService.set(key, value, expireTime);
        return value;
    }

    /**
     * 获取缓存，如果不存在则从数据库加载并缓存（带分布式锁，防止缓存击穿）
     *
     * @param key        缓存 key
     * @param clazz      返回类型
     * @param expireTime 过期时间（秒）
     * @param dbFallback 数据库回调函数
     * @return 缓存值
     */
    public <T> T getOrLoadWithLock(String key, Class<T> clazz, long expireTime, Supplier<T> dbFallback) {
        // 1. 先从缓存获取
        Object cached = redisService.get(key);
        
        if (NULL_VALUE.equals(cached)) {
            return null;
        }
        
        if (cached != null && clazz.isInstance(cached)) {
            return clazz.cast(cached);
        }
        
        // 2. 尝试获取分布式锁
        String lockKey = RedisKeyUtil.getLockKey("cache", key.hashCode());
        String requestId = String.valueOf(Thread.currentThread().threadId());
        
        try {
            if (redisService.tryLock(lockKey, requestId, RedisExpireConstant.LOCK_DEFAULT_EXPIRE)) {
                // 3. 双重检查，防止重复加载
                cached = redisService.get(key);
                if (cached != null) {
                    if (NULL_VALUE.equals(cached)) {
                        return null;
                    }
                    if (clazz.isInstance(cached)) {
                        return clazz.cast(cached);
                    }
                }
                
                // 4. 从数据库加载
                T value = dbFallback.get();
                if (value == null) {
                    redisService.set(key, NULL_VALUE, NULL_EXPIRE);
                    return null;
                }
                
                redisService.set(key, value, expireTime);
                return value;
            } else {
                // 5. 获取锁失败，等待后重试
                Thread.sleep(50);
                return getOrLoad(key, clazz, expireTime, dbFallback);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取缓存被中断: {}", key, e);
            return dbFallback.get();
        } finally {
            redisService.releaseLock(lockKey, requestId);
        }
    }

    /**
     * 删除缓存
     *
     * @param key 缓存 key
     */
    public void evict(String key) {
        redisService.delete(key);
    }

    /**
     * 批量删除缓存（按前缀）
     *
     * @param prefix key 前缀
     * @return 删除的数量
     */
    public long evictByPrefix(String prefix) {
        return redisService.deleteByPrefix(prefix);
    }

    // ==================== 计数器操作 ====================

    /**
     * 增加计数
     *
     * @param key   计数器 key
     * @param delta 增量
     * @return 新值
     */
    public long incrementCount(String key, long delta) {
        return redisService.increment(key, delta);
    }

    /**
     * 减少计数
     *
     * @param key   计数器 key
     * @param delta 减量
     * @return 新值
     */
    public long decrementCount(String key, long delta) {
        return redisService.decrement(key, delta);
    }

    /**
     * 获取计数
     *
     * @param key 计数器 key
     * @return 计数值
     */
    public long getCount(String key) {
        Object value = redisService.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    /**
     * 设置计数（带过期时间）
     *
     * @param key        计数器 key
     * @param count      计数值
     * @param expireTime 过期时间（秒）
     */
    public void setCount(String key, long count, long expireTime) {
        redisService.set(key, count, expireTime);
    }

    // ==================== 点赞/收藏 Set 操作 ====================

    /**
     * 添加到用户操作集合（如点赞、收藏）
     *
     * @param key     集合 key
     * @param targetId 目标ID
     * @return 是否添加成功（false 表示已存在）
     */
    public boolean addToUserSet(String key, Long targetId) {
        long result = redisService.sAdd(key, targetId);
        return result > 0;
    }

    /**
     * 从用户操作集合移除
     *
     * @param key      集合 key
     * @param targetId 目标ID
     * @return 是否移除成功
     */
    public boolean removeFromUserSet(String key, Long targetId) {
        long result = redisService.sRemove(key, targetId);
        return result > 0;
    }

    /**
     * 检查是否在用户操作集合中
     *
     * @param key      集合 key
     * @param targetId 目标ID
     * @return 是否存在
     */
    public boolean isInUserSet(String key, Long targetId) {
        return redisService.sIsMember(key, targetId);
    }

    /**
     * 获取用户操作集合
     *
     * @param key 集合 key
     * @return 目标ID集合
     */
    public Set<Object> getUserSet(String key) {
        return redisService.sMembers(key);
    }

    // ==================== 热门排行 ZSet 操作 ====================

    /**
     * 更新热门排行分数
     *
     * @param key   排行榜 key
     * @param id    元素ID
     * @param score 分数
     */
    public void updateHotRank(String key, Object id, double score) {
        redisService.zAdd(key, id, score);
    }

    /**
     * 增加热门排行分数
     *
     * @param key   排行榜 key
     * @param id    元素ID
     * @param delta 增量
     * @return 新分数
     */
    public double incrementHotRank(String key, Object id, double delta) {
        return redisService.zIncrementScore(key, id, delta);
    }

    /**
     * 获取热门排行榜（降序）
     *
     * @param key   排行榜 key
     * @param start 起始位置
     * @param end   结束位置
     * @return 元素集合
     */
    public Set<Object> getHotRank(String key, long start, long end) {
        return redisService.zReverseRange(key, start, end);
    }

    /**
     * 从热门排行榜移除
     *
     * @param key 排行榜 key
     * @param id  元素ID
     */
    public void removeFromHotRank(String key, Object id) {
        redisService.zRemove(key, id);
    }

    // ==================== 限流操作 ====================

    /**
     * 检查是否允许访问（限流）
     *
     * @param api        接口标识
     * @param identifier 用户标识
     * @param period     时间窗口（秒）
     * @param maxCount   最大请求次数
     * @return 是否允许访问
     */
    public boolean isRateLimitAllowed(String api, String identifier, long period, long maxCount) {
        String key = RedisKeyUtil.getRateLimitKey(api, identifier);
        return redisService.isAllowed(key, period, maxCount);
    }

    /**
     * 记录登录失败次数
     *
     * @param identifier 标识
     * @return 当前失败次数
     */
    public long recordLoginFail(String identifier) {
        String key = RedisKeyUtil.getLoginFailCountKey(identifier);
        long count = redisService.increment(key);
        if (count == 1) {
            redisService.expire(key, RedisExpireConstant.LOGIN_LOCK_EXPIRE);
        }
        return count;
    }

    /**
     * 获取登录失败次数
     *
     * @param identifier 标识
     * @return 失败次数
     */
    public long getLoginFailCount(String identifier) {
        String key = RedisKeyUtil.getLoginFailCountKey(identifier);
        return getCount(key);
    }

    /**
     * 清除登录失败记录
     *
     * @param identifier 标识
     */
    public void clearLoginFail(String identifier) {
        String key = RedisKeyUtil.getLoginFailCountKey(identifier);
        redisService.delete(key);
    }
}
