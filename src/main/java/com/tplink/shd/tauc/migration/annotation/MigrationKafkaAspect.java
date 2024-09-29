package com.tplink.shd.tauc.migration.aspect;

import com.tplink.shd.tauc.migration.annotation.ExecuteMigration;
import com.tplink.shd.tauc.migration.annotation.ExecuteKafka;
import com.tplink.shd.tauc.migration.annotation.ExecuteSaveAspect;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.util.List;

@Aspect
@Component
@Slf4j
public class MigrationKafkaAspect {

    @Autowired
    private ExecuteMigration executeMigration; // 注入配置类

    @Autowired
    private ExecuteSaveAspect executeSaveAspect; // 注入复用的切面逻辑

    @Around("@annotation(com.tplink.shd.tauc.migration.annotation.ExecuteKafka)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        ExecuteKafka annotation = methodSignature.getMethod().getAnnotation(ExecuteKafka.class);

        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        if (args.length < 1 || !(args[0] instanceof String)) {
            log.warn("No topic parameter found in method call, proceeding with business logic.");
            return joinPoint.proceed(); // 如果没有topic参数，则继续执行业务逻辑
        }

        String methodTopic = (String) args[0]; // 从方法参数中获取topic

        // 获取配置文件中的Kafka topic列表
        List<String> topicList = executeMigration.getKafkaTopicList();

        // 判断方法参数中的topic是否在配置的topic列表中
        if (topicList.contains(methodTopic)) {
            log.info("Intercepted method with matching topic: {}. Proceeding with additional processing.", methodTopic);

            // 执行匹配的额外处理逻辑
            return processMatchingTopic(joinPoint, methodTopic, args);
        } else {
            log.info("Topic {} not in the configured list: {}. Skipping additional processing.", methodTopic, topicList);
            return joinPoint.proceed(); // 继续执行业务逻辑
        }
    }

    private Object processMatchingTopic(ProceedingJoinPoint joinPoint, String methodTopic, Object[] args) throws Throwable {
        String uuid = "kafka";

        // 生成详细参数摘要和简单参数摘要
        String argsDigest = executeSaveAspect.generateArgsDigest(args);
        String simpleArgsDigest = executeSaveAspect.generateSimpleArgsDigest(args);

        // 生成keyPart，包含类名和方法名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String keyPart = methodSignature.getDeclaringType().getSimpleName() + ":" + methodSignature.getName();

        // 生成Redis键，分别使用master和slave的标识，并带上类名和方法名或tag
        String masterInputKey = executeSaveAspect.generateKey(uuid, "master", keyPart, simpleArgsDigest);
        String slaveInputKey = executeSaveAspect.generateKey(uuid, "slave", keyPart, simpleArgsDigest);

        log.debug("Generated masterInputKey: {}", masterInputKey);

        // 使用配置中的缓存名称
        String cacheName = executeMigration.getCacheName();

        // 根据角色区分逻辑处理
        if ("master".equalsIgnoreCase(executeMigration.getKafkaRole())) {
            return executeSaveAspect.handleMasterRole(joinPoint, cacheName, masterInputKey, slaveInputKey, argsDigest);
        } else if ("slave".equalsIgnoreCase(executeMigration.getKafkaRole())) {
            return executeSaveAspect.handleSlaveRole(cacheName, masterInputKey, slaveInputKey, argsDigest);
        }

        return joinPoint.proceed(); // 默认情况下，继续执行业务逻辑
    }
}
