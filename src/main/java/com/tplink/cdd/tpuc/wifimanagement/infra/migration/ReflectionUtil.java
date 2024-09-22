package com.tplink.cdd.tpuc.wifimanagement.infra.migration;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.lang.reflect.Method;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/20
 */

public class ReflectionUtil {
    public static String smartToString(Object object) {
        if (object == null) {
            return "null";
        }
        if (isToStringMethodOverridden(object.getClass())) {
            return object.toString();
        } else {
            try {
                return ReflectionToStringBuilder.toString(object, ToStringStyle.SHORT_PREFIX_STYLE, false, false, true, Object.class);
            } catch (Exception e) {
                return "Exception in toString(): " + e.getMessage();
            }
        }
    }

    private static boolean isToStringMethodOverridden(Class<?> clazz) {
        try {
            Method toStringMethod = clazz.getMethod("toString");
            return !toStringMethod.getDeclaringClass().equals(Object.class);
        } catch (NoSuchMethodException e) {
            // Should not happen
            return false;
        }
    }
}