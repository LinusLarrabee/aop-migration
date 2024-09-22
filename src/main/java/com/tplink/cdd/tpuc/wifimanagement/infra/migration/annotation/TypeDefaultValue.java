package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/6
 */
public enum TypeDefaultValue {
    INT(int.class, 0, "int"),INTCLASS(Integer.class, 0, "Integer"),
    DOUBLE(double.class,0.0f, "double"), DOUBLECLASS(Double.class, 0.0f, "Double"),
    BOOLEAN(boolean.class, false, "boolean"), BOOLEANCLASS(Boolean.class, false, "Boolean"),
    STRING(String.class, "", "String");

    private final Class<?> typeClass;
    private final Object defaultValue;
    private final String typeName;

    TypeDefaultValue(Class<?> typeClass, Object defaultValue, String typeName) {
        this.typeClass = typeClass;
        this.defaultValue = defaultValue;
        this.typeName = typeName;
    }

    public Class<?> getTypeClass() {
        return typeClass;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public static Object getDefault(Class<?> type){
        for (TypeDefaultValue value :values()) {
            if (value.getDefaultValue().equals(type)) {
                return value.getDefaultValue();
            }
        }
        return null;
    }

    public static Object getDefault(String type){
        for (TypeDefaultValue value :values()) {
            if (value.typeName.equalsIgnoreCase(type)) {
                return value.getDefaultValue();
            }
        }
        return null;
    }
}
