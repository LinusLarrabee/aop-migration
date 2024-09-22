package com.tplink.cdd.tpuc.wifimanagement.infra.migration;

import com.tplink.cdd.tpuc.wifimanagement.dao.ApWifiQualityDAO;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.InsertUniqueId;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.props.AopMigrationProps;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.reflection.User;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.reflection.UserDTO;
import com.tplink.cdd.tpuc.wifimanagement.infra.migration.reflection.UserMapper;
import com.tplink.cdd.tpuc.wifimanagement.port.grpc.WifiManagementNetworkClientServerGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/6
 */
@Slf4j
@RestController
@RequestMapping("/internal")
public class test {
    @Autowired
    SampleService sampleService;
    @Autowired
    ApWifiQualityDAO apWifiQualityDAO;
    @Autowired
    AopMigrationProps aopMigrationProps;
    @Autowired
    WifiManagementNetworkClientServerGrpcClient wifiManagementNetworkClientServerGrpcClient;
    @InsertUniqueId
    @PostMapping("/a")
    public  String akk(@RequestParam Integer a, @RequestParam int b){
        aopMigrationProps.setGroupId("a");
        String result = sampleService.methodToSkip(a,b);
        System.out.println(result);
        log.info(result);
        log.warn(aopMigrationProps.getTopic());
//        apWifiQualityDAO.save(new ApWifiQualityDO(), 2);


        result = sampleService.methodToExecute();
        System.out.println(result);
        return aopMigrationProps.getTopic();
    }

    @InsertUniqueId
    @PostMapping("/b")
    public void bkk(String a){
                User user = new User("John", "Doe", 30, new Date());
        UserDTO userDTO = UserMapper.INSTANCE.userToUserDTO(user);
        System.out.println(userDTO.getFullName()); // 输出 John Doe
        System.out.println(userDTO.getRegistrationDateString()); // 输出日期字符串
        wifiManagementNetworkClientServerGrpcClient.getMonitoringSetting(a);
    }
}
