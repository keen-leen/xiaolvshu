package com.xiaolvshu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作服务类
 * 提供常用的 Redis 操作方法封装
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== String 操作 ====================

    /**
     * 设置缓存
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置缓存并指定过期时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间（秒）
     */
    public void set(String key, Object value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存并指定过期时间
     *
     * @param key      键
     * @param value    值
     * @param timeout  过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 仅当 key 不存在时设置（分布式锁基础）
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间（秒）
     * @return 是否设置成功
     */
    public boolean setIfAbsent(String key, Object value, long timeout) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取缓存并转换类型
     *
     * @param key   键
     * @param clazz 目标类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 删除缓存
     *
     * @param key 键
     * @return 是否删除成功
     */
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 批量删除缓存
     *
     * @param keys 键集合
     * @return 删除的数量
     */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count != null ? count : 0;
    }

    /**
     * 根据前缀删除缓存
     *
     * @param prefix 前缀
     * @return 删除的数量
     */
    public long deleteByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            Long count = redisTemplate.delete(keys);
            return count != null ? count : 0;
        }
        return 0;
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 设置过期时间
     *
     * @param key     键
     * @param timeout 过期时间（秒）
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout) {
        Boolean result = redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取过期时间
     *
     * @param key 键
     * @return 过期时间（秒），-1 表示永不过期，-2 表示 key 不存在
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire : -2;
    }

    // ==================== 计数器操作 ====================

    /**
     * 递增
     *
     * @param key   键
     * @param delta 增量
     * @return 递增后的值
     */
    public long increment(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result != null ? result : 0;
    }

    /**
     * 递增1
     *
     * @param key 键
     * @return 递增后的值
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 减量
     * @return 递减后的值
     */
    public long decrement(String key, long delta) {
        Long result = redisTemplate.opsForValue().decrement(key, delta);
        return result != null ? result : 0;
    }

    /**
     * 递减1
     *
     * @param key 键
     * @return 递减后的值
     */
    public long decrement(String key) {
        return decrement(key, 1);
    }

    // ==================== Hash 操作 ====================

    /**
     * 设置 Hash 字段值
     *
     * @param key     键
     * @param hashKey Hash 字段
     * @param value   值
     */
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * 设置整个 Hash
     *
     * @param key 键
     * @param map Map
     */
    public void hSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 获取 Hash 字段值
     *
     * @param key     键
     * @param hashKey Hash 字段
     * @return 值
     */
    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 获取整个 Hash
     *
     * @param key 键
     * @return Map
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 删除 Hash 字段
     *
     * @param key      键
     * @param hashKeys Hash 字段
     * @return 删除的数量
     */
    public long hDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * 判断 Hash 字段是否存在
     *
     * @param key     键
     * @param hashKey Hash 字段
     * @return 是否存在
     */
    public boolean hHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * Hash 字段递增
     *
     * @param key     键
     * @param hashKey Hash 字段
     * @param delta   增量
     * @return 递增后的值
     */
    public long hIncrement(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    // ==================== List 操作 ====================

    /**
     * 从左边添加元素
     *
     * @param key   键
     * @param value 值
     * @return 列表长度
     */
    public long lLeftPush(String key, Object value) {
        Long result = redisTemplate.opsForList().leftPush(key, value);
        return result != null ? result : 0;
    }

    /**
     * 从右边添加元素
     *
     * @param key   键
     * @param value 值
     * @return 列表长度
     */
    public long lRightPush(String key, Object value) {
        Long result = redisTemplate.opsForList().rightPush(key, value);
        return result != null ? result : 0;
    }

    /**
     * 从左边弹出元素
     *
     * @param key 键
     * @return 值
     */
    public Object lLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从右边弹出元素
     *
     * @param key 键
     * @return 值
     */
    public Object lRightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取列表范围内的元素
     *
     * @param key   键
     * @param start 起始位置
     * @param end   结束位置（-1 表示到末尾）
     * @return 元素列表
     */
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 获取列表长度
     *
     * @param key 键
     * @return 长度
     */
    public long lSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size != null ? size : 0;
    }

    /**
     * 裁剪列表
     *
     * @param key   键
     * @param start 起始位置
     * @param end   结束位置
     */
    public void lTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    // ==================== Set 操作 ====================

    /**
     * 添加元素到 Set
     *
     * @param key    键
     * @param values 值
     * @return 添加的数量
     */
    public long sAdd(String key, Object... values) {
        Long result = redisTemplate.opsForSet().add(key, values);
        return result != null ? result : 0;
    }

    /**
     * 从 Set 移除元素
     *
     * @param key    键
     * @param values 值
     * @return 移除的数量
     */
    public long sRemove(String key, Object... values) {
        Long result = redisTemplate.opsForSet().remove(key, values);
        return result != null ? result : 0;
    }

    /**
     * 判断元素是否在 Set 中
     *
     * @param key   键
     * @param value 值
     * @return 是否存在
     */
    public boolean sIsMember(String key, Object value) {
        Boolean result = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取 Set 所有元素
     *
     * @param key 键
     * @return 元素集合
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 获取 Set 大小
     *
     * @param key 键
     * @return 大小
     */
    public long sSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size : 0;
    }

    // ==================== ZSet (有序集合) 操作 ====================

    /**
     * 添加元素到 ZSet
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     * @return 是否添加成功
     */
    public boolean zAdd(String key, Object value, double score) {
        Boolean result = redisTemplate.opsForZSet().add(key, value, score);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 从 ZSet 移除元素
     *
     * @param key    键
     * @param values 值
     * @return 移除的数量
     */
    public long zRemove(String key, Object... values) {
        Long result = redisTemplate.opsForZSet().remove(key, values);
        return result != null ? result : 0;
    }

    /**
     * 增加 ZSet 元素分数
     *
     * @param key   键
     * @param value 值
     * @param delta 增量
     * @return 新分数
     */
    public double zIncrementScore(String key, Object value, double delta) {
        Double result = redisTemplate.opsForZSet().incrementScore(key, value, delta);
        return result != null ? result : 0;
    }

    /**
     * 获取 ZSet 元素排名（升序）
     *
     * @param key   键
     * @param value 值
     * @return 排名
     */
    public Long zRank(String key, Object value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    /**
     * 获取 ZSet 元素排名（降序）
     *
     * @param key   键
     * @param value 值
     * @return 排名
     */
    public Long zReverseRank(String key, Object value) {
        return redisTemplate.opsForZSet().reverseRank(key, value);
    }

    /**
     * 获取 ZSet 范围内的元素（升序）
     *
     * @param key   键
     * @param start 起始位置
     * @param end   结束位置
     * @return 元素集合
     */
    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * 获取 ZSet 范围内的元素（降序）
     *
     * @param key   键
     * @param start 起始位置
     * @param end   结束位置
     * @return 元素集合
     */
    public Set<Object> zReverseRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * 获取 ZSet 大小
     *
     * @param key 键
     * @return 大小
     */
    public long zSize(String key) {
        Long size = redisTemplate.opsForZSet().size(key);
        return size != null ? size : 0;
    }

    /**
     * 获取 ZSet 元素分数
     *
     * @param key   键
     * @param value 值
     * @return 分数
     */
    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    // ==================== 分布式锁 ====================

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey    锁的 key
     * @param requestId  请求标识（用于释放锁时验证）
     * @param expireTime 锁过期时间（秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, String requestId, long expireTime) {
        return setIfAbsent(lockKey, requestId, expireTime);
    }

    /**
     * 释放分布式锁
     * 使用 Lua 脚本保证原子性
     *
     * @param lockKey   锁的 key
     * @param requestId 请求标识
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey, String requestId) {
        String script = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(lockKey), requestId);
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 执行 Lua 脚本并返回 Long 结果
     *
     * @param script Lua 脚本
     * @param keys   Redis keys
     * @param args   脚本参数
     * @return 返回结果（null 时返回 0）
     */
    public long evalLong(String script, List<String> keys, Object... args) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        RedisSerializer<Long> longSerializer = new GenericToStringSerializer<>(Long.class);
        String[] stringArgs = Arrays.stream(args)
                .map(String::valueOf)
                .toArray(String[]::new);
        // 显式转为 Object[]，表明这里要将每个字符串作为独立 ARGV 传入，
        // 同时避免 String[] 调用 Object... 时的歧义编译警告。
        Long result = redisTemplate.execute(redisScript, stringSerializer, longSerializer,
                keys, (Object[]) stringArgs);
        return result != null ? result : 0L;
    }

    // ==================== 限流操作 ====================

    /**
     * 简单滑动窗口限流
     * 指定 period 内同一个 key 的请求数不能超过 maxCount
     *
     * @param key       限流 key
     * @param period    时间窗口（秒）
     * @param maxCount  最大请求次数
     * @return 是否允许访问
     */
    public boolean isAllowed(String key, long period, long maxCount) {
        long currentTime = System.currentTimeMillis();
        // 移除过期时间在当前时间窗口之前的记录，并获取当前窗口内的请求数
        String script = """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])
                local limit = tonumber(ARGV[3])
                
                redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)
                local count = redis.call('ZCARD', key)
                
                if count < limit then
                    -- member 必须唯一。仅用毫秒时间戳时，同一毫秒的并发请求会相互覆盖，
                    -- 从而使滑动窗口少计数。由 Java 传入随机后缀可保留每一次请求。
                    redis.call('ZADD', key, now, ARGV[4])
                    redis.call('EXPIRE', key, window)
                    return 1
                else
                    return 0
                end
                """;
        // RedisTemplate 的默认 value serializer 是 JSON，若直接 execute，数字参数会被编码为
        // 带引号的 JSON 字符串，Lua 中 tonumber(ARGV[n]) 将得到 nil。统一经由
        // evalLong 的 StringRedisSerializer 传参，保证 Lua 收到纯文本数字。
        long result = evalLong(script,
                Collections.singletonList(key),
                currentTime,
                period,
                maxCount,
                currentTime + "-" + UUID.randomUUID());
        return result == 1L;
    }
}
