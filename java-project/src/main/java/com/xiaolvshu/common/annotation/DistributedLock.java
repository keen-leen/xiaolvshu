package com.xiaolvshu.common.annotation;

import java.lang.annotation.*;

/**
 * 分布式锁注解
 * 用于标注需要加分布式锁的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的 key 前缀
     * 支持 SpEL 表达式，如 "#userId"
     */
    String key();

    /**
     * 锁过期时间（秒）
     * 默认 30 秒
     */
    long expireTime() default 30;

    /**
     * 获取锁等待时间（毫秒）
     * 默认 3000 毫秒
     * 设为 0 表示不等待
     */
    long waitTime() default 3000;

    /**
     * 获取锁失败时的提示信息
     */
    String message() default "系统繁忙，请稍后再试";
}
