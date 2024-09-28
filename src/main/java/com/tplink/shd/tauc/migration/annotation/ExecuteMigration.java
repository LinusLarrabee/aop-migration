package com.tplink.shd.tauc.migration.annotation;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Data
@Component
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "execute-migration")
public class ExecuteMigration {
    private boolean saveSwitch; // 替换原来的isKafka
    private String kafkaRole; // Kafka角色，可能用来区分master和slave
    private boolean xxl;
    private String xxlRole;
    private long waitTime; // 等待时间
    private long expireTime; // 缓存过期时间
    private String cacheName; // 缓存名称
    private String uuid; // UUID索引键
}
