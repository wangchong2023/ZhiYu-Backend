package com.zhiyu.ufp.auth.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserStatusTest {

    @Test
    void shouldHaveAllStatuses() {
        AuthUserStatus[] values = AuthUserStatus.values();
        assertThat(values).containsExactly(
                AuthUserStatus.ENABLED,
                AuthUserStatus.DISABLED,
                AuthUserStatus.DELETED
        );
    }

    @Test
    void shouldResolveEnabledByValueOf() {
        assertThat(AuthUserStatus.valueOf("ENABLED")).isEqualTo(AuthUserStatus.ENABLED);
    }

    @Test
    void shouldResolveDisabledByValueOf() {
        assertThat(AuthUserStatus.valueOf("DISABLED")).isEqualTo(AuthUserStatus.DISABLED);
    }

    @Test
    void shouldResolveDeletedByValueOf() {
        assertThat(AuthUserStatus.valueOf("DELETED")).isEqualTo(AuthUserStatus.DELETED);
    }
}
