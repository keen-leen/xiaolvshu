package com.xiaolvshu.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类
 * 配置 RedisTemplate 和 CacheManager
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 配置 RedisTemplate
     * 使用 String 序列化器处理 key，JSON 序列化器处理 value
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 创建 JSON 序列化器
        GenericJacksonJsonRedisSerializer jsonSerializer = createJsonSerializer();

        // key 使用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 使用 JSON 序列化
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置 Spring Cache 缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer jsonSerializer = createJsonSerializer();

        // 缓存配置
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 默认缓存过期时间：1小时
                .entryTtl(Duration.ofHours(1))
                // key 序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // value 序列化
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                // 不缓存 null 值
                .disableCachingNullValues();

        // 创建缓存管理器
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                // 可以为不同的缓存名称设置不同的配置
                .withCacheConfiguration("users", config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("posts", config.entryTtl(Duration.ofMinutes(15)))
                .withCacheConfiguration("hotPosts", config.entryTtl(Duration.ofMinutes(5)))
                .build();
    }

    /**
     * 创建 JSON 序列化器
     */
    public GenericJacksonJsonRedisSerializer createJsonSerializer() {
        /*
         * Spring Boot 4 以 Jackson 3 为默认 JSON 栈，Spring Data Redis 也提供了
         * 不带版本号的 GenericJacksonJsonRedisSerializer。这里保留多态类型信息，
         * 是因为 RedisTemplate<String, Object> 需要恢复不同业务 DTO；该序列化器只允许
         * 处理应用自己写入的 Redis 数据，不得用于反序列化用户直接提交的 JSON。
         */
        return GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();
    }
}
