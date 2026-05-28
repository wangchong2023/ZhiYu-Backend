/**
 * 文件名: AuthConverter.java
 * 描述: 认证数据转换器，利用 MapStruct 实现 DTO 与 Entity 之间的互相转换
 */
package com.zhiyu.auth.converter;

import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.ufp.auth.entity.AuthUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 接口名: AuthConverter
 * 描述: 提供注册请求 DTO 到用户实体 AuthUser 的映射功能
 */
@Mapper
public interface AuthConverter {

    AuthConverter INSTANCE = Mappers.getMapper(AuthConverter.class);

    /**
     * 描述: 将注册请求 DTO 映射为 AuthUser 数据库实体
     * @param request 注册请求 DTO
     * @return 转换后的 AuthUser 实体
     */

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
    @Mapping(target = "authUserAvatar", ignore = true)
    @Mapping(target = "authUserPersonalLocale", ignore = true)
    AuthUser toEntity(RegisterRequest request);
}
