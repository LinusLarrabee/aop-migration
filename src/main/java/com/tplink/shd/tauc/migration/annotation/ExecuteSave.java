package com.tplink.shd.tauc.migration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExecuteSave {
    String tag() default ""; // 新增tag字段，如果不提供则默认为空
}