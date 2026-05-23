package com.zhiyu.admin.converter;

import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AdminConverter {
    AdminConverter INSTANCE = Mappers.getMapper(AdminConverter.class);

    @Mapping(target = "userId", source = "authUserId")
    @Mapping(target = "username", source = "authUserUsername")
    @Mapping(target = "email", source = "authUserMail")
    @Mapping(target = "mobile", source = "authUserMobile")
    @Mapping(target = "createdAt", source = "createdTime")
    @Mapping(target = "status",
            expression = "java(toStatus(entity.getAuthUserEnable(),"
                    + " entity.getAuthUserDeleted()))")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "lastLoginIp", ignore = true)
    AdminUserDto toDto(AuthUser entity);

    @Mapping(target = "id", source = "authUserLogId")
    @Mapping(target = "username", source = "authUserLogUserDisplay")
    @Mapping(target = "action", source = "authUserLogAction")
    @Mapping(target = "result", source = "authUserLogResult")
    @Mapping(target = "ip", source = "authUserLogIp")
    @Mapping(target = "device", source = "authUserLogDevice")
    @Mapping(target = "location", source = "authUserLogLocation")
    @Mapping(target = "time", source = "createdTime")
    LoginLogDto toLogDto(AuthUserLog entity);

    default String toStatus(final Integer enable, final Integer deleted) {
        if (deleted != null && deleted == 1) {
            return "已注销";
        }
        if (enable == null || enable != 1) {
            return "已禁用";
        }
        return "正常";
    }
}
