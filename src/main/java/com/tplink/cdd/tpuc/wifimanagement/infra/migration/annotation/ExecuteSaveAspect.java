package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.CacheService;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.ExecuteMigration;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.PrometheusHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class ExecuteSaveAspect {

    @Autowired
    private ExecuteMigration properties; // 使用公共的配置类

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PrometheusHandler prometheusHandler; // 注入Prometheus预警处理器

    @Around("@annotation(ExecuteSave)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isOpen()) {
            return joinPoint.proceed(); // 如果配置未开启，继续执行业务逻辑
        }

        // 从MDC中获取配置中定义的uuid索引
        String uuid = MDC.get(properties.getUuid());
        if (uuid == null || uuid.isEmpty()) {
            log.error("UUID is missing from MDC with key '{}'. Proceeding with business logic.", properties.getUuid());
            return joinPoint.proceed(); // 如果 UUID 缺失，继续执行业务逻辑
        }

        // 定义缓存名称
        String cacheName = "ExecuteSaveCache";

        // 生成Redis的键，使用uuid作为键的一部分
        String keyInput = generateKey(uuid, "input");

        Object[] args = joinPoint.getArgs(); // 获取方法入参

        if ("master".equalsIgnoreCase(properties.getRole())) {
            if (!handleMasterRole(cacheName, keyInput, args)) {
                return joinPoint.proceed(); // 当不需要拦截时，继续执行业务逻辑
            }
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(cacheName, keyInput, args); // 直接返回null，不继续执行原方法
        }

        return joinPoint.proceed(); // 默认情况下，继续执行业务逻辑
    }

    private boolean handleMasterRole(String cacheName, String keyInput, Object[] args) {
        // 获取Redis中的输入值，指定类型为Object[]
        String argsDigest = generateArgsDigest(args); // 生成参数摘要
        String redisValue = cacheService.get(cacheName, keyInput, String.class);

        if (redisValue != null && !redisValue.equals(argsDigest)) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisValue, argsDigest);
            prometheusHandler.mismatch(keyInput, redisValue, argsDigest); // 调用预警
            return false; // 不拦截，继续执行业务逻辑
        }

        // 保存参数摘要到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInput, argsDigest, 1, TimeUnit.HOURS);

        // 返回 true 表示拦截，不继续执行业务逻辑
        return true;
    }

    private Object handleSlaveRole(String cacheName, String keyInput, Object[] args) {
        // 获取Redis中的输入值，指定类型为Object[]
        String argsDigest = generateArgsDigest(args); // 生成参数摘要
        String redisValue = cacheService.get(cacheName, keyInput, String.class);

        if (redisValue != null && !redisValue.equals(argsDigest)) {
            // 如果Redis中存在输入参数且不一致
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisValue, argsDigest);
            prometheusHandler.mismatch(keyInput, redisValue, argsDigest); // 调用预警
            return null; // 返回null，不执行原方法
        }

        // 保存参数摘要到缓存中, 不再执行业务逻辑
        log.info("No input found or input matches. Saving input to cache.");
        cacheService.set(cacheName, keyInput, argsDigest, 1, TimeUnit.HOURS); // 保存输入
        return null; // 不执行原方法，直接返回null
    }

    // 生成Redis键的方法，使用uuid和类型作为区分
    private String generateKey(String uuid, String type) {
        return uuid + ":" + type;
    }

    // 生成参数摘要的方法，可以使用自定义摘要算法或简单的字符串拼接
    private String generateArgsDigest(Object[] args) {
        // 可以根据实际需求生成摘要，例如用某种哈希算法或简单的字符串拼接
        return String.join(":", Arrays.stream(args)
                .map(Object::toString)
                .toArray(String[]::new));
    }
}
