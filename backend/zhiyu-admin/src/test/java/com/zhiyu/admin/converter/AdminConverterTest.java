package com.zhiyu.admin.converter;

import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConverterTest {

    // ── toDto ──────────────────────────────────────────────────

    @Test
    void shouldReturnNullWhenAuthUserIsNull() {
        assertThat(AdminConverter.INSTANCE.toDto(null)).isNull();
    }

    @Test
    void shouldMapNormalStatus() {
        AuthUser entity = AuthUser.builder()
                .authUserId(1L).authUserUsername("test")
                .authUserMail("test@example.com").authUserMobile("13800138000")
                .authUserEnable(1).authUserDeleted(0)
                .createdTime(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();

        AdminUserDto dto = AdminConverter.INSTANCE.toDto(entity);

        assertThat(dto.getUserId()).isEqualTo(1L);
        assertThat(dto.getUsername()).isEqualTo("test");
        assertThat(dto.getEmail()).isEqualTo("test@example.com");
        assertThat(dto.getMobile()).isEqualTo("13800138000");
        assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 10, 0));
        assertThat(dto.getStatus()).isEqualTo("正常");
    }

    // ── toLogDto ───────────────────────────────────────────────

    @Test
    void shouldReturnNullWhenAuthUserLogIsNull() {
        assertThat(AdminConverter.INSTANCE.toLogDto(null)).isNull();
    }

    @Test
    void shouldMapLogDto() {
        AuthUserLog entity = new AuthUserLog();
        entity.setAuthUserLogId(100L);
        entity.setAuthUserLogUserDisplay("logger");
        entity.setAuthUserLogAction("LOGIN");
        entity.setAuthUserLogType("PASSWORD");
        entity.setAuthUserLogResult("SUCCESS");
        entity.setAuthUserLogIp("10.0.0.1");
        entity.setAuthUserLogDevice("Chrome");
        entity.setAuthUserLogLocation("Beijing");
        entity.setCreatedTime(LocalDateTime.of(2026, 5, 23, 10, 0));

        LoginLogDto dto = AdminConverter.INSTANCE.toLogDto(entity);

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getUsername()).isEqualTo("logger");
        assertThat(dto.getAction()).isEqualTo("LOGIN");
        assertThat(dto.getType()).isEqualTo("PASSWORD");
        assertThat(dto.getResult()).isEqualTo("SUCCESS");
        assertThat(dto.getIp()).isEqualTo("10.0.0.1");
        assertThat(dto.getDevice()).isEqualTo("Chrome");
        assertThat(dto.getLocation()).isEqualTo("Beijing");
        assertThat(dto.getTime()).isEqualTo(LocalDateTime.of(2026, 5, 23, 10, 0));
    }

    // ── toStatus ───────────────────────────────────────────────

    @Test
    void shouldMapNormalStatusDirect() {
        assertThat(AdminConverter.INSTANCE.toStatus(1, 0)).isEqualTo("正常");
    }

    @Test
    void shouldMapDisabledStatusByZero() {
        assertThat(AdminConverter.INSTANCE.toStatus(0, 0)).isEqualTo("已禁用");
    }

    @Test
    void shouldMapDeletedStatus() {
        assertThat(AdminConverter.INSTANCE.toStatus(1, 1)).isEqualTo("已注销");
    }

    @Test
    void shouldMapNullEnableAsDisabled() {
        assertThat(AdminConverter.INSTANCE.toStatus(null, 0)).isEqualTo("已禁用");
    }

    @Test
    void shouldMapDeletedOverDisabled() {
        // deleted=1 should take priority over enable=0
        assertThat(AdminConverter.INSTANCE.toStatus(0, 1)).isEqualTo("已注销");
    }

    @Test
    void shouldMapDeletedWhenBothNull() {
        assertThat(AdminConverter.INSTANCE.toStatus(null, 1)).isEqualTo("已注销");
    }

    @Test
    void shouldMapNormalWhenDeletedNull() {
        assertThat(AdminConverter.INSTANCE.toStatus(1, null)).isEqualTo("正常");
    }

    @Test
    void shouldMapDisabledWhenDeletedNull() {
        assertThat(AdminConverter.INSTANCE.toStatus(0, null)).isEqualTo("已禁用");
    }

    @Test
    void shouldMapDisabledWhenEnableNullAndDeletedNull() {
        assertThat(AdminConverter.INSTANCE.toStatus(null, null)).isEqualTo("已禁用");
    }

    @Test
    void shouldConvertFullAuthUser() {
        AuthUser entity = AuthUser.builder()
                .authUserId(42L).authUserUsername("fulluser")
                .authUserMail("full@example.com").authUserMobile("13900139000")
                .authUserEnable(1).authUserDeleted(0)
                .authUserScope("ADMIN")
                .createdTime(LocalDateTime.of(2026, 1, 15, 9, 30))
                .build();

        AdminUserDto dto = AdminConverter.INSTANCE.toDto(entity);

        assertThat(dto.getUserId()).isEqualTo(42L);
        assertThat(dto.getUsername()).isEqualTo("fulluser");
        assertThat(dto.getEmail()).isEqualTo("full@example.com");
        assertThat(dto.getMobile()).isEqualTo("13900139000");
        assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 9, 30));
        assertThat(dto.getStatus()).isEqualTo("正常");
        assertThat(dto.getLastLoginAt()).isNull();
        assertThat(dto.getLastLoginIp()).isNull();
    }

    @Test
    void shouldConvertFullAuthUserLog() {
        AuthUserLog entity = new AuthUserLog();
        entity.setAuthUserLogId(200L);
        entity.setAuthUserLogUserDisplay("fulllogger");
        entity.setAuthUserLogAction("LOGOUT");
        entity.setAuthUserLogType("TOTP");
        entity.setAuthUserLogResult("SUCCESS");
        entity.setAuthUserLogIp("172.16.0.1");
        entity.setAuthUserLogDevice("Firefox");
        entity.setAuthUserLogLocation("Shanghai");
        entity.setCreatedTime(LocalDateTime.of(2026, 5, 22, 18, 45));

        LoginLogDto dto = AdminConverter.INSTANCE.toLogDto(entity);

        assertThat(dto.getId()).isEqualTo(200L);
        assertThat(dto.getUsername()).isEqualTo("fulllogger");
        assertThat(dto.getAction()).isEqualTo("LOGOUT");
        assertThat(dto.getType()).isEqualTo("TOTP");
        assertThat(dto.getResult()).isEqualTo("SUCCESS");
        assertThat(dto.getIp()).isEqualTo("172.16.0.1");
        assertThat(dto.getDevice()).isEqualTo("Firefox");
        assertThat(dto.getLocation()).isEqualTo("Shanghai");
        assertThat(dto.getTime()).isEqualTo(LocalDateTime.of(2026, 5, 22, 18, 45));
    }
}
