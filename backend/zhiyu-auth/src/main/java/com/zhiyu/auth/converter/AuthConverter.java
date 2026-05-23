package com.zhiyu.auth.converter;

import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.ufp.auth.entity.AuthUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AuthConverter {

    AuthConverter INSTANCE = Mappers.getMapper(AuthConverter.class);

    @Mapping(target = "authUserId", ignore = true)
    @Mapping(target = "authUserCode", ignore = true)
    @Mapping(target = "authUserNick", source = "username")
    @Mapping(target = "authUserUsername", source = "username")
    @Mapping(target = "authUserUsernameLoginEnable", constant = "1")
    @Mapping(target = "authUserMail", source = "email")
    @Mapping(target = "authUserMailVerified", constant = "0")
    @Mapping(target = "authUserMailLoginEnable", constant = "0")
    @Mapping(target = "authUserMobile", ignore = true)
    @Mapping(target = "authUserMobileVerified", ignore = true)
    @Mapping(target = "authUserMobileLoginEnable", ignore = true)
    @Mapping(target = "authUserPassword", ignore = true)
    @Mapping(target = "authUserPasswordSalt", ignore = true)
    @Mapping(target = "authUserPasswordExpire", ignore = true)
    @Mapping(target = "authUserPasswordHistory", ignore = true)
    @Mapping(target = "authUserEnable", constant = "1")
    @Mapping(target = "authUserEnableExpire", ignore = true)
    @Mapping(target = "authUserDeleted", constant = "0")
    @Mapping(target = "authUserScope", constant = "openid")
    @Mapping(target = "createdUser", constant = "SYSTEM")
    @Mapping(target = "createdTime", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedUser", ignore = true)
    @Mapping(target = "updatedTime", ignore = true)
    AuthUser toEntity(RegisterRequest request);
}
