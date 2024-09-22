package com.tplink.cdd.tpuc.wifimanagement.infra.migration.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/7
 */

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "aop-migration")
public class AopMigrationProps {
    private boolean kafka;
    private boolean skip;
    private String uuid;
    private String route;
    private String topic;
    private String groupId;
    public String match;
}
