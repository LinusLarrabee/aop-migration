package com.tplink.cdd.tpuc.wifimanagement.infra.migration.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "execute-check")
public class ExecuteCheckProperties {
    private boolean open;
    private String role;
    private long waitTime; // 等待时间
}
