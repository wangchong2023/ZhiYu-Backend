package com.zhiyu.ufp.auth.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthGrantTypeTest {

    @Test
    void shouldHaveAllGrantTypes() {
        AuthGrantType[] values = AuthGrantType.values();
        assertThat(values).containsExactly(
                AuthGrantType.PASSWORD,
                AuthGrantType.SMS,
                AuthGrantType.APPLE,
                AuthGrantType.GOOGLE,
                AuthGrantType.WECHAT,
                AuthGrantType.WEB_AUTHN
        );
    }

    @Test
    void shouldResolvePasswordByValueOf() {
        assertThat(AuthGrantType.valueOf("PASSWORD")).isEqualTo(AuthGrantType.PASSWORD);
    }

    @Test
    void shouldResolveSmsByValueOf() {
        assertThat(AuthGrantType.valueOf("SMS")).isEqualTo(AuthGrantType.SMS);
    }

    @Test
    void shouldResolveAppleByValueOf() {
        assertThat(AuthGrantType.valueOf("APPLE")).isEqualTo(AuthGrantType.APPLE);
    }

    @Test
    void shouldResolveGoogleByValueOf() {
        assertThat(AuthGrantType.valueOf("GOOGLE")).isEqualTo(AuthGrantType.GOOGLE);
    }

    @Test
    void shouldResolveWechatByValueOf() {
        assertThat(AuthGrantType.valueOf("WECHAT")).isEqualTo(AuthGrantType.WECHAT);
    }

    @Test
    void shouldResolveWebAuthnByValueOf() {
        assertThat(AuthGrantType.valueOf("WEB_AUTHN")).isEqualTo(AuthGrantType.WEB_AUTHN);
    }

    @Test
    void shouldHaveValidOrdinals() {
        assertThat(AuthGrantType.PASSWORD.ordinal()).isEqualTo(0);
        assertThat(AuthGrantType.SMS.ordinal()).isEqualTo(1);
        assertThat(AuthGrantType.WEB_AUTHN.ordinal()).isEqualTo(5);
    }
}
