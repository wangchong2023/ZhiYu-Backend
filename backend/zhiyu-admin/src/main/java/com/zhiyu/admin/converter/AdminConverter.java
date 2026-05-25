package com.zhiyu.admin.converter;

import com.zhiyu.admin.dto.AccessLogDto;
import com.zhiyu.admin.dto.AdminOperationDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.AppLogDto;
import com.zhiyu.admin.dto.IdentityChangeDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.admin.entity.AppLog;
import com.zhiyu.ufp.auth.entity.AuthOperationLog;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.admin.dto.WebAuthnCredentialDto;
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
    @Mapping(target = "type", source = "authUserLogType")
    @Mapping(target = "result", source = "authUserLogResult")
    @Mapping(target = "ip", source = "authUserLogIp")
    @Mapping(target = "device", source = "authUserLogDevice")
    @Mapping(target = "location", source = "authUserLogLocation")
    @Mapping(target = "time", source = "createdTime")
    LoginLogDto toLogDto(AuthUserLog entity);

    default String toStatus(final Integer enable, final Integer deleted) {
        if (deleted != null && deleted == 1) {
            return "DELETED";
        }
        if (enable == null || enable != 1) {
            return "DISABLED";
        }
        return "ACTIVE";
    }

    // ── App Log ──────────────────────────────────────────

    @Mapping(target = "time", source = "createdAt")
    AppLogDto toAppLogDto(AppLog entity);

    // ── Access Log (from AuthOperationLog) ──────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "time", source = "logTime")
    @Mapping(target = "ip", source = "remoteIp")
    @Mapping(target = "method", source = "method")
    @Mapping(target = "path", source = "uri")
    @Mapping(target = "statusCode", expression = "java(parseIntSafe(entity.getStatus()))")
    @Mapping(target = "responseTimeMs", source = "duration")
    @Mapping(target = "userAgent", ignore = true)
    AccessLogDto toAccessLogDto(AuthOperationLog entity);

    default Integer parseIntSafe(final String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Admin Operation (from AuthOperationLog) ──────────

    @Mapping(target = "id", source = "logId")
    @Mapping(target = "username", source = "userName")
    @Mapping(target = "action", source = "operationType")
    @Mapping(target = "target", source = "operationDesc")
    @Mapping(target = "targetId", ignore = true)
    @Mapping(target = "ip", source = "remoteIp")
    @Mapping(target = "time", source = "logTime")
    AdminOperationDto toAdminOperationDto(AuthOperationLog entity);

    // ── Identity Change ──────────────────────────────────

    @Mapping(target = "id", source = "authUserIdentityId")
    @Mapping(target = "userId", source = "authUserId")
    @Mapping(target = "action", ignore = true)
    @Mapping(target = "identityType", source = "provider")
    @Mapping(target = "sourceIp", ignore = true)
    @Mapping(target = "createdAt", source = "createdTime")
    IdentityChangeDto toIdentityChangeDto(AuthUserIdentity entity);

    // ── WebAuthn Credential ──────────────────────────────

    @Mapping(target = "credentialId", source = "credentialId")
    @Mapping(target = "deviceName", source = "deviceName")
    @Mapping(target = "createdAt", source = "createdTime")
    @Mapping(target = "lastUsedTime", source = "lastUsedTime")
    WebAuthnCredentialDto toWebAuthnCredentialDto(AuthUserWebAuthn entity);
}
