package com.tplink.shd.tauc.migration.annotation;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Component
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "execute-migration")
public class ExecuteMigration {
    private boolean saveSwitch; // 控制保存逻辑的开关
    private String kafkaRole; // Kafka角色，可能用来区分master和slave
    private boolean xxl;
    private String xxlRole;
    private long waitTime; // 等待时间
    private long expireTime; // 缓存过期时间
    private String cacheName; // 缓存名称
    private String uuid; // UUID索引键
    private String kafkaTopics; // 用逗号分隔的Kafka topic列表

    // 获取Kafka topic列表，并将其转换为List
    public List<String> getKafkaTopicList() {
        return Stream.of(kafkaTopics.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
