package com.xiaolvshu.config;

import com.xiaolvshu.common.annotation.DistributedLock;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.service.RedisService;
import com.xiaolvshu.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 分布式锁切面
 * 处理 @DistributedLock 注解的分布式锁
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedisService redisService;
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.xiaolvshu.common.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        
        // 获取注解
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);
        
        // 解析锁 key
        String lockKey = parseLockKey(distributedLock.key(), method, point.getArgs());
        String fullLockKey = RedisKeyUtil.getLockKey(lockKey);
        String requestId = UUID.randomUUID().toString();
        
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = tryAcquireLock(
                    fullLockKey, 
                    requestId, 
                    distributedLock.expireTime(),
                    distributedLock.waitTime()
            );
            
            if (!locked) {
                log.warn("获取分布式锁失败: key={}", fullLockKey);
                throw new BusinessException(distributedLock.message());
            }
            
            log.debug("获取分布式锁成功: key={}, requestId={}", fullLockKey, requestId);
            return point.proceed();
            
        } finally {
            if (locked) {
                boolean released = redisService.releaseLock(fullLockKey, requestId);
                if (released) {
                    log.debug("释放分布式锁成功: key={}", fullLockKey);
                } else {
                    log.warn("释放分布式锁失败: key={}，可能已过期", fullLockKey);
                }
            }
        }
    }

    /**
     * 尝试获取锁，支持等待重试
     */
    private boolean tryAcquireLock(String lockKey, String requestId, long expireTime, long waitTime) {
        // 首次尝试
        if (redisService.tryLock(lockKey, requestId, expireTime)) {
            return true;
        }
        
        // 如果不等待，直接返回失败
        if (waitTime <= 0) {
            return false;
        }
        
        // 等待重试
        long startTime = System.currentTimeMillis();
        long sleepTime = 50; // 每次等待 50ms
        
        while (System.currentTimeMillis() - startTime < waitTime) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            if (redisService.tryLock(lockKey, requestId, expireTime)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 解析锁 key（支持 SpEL 表达式）
     */
    private String parseLockKey(String keyExpression, Method method, Object[] args) {
        // 如果不包含 SpEL 表达式标识，直接返回
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }
        
        // 获取参数名称
        String[] parameterNames = nameDiscoverer.getParameterNames(method);
        if (parameterNames == null || parameterNames.length == 0) {
            return keyExpression;
        }
        
        // 构建 SpEL 上下文
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        
        try {
            Object value = parser.parseExpression(keyExpression).getValue(context);
            return value != null ? value.toString() : keyExpression;
        } catch (Exception e) {
            log.warn("解析锁 key 失败: {}, 使用原始值", keyExpression, e);
            return keyExpression;
        }
    }
}
