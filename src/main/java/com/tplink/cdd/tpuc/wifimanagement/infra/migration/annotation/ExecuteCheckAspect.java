import com.tplink.cdd.tpuc.wifimanagement.infra.migration.props.ExecuteCheckProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;
import org.apache.logging.log4j.ThreadContext; // 或使用 org.slf4j.MDC
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class ExecuteCheckAspect {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ExecuteCheckProperties properties;

    @Autowired
    private PrometheusMetricQoeReportCheckHandler prometheusHandler;

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

        // 生成Redis键，使用uuid作为区分
        String keyInput = generateKey(uuid, "input");
        String keyInOut = generateKey(uuid, "inout");

        // 获取方法入参
        Object[] args = joinPoint.getArgs();

        if ("master".equalsIgnoreCase(properties.getRole())) {
            return handleMasterRole(joinPoint, keyInput, keyInOut, args);
        } else if ("slave".equalsIgnoreCase(properties.getRole())) {
            return handleSlaveRole(keyInput, keyInOut, args);
        }

        return null;
    }

    private Object handleMasterRole(ProceedingJoinPoint joinPoint, String keyInput, String keyInOut, Object[] args) throws Throwable {
        String redisInput = (String) redisTemplate.opsForValue().get(keyInput);

        if (redisInput != null && !redisInput.equals(convertToJson(args))) {
            log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
            // 触发 Prometheus 预警
            prometheusHandler.qoeApFailedInc();
            return null;
        }

        redisTemplate.opsForValue().set(keyInput, convertToJson(args)); // 保存输入到Redis
        log.info("Saved input to Redis for key: {}", keyInput);

        Object output = joinPoint.proceed(); // 执行原方法

        redisTemplate.opsForValue().set(keyInOut, convertToJson(new InOut(args, output)), properties.getExpireTime(), TimeUnit.SECONDS); // 保存输入和输出到Redis
        log.info("Saved in/out to Redis for key: {}", keyInOut);

        return output;
    }

    private Object handleSlaveRole(String keyInput, String keyInOut, Object[] args) throws InterruptedException {
        String redisInput = (String) redisTemplate.opsForValue().get(keyInput);
        String redisInOut = (String) redisTemplate.opsForValue().get(keyInOut);

        if (redisInput != null && redisInOut != null) {
            // 如果存在 input 和 in/out
            if (redisInput.equals(convertToJson(args))) {
                InOut inOut = convertFromJson(redisInOut, InOut.class);
                log.info("Input matches, returning cached output: {}", inOut.getOutput());
                return inOut.getOutput();
            } else {
                log.warn("Mismatch detected for input key: {}. Existing value: {}, New value: {}", keyInput, redisInput, args);
                // 触发 Prometheus 预警
                prometheusHandler.qoeClientFailedInc();
                return null;
            }
        } else if (redisInput != null) {
            // 仅存在 input，没有 in/out
            log.warn("Only input found for key: {}. Checking output after waiting...", keyInput);
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = (String) redisTemplate.opsForValue().get(keyInOut);
            if (redisInOut == null) {
                log.error("One side alert: Output not found for input key: {}", keyInput);
            }
            return null;
        } else {
            // input 和 in/out 都不存在
            log.info("No input or in/out found. Saving input and waiting...");
            redisTemplate.opsForValue().set(keyInput, convertToJson(args)); // 保存输入
            Thread.sleep(properties.getWaitTime()); // 等待固定时间
            redisInOut = (String) redisTemplate.opsForValue().get(keyInOut);
            if (redisInOut == null) {
                log.error("One side alert: No input and output found after waiting for key: {}", keyInOut);
            }
            return null;
        }
    }

    private String generateKey(String uuid, String type) {
        return "executeCheck:" + uuid + ":" + type;
    }

    private String convertToJson(Object obj) {
        // 使用Gson或者Jackson将对象转为JSON字符串
        return new Gson().toJson(obj);
    }

    private <T> T convertFromJson(String json, Class<T> clazz) {
        // 使用Gson或者Jackson将JSON字符串转为对象
        return new Gson().fromJson(json, clazz);
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
