package com.xiaolvshu.service;

import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.exception.AgentAccessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 旅行 Agent 专用的成本与并发保护层。
 * <p>
 * Redis 滑动窗口限制单个访问者在一分钟内的请求数；进程内信号量限制同时占用
 * 大模型和 SSE 连接的请求数。两者的目标不同，不能只依赖线程池队列间接限流。
 */
@Service
@Slf4j
public class AgentAccessGuard {

    private static final Pattern SAFE_IP = Pattern.compile("[0-9a-fA-F:.]{1,64}");

    private final CacheService cacheService;
    private final long periodSeconds;
    private final long anonymousLimit;
    private final long authenticatedLimit;
    private final boolean trustForwardedHeaders;
    private final Semaphore concurrentPermits;

    public AgentAccessGuard(
            CacheService cacheService,
            @Value("${app.agent.rate-limit.period-seconds:60}") long periodSeconds,
            @Value("${app.agent.rate-limit.anonymous-max:5}") long anonymousLimit,
            @Value("${app.agent.rate-limit.authenticated-max:20}") long authenticatedLimit,
            @Value("${app.agent.max-concurrent:8}") int maxConcurrent,
            @Value("${app.agent.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.cacheService = cacheService;
        this.periodSeconds = Math.max(1L, periodSeconds);
        this.anonymousLimit = Math.max(1L, anonymousLimit);
        this.authenticatedLimit = Math.max(1L, authenticatedLimit);
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.concurrentPermits = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    /**
     * 检查访问者额度并尝试获取一个并发许可。
     * Redis 故障时采用失败关闭，防止依赖异常绕过 AI 成本保护。
     */
    public Lease acquire(HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        boolean authenticated = userId != null;
        String identifier = authenticated? "user:" + userId : "ip:" + resolveClientIp(request);
        long limit = authenticated ? authenticatedLimit : anonymousLimit;

        final boolean allowed;
        try {
            allowed = cacheService.isRateLimitAllowed("travel-agent", identifier, periodSeconds, limit);
        } catch (RuntimeException e) {
            // 客户端只接收稳定的服务不可用提示；完整异常仅写入服务端日志，
            // 既便于区分连接故障、Lua 脚本错误等真实原因，也避免泄露 Redis 内部信息。
            log.error("Agent限流检查失败，已按失败关闭策略拒绝请求 - visitorType: {}",
                    authenticated ? "authenticated" : "anonymous", e);
            throw new AgentAccessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Agent限流服务暂时不可用，请稍后重试");
        }
        if (!allowed) {
            throw new AgentAccessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Agent请求过于频繁，请稍后重试");
        }
        if (!concurrentPermits.tryAcquire()) {
            throw new AgentAccessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Agent当前繁忙，请稍后重试");
        }
        return new Lease(concurrentPermits);
    }

    /**
     * 解析客户端 IP，优先使用 X-Forwarded-For 或 X-Real-IP。
     * <p>
     * 如果配置了信任代理头，则仅在 IP 格式安全时使用，否则返回 remoteAddr。
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String firstHop = forwarded.split(",", 2)[0].trim();
                if (SAFE_IP.matcher(firstHop).matches()) {
                    return firstHop;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && SAFE_IP.matcher(realIp.trim()).matches()) {
                return realIp.trim();
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress != null && SAFE_IP.matcher(remoteAddress).matches()
                ? remoteAddress : "unknown";
    }

    /** SSE 结束时释放的并发许可；多个结束回调并发触发时也只释放一次。 */
    public static final class Lease implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Lease(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
