package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import java.util.concurrent.TimeUnit;

public interface CacheService {
    void set(String cacheName, String key, Object value, long timeout, TimeUnit unit);
    Object get(String cacheName, String key);
}
