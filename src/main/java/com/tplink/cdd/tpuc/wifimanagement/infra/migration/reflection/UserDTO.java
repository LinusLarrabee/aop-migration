package com.tplink.cdd.tpuc.wifimanagement.infra.migration.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Setter
@Getter
public class UserDTO {
    private String fullName;
    private int age;
    private String registrationDateString;

    // getters and setters
}
