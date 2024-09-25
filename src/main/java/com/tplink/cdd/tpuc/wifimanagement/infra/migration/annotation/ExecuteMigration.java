package com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "execute-migration")
public class ExecuteMigration {
    private boolean open;
    private String role;
    private long waitTime; // 等待时间，以毫秒为单位
}
