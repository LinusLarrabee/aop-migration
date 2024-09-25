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
public class ExecuteSaveAspect {

    @Autowired
    private ExecuteMigration properties; // 使用公共的配置类

    @Autowired
    private CacheService cacheService;

    @Around("@annotation(ExecuteSave)")
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
        String cacheName = "ExecuteSaveCache";

        // 生成Redis的键，使用uuid作为键的一部分
        String keyInput = generateKey(uuid, "input");

        Object[] args = joinPoint.getArgs(); // 获取方法入参

        if ("master".equalsIgnoreCase(properties.getRole())) {
            return handleMasterRole(joinPoint, cacheName, keyInput, args);
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(cacheName, keyInput, args);
        }

        return null; // 如果角色不匹配，不执行任何逻辑
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String cacheName, String keyInput, Object[] args) throws Throwable {
        Object redisInput = cacheService.get(cacheName, keyInput);

        if (redisInput != null && !redisInput.equals(args)) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
            return null;
        }

        // 保存输入到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInput, args, 1, TimeUnit.HOURS);

        Object output = joinPoint.proceed(); // 执行原方法

        // 保存输入和输出到Redis, 设置过期时间为1小时
        cacheService.set(cacheName, keyInput + ":output", output, 1, TimeUnit.HOURS);

        return output;
    }

    private Object handleSlaveRole(String cacheName, String keyInput, Object[] args) {
        Object redisInput = cacheService.get(cacheName, keyInput);
        Object redisOutput = cacheService.get(cacheName, keyInput + ":output");

        if (redisInput != null) {
            // 如果Redis中存在输入参数
            if (redisInput.equals(args)) {
                // 如果输入一致，直接返回缓存的输出
                log.info("Input matches, returning cached output: {}", redisOutput);
                return redisOutput;
            } else {
                log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
                return null;
            }
        } else {
            // input 不存在，保存 input 到 Redis
            log.info("No input found, saving input and returning null...");
            cacheService.set(cacheName, keyInput, args, 1, TimeUnit.HOURS); // 保存输入
            return null; // 不执行原方法
        }
    }

    // 生成Redis键的方法，使用uuid和类型作为区分
    private String generateKey(String uuid, String type) {
        return uuid + ":" + type;
    }
}
