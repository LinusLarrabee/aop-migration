package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

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
public class ExecuteCheckAspect {

    @Autowired
    private ExecuteMigration properties; // 使用公共的配置类

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PrometheusHandler prometheusHandler; // 注入Prometheus预警处理器

    @Around("@annotation(ExecuteCheck)")
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
        String cacheName = "ExecuteCheckCache";

        // 生成Redis的键，使用uuid作为键的一部分
        String keyInput = generateKey(uuid, "input");
        String keyInOut = generateKey(uuid, "inout");

        Object[] args = joinPoint.getArgs(); // 获取方法入参

        if ("master".equalsIgnoreCase(properties.getRole())) {
            return handleMasterRole(joinPoint, cacheName, keyInput, keyInOut, args);
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(cacheName, keyInput, keyInOut, args);
        }

        return joinPoint.proceed(); // 默认情况下，继续执行业务逻辑
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String cacheName, String keyInput, String keyInOut, Object[] args) throws Throwable {
        // 获取Redis中的输入值，指定类型为Object[]
        String argsDigest = generateArgsDigest(args); // 生成参数摘要
        String redisInput = cacheService.get(cacheName, keyInput, String.class);

        if (redisInput != null && !redisInput.equals(argsDigest)) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, argsDigest);
            prometheusHandler.mismatch(keyInput, redisInput, argsDigest); // 调用预警
        } else {
            // 保存输入到Redis, 设置过期时间为1小时
            cacheService.set(cacheName, keyInput, argsDigest, 1, TimeUnit.HOURS);
        }

        Object output = joinPoint.proceed(); // 执行原方法

        // 保存输入和输出到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInOut, new InOut(argsDigest, output), 1, TimeUnit.HOURS);

        return output;
    }

    private Object handleSlaveRole(String cacheName, String keyInput, String keyInOut, Object[] args) throws InterruptedException {
        // 获取Redis中的输入值，指定类型为Object[]
        String argsDigest = generateArgsDigest(args); // 生成参数摘要
        String redisInput = cacheService.get(cacheName, keyInput, String.class);
        InOut redisInOut = cacheService.get(cacheName, keyInOut, InOut.class);

        if (redisInput != null && redisInOut != null) {
            // 如果存在 input 和 in/out
            if (redisInput.equals(argsDigest)) {
                log.info("Input matches for key: {}. Returning cached output.", keyInput);
                return redisInOut.getOutput(); // 直接返回缓存的输出
            } else {
                log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, argsDigest);
                prometheusHandler.mismatch(keyInput, redisInput, argsDigest); // 调用预警
                return null; // 返回 null，不继续执行业务逻辑
            }
        } else if (redisInput != null) {
            // 仅存在 input，没有 in/out
            if (!redisInput.equals(argsDigest)) {
                log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, argsDigest);
                prometheusHandler.mismatch(keyInput, redisInput, argsDigest); // 调用预警
            }
            log.info("Only input found for key: {}. Waiting for output...", keyInput);
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = cacheService.get(cacheName, keyInOut, InOut.class);
            if (redisInOut == null) {
                log.error("One side alert: Output not found for input key: {}", keyInput);
            }
            return null;
        } else {
            // input 和 in/out 都不存在
            log.info("No input or in/out found. Saving input and waiting...");
            // 保存输入到Redis, 设置过期时间为1小时
            cacheService.set(cacheName, keyInput, argsDigest, 1, TimeUnit.HOURS);
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = cacheService.get(cacheName, keyInOut, InOut.class);
            if (redisInOut == null) {
                log.error("One side alert: No input and output found after waiting for key: {}", keyInput);
            }
            return null;
        }
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

    // 用于保存input和output的内部类
    private static class InOut {
        private final String input;
        private final Object output;

        public InOut(String input, Object output) {
            this.input = input;
            this.output = output;
        }

        public String getInput() {
            return input;
        }

        public Object getOutput() {
            return output;
        }

        @Override
        public String toString() {
            return "InOut{" +
                    "input='" + input + '\'' +
                    ", output=" + output +
                    '}';
        }
    }
}
