package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class ExecutionVerifyAspect {

    @Autowired
    private RedisUtil redisUtil;

    @Around("@annotation(executionVerify)")
    public Object around(ProceedingJoinPoint joinPoint, ExecutionVerify executionVerify) throws Throwable {
        // 获取方法名和类名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // 从 MDC 中获取 uuid 和 count
        String uuid = MDC.get("uuid");
        String countStr = MDC.get("count");

        if (uuid == null || countStr == null) {
            throw new IllegalStateException("MDC does not contain uuid or count");
        }

        int count = Integer.parseInt(countStr);

        // 更新 count 值
        count++;
        MDC.put("count", String.valueOf(count));

        // 生成 Redis 键，格式为 uuid + count
        String redisKey = uuid + "_" + count;

        // 检查 Redis 中是否存在该键值
        String cachedResult = redisUtil.getValue(redisKey);

        // 获取方法输入参数
        Object[] args = joinPoint.getArgs();
        String inputHash = Arrays.toString(args).hashCode() + ""; // 简单计算输入的 hash 值作为标识

        if (cachedResult != null) {
            // 如果缓存中存在该值，解析缓存内容
            String[] cachedData = cachedResult.split(":");
            String cachedInputHash = cachedData[0]; // 缓存中的输入 hash 值
            String cachedOutput = cachedData[1]; // 缓存中的输出值

            if (cachedInputHash.equals(inputHash)) {
                // 输入一致，返回缓存的输出
                System.out.println("Cache hit! Returning cached result.");
                return cachedOutput;
            } else {
                // 输入不一致，告警
                System.out.println("Warning: Cache input does not match! Potential issue.");
            }
        }

        // 如果缓存中没有该值，执行原方法
        Object result = joinPoint.proceed();

        // 将输入、输出以及 uuid+count 写入 Redis
        String outputValue = result.toString();
        String redisValue = inputHash + ":" + outputValue;
        redisUtil.setValue(redisKey, redisValue, 3600); // 1小时过期时间

        return result;
    }
}
