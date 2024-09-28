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
import java.util.Optional;
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
        // 如果isSaveSwitch未开启，继续执行业务逻辑
        if (!properties.isSaveSwitch()) {
            return joinPoint.proceed();
        }

        // 从MDC中获取配置中定义的uuid索引
        String uuid = MDC.get(properties.getUuid());
        if (uuid == null || uuid.isEmpty()) {
            log.error("UUID is missing from MDC with key '{}'. Proceeding with business logic.", properties.getUuid());
            return joinPoint.proceed(); // 如果 UUID 缺失，继续执行业务逻辑
        }

        Object[] args = joinPoint.getArgs(); // 获取方法入参
        String argsDigest = generateArgsDigest(args); // 生成详细参数摘要
        String simpleArgsDigest = generateSimpleArgsDigest(args); // 生成简单参数摘要

        // 获取当前方法和上一层调用者的方法信息
        String currentMethodInfo = getCurrentMethodInfo(joinPoint);
        String callerMethodInfo = getCallerMethodInfo().orElse("UnknownCaller");

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
            // 当tag为空时，使用当前方法信息和调用者方法信息作为key的一部分
            keyPart = currentMethodInfo + ":" + callerMethodInfo;
        }

        // 生成Redis键，加入简单参数摘要
        String masterInputKey = generateKey(uuid, "master", keyPart, simpleArgsDigest);
        String slaveInputKey = generateKey(uuid, "slave", keyPart, simpleArgsDigest);

        log.debug("masterInputKey{}",masterInputKey);
        // 使用配置中的缓存名称
        String cacheName = properties.getCacheName();

        // 根据角色区分逻辑
        if ("master".equalsIgnoreCase(properties.getKafkaRole())) {
            return handleMasterRole(joinPoint, cacheName, masterInputKey, slaveInputKey, argsDigest);
        } else if ("slave".equalsIgnoreCase(properties.getKafkaRole())) {
            return handleSlaveRole(cacheName, masterInputKey, slaveInputKey, argsDigest);
        }

        return joinPoint.proceed(); // 默认情况下，继续执行业务逻辑
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String cacheName, String masterInputKey, String slaveInputKey, String argsDigest) throws Throwable {
        // 检查 Redis 中是否有相同的 slave 输入参数
        String redisSlaveInput = cacheService.get(cacheName, slaveInputKey, String.class);
        if (redisSlaveInput != null && redisSlaveInput.equals(argsDigest)) {
            // 如果 slave 输入一致，调用匹配成功预警
            log.info("Match detected for slave input key: {}", slaveInputKey);
            prometheusHandler.match(); // 调用匹配预警
            return joinPoint.proceed(); // 继续执行业务逻辑
        } else if (redisSlaveInput != null) {
            // 如果 slave 输入不一致，调用不匹配预警
            log.warn("Mismatch detected for slave input key: {}. Existing value: {}, New value: {}", slaveInputKey, redisSlaveInput, argsDigest);
            prometheusHandler.migrationMismatch(); // 调用不匹配预警
            return joinPoint.proceed(); // 继续执行业务逻辑
        }

        // 检查 Redis 中是否有相同的 master 输入参数
        String redisMasterInput = cacheService.get(cacheName, masterInputKey, String.class);
        if (redisMasterInput != null && redisMasterInput.equals(argsDigest)) {
            // 如果 master 输入一致，调用匹配成功预警
            log.info("Match detected for master input key: {}", masterInputKey);
            prometheusHandler.match(); // 调用匹配预警
            return null; // 返回 null，不继续执行业务逻辑
        } else if (redisMasterInput != null) {
            // 如果 master 输入不一致，调用不匹配预警
            log.warn("Mismatch detected for master input key: {}. Existing value: {}, New value: {}", masterInputKey, redisMasterInput, argsDigest);
            prometheusHandler.migrationMismatch(); // 调用不匹配预警
            return null; // 返回null，不继续执行业务逻辑
        }

        // master 参数在 Redis 中不存在，保存并执行原方法
        cacheService.set(cacheName, masterInputKey, argsDigest, properties.getExpireTime(), TimeUnit.SECONDS); // 保存 master 输入到 Redis
        return joinPoint.proceed(); // 执行原方法
    }

    private Object handleSlaveRole(String cacheName, String masterInputKey, String slaveInputKey, String argsDigest) {
        // 检查 Redis 中是否有相同的 master 输入参数
        String redisMasterInput = cacheService.get(cacheName, masterInputKey, String.class);
        if (redisMasterInput != null && redisMasterInput.equals(argsDigest)) {
            // 如果 master 输入一致，调用匹配成功预警
            log.info("Match detected for master input key: {}", masterInputKey);
            prometheusHandler.match(); // 调用匹配预警
            return null; // 返回 null，不继续执行业务逻辑
        } else if (redisMasterInput != null) {
            // 如果 master 输入不一致，调用不匹配预警
            log.warn("Mismatch detected for master input key: {}. Existing value: {}, New value: {}", masterInputKey, redisMasterInput, argsDigest);
            prometheusHandler.migrationMismatch(); // 调用不匹配预警
            return null; // 返回null，不继续执行业务逻辑
        }

        // master 参数在 Redis 中不存在，保存 slave 输入到 Redis
        cacheService.set(cacheName, slaveInputKey, argsDigest, properties.getExpireTime(), TimeUnit.SECONDS); // 保存 slave 输入到 Redis

        // master 参数在 Redis 中不存在，等待指定时间后再次检查
        try {
            Thread.sleep(properties.getWaitTime()); // 等待配置的等待时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            log.error("Interrupted while waiting for master input key: {}", masterInputKey);
        }
        // 再次检查 Redis 中是否有相同的 master 输入参数
        redisMasterInput = cacheService.get(cacheName, masterInputKey, String.class);
        if (redisMasterInput == null) {
            // 等待后依然找不到 master 输入，触发 oneside 异常预警
            log.warn("One-side exception: master input key not found after waiting: {}", masterInputKey);
            prometheusHandler.migrationMismatch(); // 调用oneside异常预警
        }
        return null; // 不继续执行业务逻辑
    }

    // 生成Redis键的方法，使用uuid、类型、类名和方法名或tag、简单摘要作为区分
    private String generateKey(String uuid, String type, String keyPart, String simpleArgsDigest) {
        return uuid + ":" + type + ":" + keyPart + ":" + simpleArgsDigest;
    }

    // 生成详细参数摘要的方法，可以使用自定义摘要算法或简单的字符串拼接
    private String generateArgsDigest(Object[] args) {
        // 可以根据实际需求生成详细摘要，例如用某种哈希算法或简单的字符串拼接
        return String.join(":", Arrays.stream(args)
                .map(Object::toString)
                .toArray(String[]::new));
    }

    // 生成简单参数摘要的方法，可以使用自定义的快速摘要算法
    private String generateSimpleArgsDigest(Object[] args) {
        // 将所有参数拼接成一个字符串并生成MD5摘要
        String input = String.join(",", Arrays.stream(args)
                .map(Object::toString)
                .toArray(String[]::new));
        return HashUtils.generateSimpleHash(input);
    }

    // 获取当前方法的信息（类名和方法名）
    private String getCurrentMethodInfo(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return className + ":" + methodName;
    }

    // 获取上一层调用者的方法信息（类名和方法名）
    private Optional<String> getCallerMethodInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // 查找调用链中的上一层方法（去掉前几层与切面相关的方法调用）
        for (int i = 1; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            if (!element.getClassName().equals(this.getClass().getName()) &&
                    !element.getClassName().contains("CGLIB") &&
                    !element.getMethodName().equals("around")) {
                return Optional.of(element.getClassName() + ":" + element.getMethodName());
            }
        }
        return Optional.empty();
    }
}
