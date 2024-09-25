package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "execute-migration")
public class ExecuteMigration {
    private boolean open;    // 控制AOP切面是否启用
    private String role;     // 指定当前实例的角色，如 master 或 slave
    private long waitTime;   // 在 slave 角色中等待时间，单位为毫秒
    private String uuid;     // 配置文件中的uuid索引，作为MDC的key
}
