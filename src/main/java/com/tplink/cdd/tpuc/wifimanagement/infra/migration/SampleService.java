package com.tplink.cdd.tpuc.wifimanagement.infra.migration;

import com.tplink.cdd.tpuc.wifimanagement.infra.migration.annotation.SkipExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/6
 */


@Slf4j
@Service
public class SampleService {
    @SkipExecution
    public  String methodToSkip(Integer a, int b){
        System.out.println(a+b);
        return "this message should not be shown.";
    }

    public String methodToExecute(){
        return "Executed";
    }



}
