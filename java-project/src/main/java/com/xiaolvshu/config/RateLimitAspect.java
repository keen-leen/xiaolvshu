package com.xiaolvshu.config;

import com.xiaolvshu.common.annotation.RateLimit;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.service.CacheService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面
 * 处理 @RateLimit 注解的接口限流
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final CacheService cacheService;

    @Around("@annotation(com.xiaolvshu.common.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        
        // 获取注解
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        
        // 构建限流 key
        String limitKey = buildLimitKey(rateLimit, method);
        
        // 获取限流标识
        String identifier = getIdentifier(rateLimit.limitType());
        
        // 检查是否允许访问
        boolean allowed = cacheService.isRateLimitAllowed(
                limitKey, 
                identifier, 
                rateLimit.period(), 
                rateLimit.maxCount()
        );
        
        if (!allowed) {
            log.warn("接口限流触发: key={}, identifier={}, period={}, maxCount={}", 
                    limitKey, identifier, rateLimit.period(), rateLimit.maxCount());
            throw new BusinessException(rateLimit.message());
        }
        
        return point.proceed();
    }

    /**
     * 构建限流 key
     */
    private String buildLimitKey(RateLimit rateLimit, Method method) {
        if (StringUtils.isNotBlank(rateLimit.key())) {
            return rateLimit.key();
        }
        // 默认使用 类名:方法名
        return method.getDeclaringClass().getSimpleName() + ":" + method.getName();
    }

    /**
     * 获取限流标识
     */
    private String getIdentifier(RateLimit.LimitType limitType) {
        return switch (limitType) {
            case IP -> getClientIp();
            case USER -> getUserId();
            case GLOBAL -> "global";
        };
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 多个代理的情况，取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }

    /**
     * 获取当前用户 ID
     */
    private String getUserId() {
        Long userId = UserContext.getUserId();
        return userId != null ? String.valueOf(userId) : getClientIp();
    }
}
