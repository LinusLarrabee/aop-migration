package com.tplink.cdd.tpuc.wifimanagement.infra.migration.reflection;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.text.SimpleDateFormat;
import java.util.Date;

@Mapper
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mappings({
            @Mapping(target = "fullName", expression = "java(user.getFirstName() + ' ' + user.getLastName())"),
            @Mapping(target = "registrationDateString", source = "registrationDate", dateFormat = "yyyy-MM-dd")
    })
    UserDTO userToUserDTO(User user);

    // Helper method to format dates, if needed elsewhere
    default String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }


}