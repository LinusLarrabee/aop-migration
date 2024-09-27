package com.tplink.shd.tauc.migration.annotation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "execute-migration")
public class ExecuteMigration {
    private boolean kafka;
    private String kafkaRole;
    private boolean xxl;
    private String xxlRole;
    private long waitTime; // 等待时间
    private long expireTime;
    private String cacheName;
    private String uuid;
}