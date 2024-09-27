package com.tplink.shd.tauc.migration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/7
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InsertUniqueId {
    // todo 在可能的性能问题情况下选择不同入参作为比较
    String[] paramNames() default {};
    String[] fieldNames() default {};
}
