/**
 * 文件名: AdminConverter.java
 * 描述: 管理后台数据转换器，利用 MapStruct 实现实体对象与数据传输对象 (DTO) 的相互映射。
 */
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

/**
 * 接口名: AdminConverter
 * 描述: 提供用户、系统日志、访问日志、WebAuthn 凭证等实体与 DTO 的互转映射接口。
 */
@Mapper
public interface AdminConverter {

    /**
     * 实例常量：AdminConverter 的全局单例
     */
    AdminConverter INSTANCE = Mappers.getMapper(AdminConverter.class);

    /**
     * 描述: 将用户实体 AuthUser 转换为 AdminUserDto 对象，自动处理用户启用状态。
     * @param entity 用户实体
     * @return 用户 DTO 对象
     */
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

    /**
     * 描述: 将用户日志实体 AuthUserLog 转换为 LoginLogDto 登录日志对象。
     * @param entity 用户日志实体
     * @return 登录日志 DTO 对象
     */
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

    /**
     * 描述: 根据是否被禁用和是否被删除得出用户当前状态值。
     * @param enable 是否启用 (1-启用)
     * @param deleted 是否已删除 (1-已删除)
     * @return 状态字符串：ACTIVE, DISABLED 或 DELETED
     */
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

    /**
     * 描述: 将系统日志实体 AppLog 转换为 AppLogDto 传输对象。
     * @param entity 系统日志实体
     * @return 系统日志 DTO 对象
     */
    @Mapping(target = "time", source = "createdAt")
    AppLogDto toAppLogDto(AppLog entity);

    // ── Access Log (from AuthOperationLog) ──────────────

    /**
     * 描述: 将操作日志实体 AuthOperationLog 转换为 AccessLogDto 访问日志传输对象。
     * @param entity 操作日志实体
     * @return 访问日志 DTO 对象
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "time", source = "logTime")
    @Mapping(target = "ip", source = "remoteIp")
    @Mapping(target = "method", source = "method")
    @Mapping(target = "path", source = "uri")
    @Mapping(target = "statusCode", expression = "java(parseIntSafe(entity.getStatus()))")
    @Mapping(target = "responseTimeMs", source = "duration")
    @Mapping(target = "userAgent", ignore = true)
    AccessLogDto toAccessLogDto(AuthOperationLog entity);

    /**
     * 描述: 安全地将字符串类型转换为整型，转换失败时返回 null。
     * @param s 输入的数字字符串
     * @return 转换后的整型值，如果失败返回 null
     */
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

    /**
     * 描述: 将操作日志实体 AuthOperationLog 转换为 AdminOperationDto 管理员操作日志对象。
     * @param entity 操作日志实体
     * @return 管理员操作日志 DTO 对象
     */
    @Mapping(target = "id", source = "logId")
    @Mapping(target = "username", source = "userName")
    @Mapping(target = "action", source = "operationType")
    @Mapping(target = "target", source = "operationDesc")
    @Mapping(target = "targetId", ignore = true)
    @Mapping(target = "ip", source = "remoteIp")
    @Mapping(target = "time", source = "logTime")
    AdminOperationDto toAdminOperationDto(AuthOperationLog entity);

    // ── Identity Change ──────────────────────────────────

    /**
     * 描述: 将认证实体 AuthUserIdentity 转换为 IdentityChangeDto 账号身份变更对象。
     * @param entity 认证实体
     * @return 账号身份变更 DTO 对象
     */
    @Mapping(target = "id", source = "authUserIdentityId")
    @Mapping(target = "userId", source = "authUserId")
    @Mapping(target = "action", ignore = true)
    @Mapping(target = "identityType", source = "provider")
    @Mapping(target = "sourceIp", ignore = true)
    @Mapping(target = "createdAt", source = "createdTime")
    IdentityChangeDto toIdentityChangeDto(AuthUserIdentity entity);

    // ── WebAuthn Credential ──────────────────────────────

    /**
     * 描述: 将 WebAuthn 凭证实体 AuthUserWebAuthn 转换为 WebAuthnCredentialDto 对象。
     * @param entity WebAuthn 凭证实体
     * @return WebAuthn 凭证 DTO 对象
     */
    @Mapping(target = "credentialId", source = "credentialId")
    @Mapping(target = "deviceName", source = "deviceName")
    @Mapping(target = "createdAt", source = "createdTime")
    @Mapping(target = "lastUsedTime", source = "lastUsedTime")
    WebAuthnCredentialDto toWebAuthnCredentialDto(AuthUserWebAuthn entity);
}
