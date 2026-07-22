package com.xiaolvshu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 执行资源的统一配置。
 *
 * <p>线程池由 Spring 管理并在应用停止时关闭，避免在 Service 中直接 new 线程池
 * 导致测试泄漏和优雅停机失效。Agent 主线程数与并发许可保持一致，
 * 因此不再让已经获得许可的 SSE 请求长时间排队。</p>
 */
@Configuration
public class AgentExecutionConfig {

    @Bean(name = "travelAgentStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService travelAgentStreamExecutor(
            @Value("${app.agent.max-concurrent:8}") int maxConcurrent) {
        int size = Math.max(1, maxConcurrent);
        /*
         * SynchronousQueue 不保存等待任务：访问控制层既然已按相同 max-concurrent 发放许可，
         * 正常请求应立即获得线程；若状态竞态或配置不一致，则快速拒绝并返回 AGENT_BUSY，
         * 而不是让已经建立的 SSE 长连接在无界队列里静默等待。
         */
        return new ThreadPoolExecutor(
                size,
                size,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                namedThreadFactory("travel-agent-stream-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "travelAgentToolExecutor", destroyMethod = "shutdown")
    public ExecutorService travelAgentToolExecutor(
            @Value("${app.agent.max-concurrent:8}") int maxConcurrent) {
        int core = Math.max(2, maxConcurrent);
        /*
         * 一轮模型决策最多可并行提出多个工具调用，工具池峰值因此允许达到 Agent 并发数的两倍。
         * 100 个等待位是保护外部 RAG/Embedding 依赖的硬边界；满载时拒绝策略会让当前 Agent
         * 进入统一失败路径，不会继续堆积内存和不可控的外部请求。
         */
        return new ThreadPoolExecutor(
                core,
                Math.max(core, core * 2),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                namedThreadFactory("travel-agent-tool-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "travelAgentScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService travelAgentScheduler() {
        // 心跳任务和总超时任务可能同时到期，两个线程可避免某个慢回调延迟另一类生命周期事件。
        return Executors.newScheduledThreadPool(2, namedThreadFactory("travel-agent-timer-"));
    }

    /** 统一线程命名便于日志和线程转储定位；守护线程只是最后保险，正常停机仍由 Spring 调用 shutdown。 */
    private java.util.concurrent.ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
