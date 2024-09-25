package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.props.ExecuteCheckProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class ExecuteCheckAspect {

    @Autowired
    private ExecuteCheckProperties properties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(ExecuteCheck)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isOpen()) {
            return joinPoint.proceed(); // 如果配置未开启，直接执行原方法
        }

        Object[] args = joinPoint.getArgs();
        String keyInput = generateKey(args); // 自定义方法，根据入参生成Redis键
        String keyInOut = generateInOutKey(args);

        if ("master".equalsIgnoreCase(properties.getRole())) {
            return handleMasterRole(joinPoint, keyInput, keyInOut, args);
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(keyInput, keyInOut, args);
        }

        return null; // 如果角色不匹配，不执行任何逻辑
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String keyInput, String keyInOut, Object[] args) throws Throwable {
        Object redisInput = redisTemplate.opsForValue().get(keyInput);

        if (redisInput != null && !redisInput.equals(args)) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
            return null;
        }

        redisTemplate.opsForValue().set(keyInput, args); // 保存输入到Redis

        Object output = joinPoint.proceed(); // 执行原方法

        redisTemplate.opsForValue().set(keyInOut, new InOut(args, output)); // 保存输入和输出到Redis

        return output;
    }

    private Object handleSlaveRole(String keyInput, String keyInOut, Object[] args) throws InterruptedException {
        Object redisInput = redisTemplate.opsForValue().get(keyInput);
        Object redisInOut = redisTemplate.opsForValue().get(keyInOut);

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
            redisInOut = redisTemplate.opsForValue().get(keyInOut);
            if (redisInOut == null) {
                log.error("One side alert: Output not found for input key: {}", keyInput);
            }
            return null;
        } else {
            // input 和 in/out 都不存在
            log.info("No input or in/out found. Saving input and waiting...");
            redisTemplate.opsForValue().set(keyInput, args); // 保存输入
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = redisTemplate.opsForValue().get(keyInOut);
            if (redisInOut == null) {
                log.error("One side alert: No input and output found after waiting for key: {}", keyInOut);
            }
            return null;
        }
    }

    private String generateKey(Object[] args) {
        // 自定义逻辑，根据入参生成Redis键（仅input部分）
        return "input-key-based-on-args";
    }

    private String generateInOutKey(Object[] args) {
        // 自定义逻辑，根据入参生成Redis键（input和output）
        return "in-out-key-based-on-args";
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
