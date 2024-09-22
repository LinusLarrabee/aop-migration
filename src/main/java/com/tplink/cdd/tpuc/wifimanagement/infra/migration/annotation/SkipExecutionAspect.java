package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.AopMigrationService;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.ReflectionUtil;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.props.AopMigrationProps;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/6
 */

@Slf4j
@Aspect
@Component
public class SkipExecutionAspect {
    @Autowired
    AopMigrationService aopMigrationService;
    @Autowired
    AopMigrationProps aopMigrationProps;
    @Around("@annotation(SkipExecution)")
    public Object skipMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        try{
        if (MDC.get(aopMigrationProps.getUuid()).isEmpty()){
            throw new Exception("[SkipExecutionAspect]UUID Miss");
        }
        String route = null;
        if (MDC.get(aopMigrationProps.getUuid()).isEmpty()){
            throw new Exception("[SkipExecutionAspect]Migration Route Miss");
        }
        else route = MDC.get(aopMigrationProps.getRoute());
        if (aopMigrationProps.isKafka()){
            // 将入参写入到kafka
            Object[] args = joinPoint.getArgs();
            String id = MDC.get(aopMigrationProps.getUuid());
            log.info("[SkipExecutionAspect]migration uuid {}", id);
            aopMigrationService.ProcessData(aopMigrationProps.getTopic(), id,
                    route + Arrays.stream(args).map(ReflectionUtil::smartToString).collect(Collectors.joining(",")));
        }

        if (aopMigrationProps.isSkip()) {
            // 将目标函数跳过 当前判断不好，必然可用
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            log.info("[SkipExecutionAspect]migration skip execution {}", signature.getMethod().getName());
            return handleDefaultReturnValue(signature.getReturnType());
        }

        return joinPoint.proceed();
        }catch (Exception e) {
            log.error("[SkipExecutionAspect]Error during AOP processing",e);
            return joinPoint.proceed();
        }

    }

    private Object handleDefaultReturnValue(Class<?> returnType) {
        log.info("[SkipExecutionAspect] returnType{}", returnType.getName());
        return TypeDefaultValue.getDefault(returnType);
    }
}
