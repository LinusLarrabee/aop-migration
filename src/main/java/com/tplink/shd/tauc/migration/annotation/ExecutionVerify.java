package com.tplink.shd.tauc.migration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) // 运行时可见
@Target(ElementType.METHOD) // 目标为方法
public @interface ExecutionVerify {

    // Redis 缓存的键值前缀
    String keyPrefix() default "";

    // 缓存过期时间，单位秒，默认15分钟
    long expiration() default 900;
}
