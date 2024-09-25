import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;
import org.apache.logging.log4j.ThreadContext; // 或使用 org.slf4j.MDC

@Aspect
@Component
@Slf4j
public class ExecuteSaveAspect {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ExecuteSaveProperties properties;

    @Autowired
    private PrometheusMetricQoeReportCheckHandler prometheusHandler;

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

        // 使用uuid作为Redis键
        String key = generateKey(uuid);

        // 获取方法入参
        Object[] args = joinPoint.getArgs();

        if ("master".equalsIgnoreCase(properties.getRole())) {
            return handleMasterRole(joinPoint, key, args);
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(key, args);
        }

        return null;
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String key, Object[] args) throws Throwable {
        String redisValue = (String) redisTemplate.opsForValue().get(key);

        if (redisValue != null) {
            log.warn("Mismatch detected for key: {}. Existing value: {}, New value: {}", key, redisValue, args);
            // 触发 Prometheus 预警
            prometheusHandler.qoeApFailedInc();
            return null;
        }

        // 将入参保存为JSON字符串
        String jsonArgs = convertToJson(args);
        redisTemplate.opsForValue().set(key, jsonArgs); // 保存入参到Redis
        log.info("Saved input to Redis for key: {}", key);

        // 执行原方法
        return joinPoint.proceed();
    }

    private Object handleSlaveRole(String key, Object[] args) {
        String redisValue = (String) redisTemplate.opsForValue().get(key);

        if (redisValue != null) {
            // 比较输入是否匹配
            if (redisValue.equals(convertToJson(args))) {
                log.info("Input matches for key: {}. Returning cached value.", key);
                return null; // 不执行原方法，直接返回null
            } else {
                log.warn("Mismatch detected for key: {}. Existing value: {}, New value: {}", key, redisValue, args);
                // 触发 Prometheus 预警
                prometheusHandler.qoeClientFailedInc();
                return null;
            }
        }

        // 将入参保存为JSON字符串
        redisTemplate.opsForValue().set(key, convertToJson(args));
        log.info("Saved input to Redis for key: {}", key);

        return null; // 不执行原方法
    }

    private String generateKey(String uuid) {
        return "executeSave:" + uuid;
    }

    private String convertToJson(Object[] args) {
        // 使用Gson或者Jackson将对象转为JSON字符串
        return new Gson().toJson(args);
    }
}
