package com.tplink.shd.tauc.migration.annotation;

import com.tplink.shd.tauc.share.prometheus.PrometheusMetricMigrationSaveHandler;
import com.tplink.smb.component.cache.api.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.lang.reflect.Method;
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
    private PrometheusMetricMigrationSaveHandler prometheusHandler; // 注入Prometheus预警处理器

    @Around("@annotation(ExecuteSave)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isKafka()) {
            return joinPoint.proceed(); // 如果配置未开启，继续执行业务逻辑
        }

        // 从MDC中获取配置中定义的uuid索引
        String uuid = MDC.get(properties.getUuid());
        if (uuid == null || uuid.isEmpty()) {
            log.error("UUID is missing from MDC with key '{}'. Proceeding with business logic.", properties.getUuid());
            return joinPoint.proceed(); // 如果 UUID 缺失，继续执行业务逻辑
        }

        Object[] args = joinPoint.getArgs(); // 获取方法入参
        String argsDigest = generateArgsDigest(args); // 生成参数摘要

        // 获取@ExecuteSave注解的tag字段
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        ExecuteSave executeSaveAnnotation = method.getAnnotation(ExecuteSave.class);
        String tag = executeSaveAnnotation.tag();

        // 判断tag是否有值
        String keyPart;
        if (tag != null && !tag.isEmpty()) {
            keyPart = tag; // 使用自定义的tag
        } else {
            // 当tag为空时，获取类名和方法名作为key的一部分
            String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
            String methodName = joinPoint.getSignature().getName();
            keyPart = className + ":" + methodName;
        }

        // 生成Redis键，分别使用master和slave的标识，并带上类名和方法名或tag
        String masterInputKey = generateKey(uuid, "master", keyPart);
        String slaveInputKey = generateKey(uuid, "slave", keyPart);

        // 根据角色区分逻辑
        if ("master".equalsIgnoreCase(properties.getKafkaRole())) {
            return handleMasterRole(joinPoint, masterInputKey, slaveInputKey, argsDigest);
        } else if ("slave".equalsIgnoreCase(properties.getKafkaRole())) {
            return handleSlaveRole(masterInputKey, slaveInputKey, argsDigest);
        }

        return joinPoint.proceed(); // 默认情况下，继续执行业务逻辑
    }

    private boolean handleMasterRole(String cacheName, String keyInput, Object[] args) {
        // 获取Redis中的输入值，指定类型为Object[]
        String argsDigest = generateArgsDigest(args); // 生成参数摘要
        String redisValue = cacheService.get(cacheName, keyInput, String.class);

        if (redisValue != null && !redisValue.equals(argsDigest)) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisValue, argsDigest);
            prometheusHandler.migrationMismatch(); // 调用预警
            return false; // 不拦截，继续执行业务逻辑
        }

        log.debug("Check for input key: {}. Existing value: {}, New value: {}", keyInput, redisValue, argsDigest);
        // 保存参数摘要到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInput, argsDigest, properties.getExpireTime(), TimeUnit.SECONDS);

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
            prometheusHandler.migrationMismatch(); // 调用预警
            return null; // 返回null，不执行原方法
        }

        // 保存参数摘要到缓存中, 不再执行业务逻辑
        log.info("No input found or input matches. Saving input to cache.");
        cacheService.set(cacheName, keyInput, argsDigest, properties.getExpireTime(), TimeUnit.SECONDS);
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