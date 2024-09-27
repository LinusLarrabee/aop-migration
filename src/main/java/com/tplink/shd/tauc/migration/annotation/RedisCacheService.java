package com.tplink.shd.tauc.migration.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService implements CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void set(String cacheName, String key, Object value, long timeout, TimeUnit unit) {
        String redisKey = cacheName + "::" + key;
        redisTemplate.opsForValue().set(redisKey, value, timeout, unit);
    }

    @Override
    public <T> T get(String cacheName, String key, Class<T> clazz) {
        String redisKey = cacheName + "::" + key;
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value != null && clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return null; // 如果值不存在或者类型不匹配，返回null
    }
}
