package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import org.springframework.stereotype.Component;

@Component
public class PrometheusHandler {

    public void mismatch(String key, String existingValue, String newValue) {
        // 实现预警逻辑，例如推送到Prometheus或者其他监控系统
        System.out.printf("Mismatch detected! Key: %s, Existing: %s, New: %s%n", key, existingValue, newValue);
        // 你可以在这里调用Prometheus的API或其他预警机制
    }
}
