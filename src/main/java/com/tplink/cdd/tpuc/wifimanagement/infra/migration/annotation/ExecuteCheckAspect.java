package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.CacheService;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.ExecuteMigration;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext; // 或者使用org.slf4j.MDC
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class ExecuteCheckAspect {

    @Autowired
    private ExecuteMigration properties; // 使用公共的配置类

    @Autowired
    private CacheService cacheService;

    @Around("@annotation(ExecuteCheck)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isOpen()) {
            return joinPoint.proceed(); // 如果配置未开启，直接执行原方法
        }

        // 从MDC中获取uuid
        String uuid = ThreadContext.get("uuid"); // 或者使用 MDC.get("uuid");
        if (uuid == null || uuid.isEmpty()) {
            log.error("UUID is missing from MDC. Cannot proceed.");
            return null; // UUID缺失，无法继续执行
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

        return null; // 如果角色不匹配，不执行任何逻辑
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String cacheName, String keyInput, String keyInOut, Object[] args) throws Throwable {
        Object redisInput = cacheService.get(cacheName, keyInput);

        if (redisInput != null && !redisInput.equals(args)) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
            return null;
        }

        // 保存输入到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInput, args, 1, TimeUnit.HOURS);

        Object output = joinPoint.proceed(); // 执行原方法

        // 保存输入和输出到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInOut, new InOut(args, output), 1, TimeUnit.HOURS);

        return output;
    }

    private Object handleSlaveRole(String cacheName, String keyInput, String keyInOut, Object[] args) throws InterruptedException {
        Object redisInput = cacheService.get(cacheName, keyInput);
        Object redisInOut = cacheService.get(cacheName, keyInOut);

        if (redisInput != null && redisInOut != null) {
            // 如果存在 input 和 in/out
            if (redisInput.equals(args)) {
                InOut inOut = (InOut) redisInOut;
                log.info("Input matches, returning cached output: {}", inOut.getOutput());
                return inOut.getOutput();
            } else {
                log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
                return null;
            }
        } else if (redisInput != null) {
            // 仅存在 input，没有 in/out
            log.warn("Only input found for key: {}. Checking output after waiting...", keyInput);
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = cacheService.get(cacheName, keyInOut);
            if (redisInOut == null) {
                log.error("One side alert: Output not found for input key: {}", keyInput);
            }
            return null;
        } else {
            // input 和 in/out 都不存在
            log.info("No input or in/out found. Saving input and waiting...");
            // 保存输入到Redis, 设置过期时间为1小时
            cacheService.set(cacheName, keyInput, args, 1, TimeUnit.HOURS);
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = cacheService.get(cacheName, keyInOut);
            if (redisInOut == null) {
                log.error("One side alert: No input and output found after waiting for key: {}", keyInOut);
            }
            return null;
        }
    }

    // 生成Redis键的方法，使用uuid和类型作为区分
    private String generateKey(String uuid, String type) {
        return uuid + ":" + type;
    }

    // 用于保存input和output的内部类
    private static class InOut {
        private final Object input;
        private final Object output;

        public InOut(Object input, Object output) {
            this.input = input;
            this.output = output;
        }

        public Object getInput() {
            return input;
        }

        public Object getOutput() {
            return output;
        }

        @Override
        public String toString() {
            return "InOut{" +
                    "input=" + input +
                    ", output=" + output +
                    '}';
        }
    }
}
