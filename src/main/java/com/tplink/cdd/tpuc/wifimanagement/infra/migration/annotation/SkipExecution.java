package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/6
 */

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipExecution {

}