package com.xiaolvshu.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisServiceRateLimitTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldSerializeLuaArgumentsAsPlainStrings() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                anyList(),
                any(Object[].class)))
                .thenAnswer(invocation -> {
                    RedisSerializer<Object> argsSerializer = invocation.getArgument(1);
                    List<String> keys = invocation.getArgument(3);
                    // Mockito 在 Invocation 中会将 Object... 展开为独立参数，
                    // 前四项分别是脚本、参数序列化器、结果序列化器和 keys。
                    Object[] args = Arrays.copyOfRange(invocation.getArguments(), 4,
                            invocation.getArguments().length);

                    assertTrue(keys.getFirst().startsWith("xiaolvshu:rate_limit:travel-agent:"));
                    assertArrayEquals("60".getBytes(StandardCharsets.UTF_8),
                            argsSerializer.serialize(args[1]));
                    assertArrayEquals("5".getBytes(StandardCharsets.UTF_8),
                            argsSerializer.serialize(args[2]));
                    assertFalse(new String(argsSerializer.serialize(args[1]), StandardCharsets.UTF_8)
                            .startsWith("\""));
                    return 1L;
                });

        RedisService redisService = new RedisService(redisTemplate);

        assertTrue(redisService.isAllowed("xiaolvshu:rate_limit:travel-agent:ip:127.0.0.1", 60, 5));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRejectWhenLuaReportsExhaustedQuota() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                anyList(),
                any(Object[].class)))
                .thenReturn(0L);

        RedisService redisService = new RedisService(redisTemplate);

        assertFalse(redisService.isAllowed("xiaolvshu:rate_limit:travel-agent:user:42", 60, 20));
    }
}
