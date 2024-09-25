package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.props.ExecuteSaveProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

@Aspect
@Component
@Slf4j
public class ExecuteSaveAspect {

    @Autowired
    private ExecuteSaveProperties properties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(ExecuteSave)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isOpen()) {
            return joinPoint.proceed(); // 如果配置未开启，直接执行原方法
        }

        Object[] args = joinPoint.getArgs();
        String key = generateKey(args); // 自定义方法，根据入参生成Redis键

        if ("master".equalsIgnoreCase(properties.getRole())) {
            return handleMasterRole(joinPoint, key, args);
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(key, args);
        }

        return null; // 如果角色不匹配，不执行任何逻辑
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String key, Object[] args) throws Throwable {
        Object redisValue = redisTemplate.opsForValue().get(key);
        if (redisValue != null && !redisValue.equals(args)) {
            log.warn("Mismatch detected for key: {}. Existing value: {}, New value: {}", key, redisValue, args);
            return null;
        }

        redisTemplate.opsForValue().set(key, args); // 保存入参到Redis
        return joinPoint.proceed(); // 执行原方法
    }

    private Object handleSlaveRole(String key, Object[] args) {
        Object redisValue = redisTemplate.opsForValue().get(key);
        if (redisValue != null && !redisValue.equals(args)) {
            log.warn("Mismatch detected for key: {}. Existing value: {}, New value: {}", key, redisValue, args);
        } else {
            redisTemplate.opsForValue().set(key, args); // 保存入参到Redis
        }
        return null; // 不执行原方法，直接返回null
    }

    private String generateKey(Object[] args) {
        // 自定义逻辑，根据入参生成Redis键
        return "unique-key-based-on-args";
    }
}
