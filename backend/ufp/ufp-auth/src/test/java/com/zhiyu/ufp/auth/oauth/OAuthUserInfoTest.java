package com.zhiyu.ufp.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthUserInfoTest {

    @Test
    void shouldConstructRecord() {
        OAuthUserInfo info = new OAuthUserInfo(
                "openid-123", "unionid-456", "TestUser",
                "https://avatar.url", "test@example.com", true);

        assertThat(info.openid()).isEqualTo("openid-123");
        assertThat(info.unionid()).isEqualTo("unionid-456");
        assertThat(info.nickname()).isEqualTo("TestUser");
        assertThat(info.avatarUrl()).isEqualTo("https://avatar.url");
        assertThat(info.email()).isEqualTo("test@example.com");
        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    void shouldAllowNullFields() {
        OAuthUserInfo info = new OAuthUserInfo(null, null, null, null, null, false);

        assertThat(info.openid()).isNull();
        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    void shouldHaveCorrectToString() {
        OAuthUserInfo info = new OAuthUserInfo("oid", "uid", "nick", "url", "e@e.com", true);
        String str = info.toString();

        assertThat(str).contains("oid").contains("uid").contains("nick");
    }

    @Test
    void shouldBeEqualWhenFieldsEqual() {
        OAuthUserInfo i1 = new OAuthUserInfo("oid", "uid", "nick", "url", "e@e.com", true);
        OAuthUserInfo i2 = new OAuthUserInfo("oid", "uid", "nick", "url", "e@e.com", true);

        assertThat(i1).isEqualTo(i2);
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenFieldsDiffer() {
        OAuthUserInfo i1 = new OAuthUserInfo("oid1", "uid", "nick", "url", "e@e.com", true);
        OAuthUserInfo i2 = new OAuthUserInfo("oid2", "uid", "nick", "url", "e@e.com", true);

        assertThat(i1).isNotEqualTo(i2);
    }

    @Test
    void shouldHandleEmailVerifiedFlag() {
        OAuthUserInfo verified = new OAuthUserInfo("oid", "uid", "n", "u", "e@e.com", true);
        OAuthUserInfo unverified = new OAuthUserInfo("oid2", "uid", "n", "u", "e2@e.com", false);

        assertThat(verified.emailVerified()).isTrue();
        assertThat(unverified.emailVerified()).isFalse();
    }
}
