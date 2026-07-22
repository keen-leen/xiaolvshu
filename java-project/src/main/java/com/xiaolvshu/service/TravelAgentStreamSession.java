package com.xiaolvshu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次 Agent SSE 运行的生命周期容器。
 *
 * <p>心跳、总超时、客户端断开和正常完成都可能同时发生，因此所有
 * 终止路径必须通过同一个原子状态收口。这保证并发许可只归还一次，
 * 同时尝试取消模型流和工具 Future。</p>
 */
@Slf4j
final class TravelAgentStreamSession {

    private final String runId = UUID.randomUUID().toString();
    private final long startedNanos = System.nanoTime();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<Future<?>> rootTask = new AtomicReference<>();
    private final AtomicReference<Future<?>> childTask = new AtomicReference<>();
    private final SseEmitter emitter;
    private final AgentAccessGuard.Lease lease;
    private final ScheduledFuture<?> heartbeatTask;
    private final ScheduledFuture<?> timeoutTask;

    TravelAgentStreamSession(AgentAccessGuard.Lease lease,
                             ScheduledExecutorService scheduler,
                             long heartbeatSeconds,
                             long timeoutSeconds) {
        this.lease = Objects.requireNonNull(lease);
        long safeTimeoutSeconds = Math.max(1L, timeoutSeconds);
        this.emitter = new SseEmitter(TimeUnit.SECONDS.toMillis(safeTimeoutSeconds));
        /*
         * Servlet 容器可能以 completion、timeout、error 中任意一种或多种回调报告同一次断连。
         * 所有回调都进入 close，最终由 closed.compareAndSet 保证任务取消和许可释放只发生一次。
         */
        this.emitter.onCompletion(() -> close(false));
        this.emitter.onTimeout(() -> close(true));
        this.emitter.onError(ignored -> close(true));

        long safeHeartbeatSeconds = Math.max(1L, heartbeatSeconds);
        this.heartbeatTask = scheduler.scheduleAtFixedRate(
                this::heartbeat, safeHeartbeatSeconds, safeHeartbeatSeconds, TimeUnit.SECONDS);
        this.timeoutTask = scheduler.schedule(this::timeout, safeTimeoutSeconds, TimeUnit.SECONDS);
    }

    String runId() {
        return runId;
    }

    SseEmitter emitter() {
        return emitter;
    }

    long elapsedMs() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    boolean isClosed() {
        return closed.get();
    }

    void rootTask(Future<?> task) {
        // submit 与任务完成存在竞态：若会话已经关闭，新登记的 Future 必须立即取消，不能遗留后台模型调用。
        Future<?> previous = rootTask.getAndSet(task);
        if (closed.get() && task != null) {
            task.cancel(true);
        } else if (previous != null && previous != task && !previous.isDone()) {
            previous.cancel(true);
        }
    }

    void childTask(Future<?> task) {
        // 单独记录工具子任务，使 SSE 断开时既能中断 Agent 主循环，也能尽快取消正在等待的外部工具。
        Future<?> previous = childTask.getAndSet(task);
        if (closed.get() && task != null) {
            task.cancel(true);
        } else if (previous != null && previous != task && !previous.isDone()) {
            previous.cancel(true);
        }
    }

    void clearChildTask(Future<?> task) {
        childTask.compareAndSet(task, null);
    }

    /**
     * 串行发送一个带递增 id 的业务事件。心跳和模型 token 来自不同线程，必须同步写 emitter，
     * 否则事件边界可能交错；返回 false 表示调用方应停止继续生产 token。
     */
    synchronized boolean send(String eventName, String data) {
        if (closed.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(sequence.incrementAndGet()))
                    .name(eventName == null ? "message" : eventName)
                    .data(data == null ? "" : data));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Agent SSE发送失败, runId={}: {}", runId, e.getMessage());
            close(true);
            return false;
        }
    }

    /** 正常完成路径：取消定时器、释放许可并结束响应，但不反向取消当前已完成的根任务。 */
    synchronized void complete() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimersAndTask(false);
        lease.close();
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Servlet 容器可能已经完成该异步请求。
        }
    }

    private synchronized void heartbeat() {
        if (closed.get()) {
            return;
        }
        try {
            // SSE 注释不会进入前端业务事件，但能刷新代理空闲计时并探测断连。
            emitter.send(SseEmitter.event()
                    .id(Long.toString(sequence.incrementAndGet()))
                    .comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            log.debug("Agent SSE心跳失败, runId={}: {}", runId, e.getMessage());
            close(true);
        }
    }

    private void timeout() {
        if (closed.get()) {
            return;
        }
        // 先尽力发送结构化错误，再关闭并取消任务；反过来会导致 emitter 已关闭而丢失可读终态。
        send("error", "{\"code\":\"RUN_TIMEOUT\",\"message\":\"Agent运行超时，请缩小问题范围后重试\",\"retryable\":true,\"runId\":\""
                + runId + "\"}");
        close(true);
    }

    private void close(boolean cancelTask) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimersAndTask(cancelTask);
        lease.close();
        if (cancelTask) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // emitter 可能已被 Servlet 容器关闭。
            }
        }
    }

    private void cancelTimersAndTask(boolean cancelTask) {
        heartbeatTask.cancel(false);
        timeoutTask.cancel(false);
        Future<?> root = rootTask.getAndSet(null);
        Future<?> child = childTask.getAndSet(null);
        if (cancelTask && child != null && !child.isDone()) {
            child.cancel(true);
        }
        if (cancelTask && root != null && !root.isDone()) {
            root.cancel(true);
        }
    }
}
