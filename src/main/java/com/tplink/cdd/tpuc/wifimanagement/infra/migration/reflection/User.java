package com.tplink.cdd.tpuc.wifimanagement.infra.migration.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/4/12
 */
@AllArgsConstructor
@Setter
@Getter
public class User {
    private String firstName;
    private String lastName;
    private int age;
    private Date registrationDate;

    // getters and setters
}

