package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.cases.BusinessService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class CacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private BusinessService businessService;

    // 定义切面，拦截 BusinessService 的 fetchData 方法
    @Around("execution(* com.example.redisexample.service.BusinessService.fetchData(..)) && args(key)")
    public Object checkCache(ProceedingJoinPoint joinPoint, String key) throws Throwable {
        // 检查 Redis 中是否存在该键
        String cachedValue = redisTemplate.opsForValue().get(key);
        if (cachedValue != null) {
            // 模拟比对逻辑，可以替换为实际的比对规则
            String resultFromSource = (String) joinPoint.proceed();
            
            // 比对缓存和数据源结果是否一致
            if (!cachedValue.equals(resultFromSource)) {
                // 如果不一致，进行告警
                System.out.println("Warning: Cache data is different from source data!");
            }

            // 如果一致，直接返回缓存内容
            System.out.println("Using cached data for key: " + key);
            return cachedValue;
        }

        // 如果缓存中没有，则执行原业务方法，获取结果
        Object result = joinPoint.proceed();
        
        // 将结果存入 Redis 并设置过期时间（例如15分钟）
        redisTemplate.opsForValue().set(key, (String) result, 15, TimeUnit.MINUTES);

        return result;
    }
}
