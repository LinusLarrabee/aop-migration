package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 从 Redis 中获取值
    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 将数据存入 Redis 并设置过期时间
    public void setValue(String key, String value, long expiration) {
        redisTemplate.opsForValue().set(key, value, expiration, TimeUnit.SECONDS);
    }

    // 删除键
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }
}
