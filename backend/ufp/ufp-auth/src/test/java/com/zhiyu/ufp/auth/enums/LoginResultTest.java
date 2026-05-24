package com.zhiyu.ufp.auth.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginResultTest {

    @Test
    void shouldHaveAllResults() {
        LoginResult[] values = LoginResult.values();
        assertThat(values).containsExactly(
                LoginResult.SUCCESS,
                LoginResult.FAILED,
                LoginResult.LOCKED,
                LoginResult.DISABLED
        );
    }

    @Test
    void shouldResolveSuccessByValueOf() {
        assertThat(LoginResult.valueOf("SUCCESS")).isEqualTo(LoginResult.SUCCESS);
    }

    @Test
    void shouldResolveFailedByValueOf() {
        assertThat(LoginResult.valueOf("FAILED")).isEqualTo(LoginResult.FAILED);
    }

    @Test
    void shouldResolveLockedByValueOf() {
        assertThat(LoginResult.valueOf("LOCKED")).isEqualTo(LoginResult.LOCKED);
    }

    @Test
    void shouldResolveDisabledByValueOf() {
        assertThat(LoginResult.valueOf("DISABLED")).isEqualTo(LoginResult.DISABLED);
    }
}
