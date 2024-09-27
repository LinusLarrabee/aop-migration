package com.tplink.smb.component.cache.api;
import java.util.concurrent.TimeUnit;


public interface CacheService {
    void set(String cacheName, String key, Object value, long timeout, TimeUnit unit);
    <T> T get(String cacheName, String key, Class<T> clazz);
}
