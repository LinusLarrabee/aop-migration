package com.tplink.cdd.tpuc.wifimanagement.infra.migration.cases;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.ExecutionVerify;
import org.springframework.stereotype.Service;

@Service
public class BusinessService {

    // 模拟一个需要被 AOP 拦截的方法
    // 使用自定义的 @ExecutionVerify 注解开启缓存
    @ExecutionVerify(keyPrefix = "data", expiration = 900) // 15分钟过期时间
    public String fetchData(String key) {
        // 模拟从数据库或其他数据源获取数据的过程
        return "data_from_source_" + System.currentTimeMillis();
    }
}
