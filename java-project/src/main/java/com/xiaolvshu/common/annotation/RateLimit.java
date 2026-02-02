package com.xiaolvshu.common.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 用于标注需要进行限流控制的接口
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key 前缀，默认使用方法名
     */
    String key() default "";

    /**
     * 时间窗口（秒）
     * 默认 60 秒
     */
    long period() default 60;

    /**
     * 时间窗口内最大请求次数
     * 默认 100 次
     */
    long maxCount() default 100;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /**
         * 根据 IP 限流
         */
        IP,
        /**
         * 根据用户 ID 限流
         */
        USER,
        /**
         * 全局限流
         */
        GLOBAL
    }
}
