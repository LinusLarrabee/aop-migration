package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    private static final String NULL_VALUE = "__NULL__"; // 定义一个特殊字符串作为 null 的替代值

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 从 Redis 中获取值，识别 null 替代值
    public String getValue(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (NULL_VALUE.equals(value)) {
            return null; // 识别到 null 替代值，返回 null
        }
        return value;
    }

    // 将数据存入 Redis，并识别 null 值
    public void setValue(String key, Object value, long expiration) {
        if (value == null) {
            // 如果值为 null，则存储 null 的替代值
            redisTemplate.opsForValue().set(key, NULL_VALUE, expiration, TimeUnit.SECONDS);
        } else {
            // 存储非 null 的实际值
            redisTemplate.opsForValue().set(key, value.toString(), expiration, TimeUnit.SECONDS);
        }
    }

    // 删除键
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }
}
