package com.xiaolvshu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Spring MVC 异步请求执行器。
 *
 * <p>旅行 Agent 返回 {@code Flux<ServerSentEvent<?>>}，Spring MVC 会把响应写入工作交给
 * 异步执行器。框架默认的 SimpleAsyncTaskExecutor 不限制线程数量，在模型响应变慢或大量
 * SSE 连接并发时会持续创建线程。这里让线程和排队容量都与 Agent 的实例级并发上限绑定，
 * 从而使应用的成本保护与 Servlet 写出侧使用同一数量级的资源边界。</p>
 */
@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    private final int maxConcurrent;
    private final Duration requestTimeout;

    public WebMvcAsyncConfig(
            @Value("${app.agent.max-concurrent:8}") int maxConcurrent,
            @Value("${app.agent.run-timeout-seconds:120}") long runTimeoutSeconds) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
        // 给业务绝对截止信号留出发送终止 SSE 和释放资源的缓冲时间。
        this.requestTimeout = Duration.ofSeconds(Math.max(1, runTimeoutSeconds) + 10);
    }

    @Bean
    public AsyncTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mvc-sse-");
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(maxConcurrent * 2);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncTaskExecutor());
        configurer.setDefaultTimeout(requestTimeout.toMillis());
    }
}
