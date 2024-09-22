package com.tplink.cdd.tpuc.wifimanagement.infra.migration;

import com.tplink.cdd.tpuc.wifimanagement.port.eventbus.common.EventBusPublisher;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/7
 */

@Slf4j
@Service
public class AopMigrationService {
    @Autowired
    EventBusPublisher eventBusPublisher;

    public boolean ProcessData(@NonNull String topic, String messageId, @NonNull String messageContent){
        log.info("[AopMigrationService] topic, id & msg {},{},{}",topic, messageId,messageContent);
        eventBusPublisher.publishAsyn(topic, messageId, messageContent, messageId);
        log.info("[AopMigrationService] finish connect to kafka");
        return false;
    }

}
