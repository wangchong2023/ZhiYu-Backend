package com.zhiyu.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthPropertiesTest {

    @Test
    void shouldHaveDefaultWechatConfig() {
        OAuthProperties props = new OAuthProperties(null, null, null);
        assertThat(props.getWechat()).isNotNull();
        assertThat(props.getWechat().getAppId()).isNull();
        assertThat(props.getWechat().getAppSecret()).isNull();
    }

    @Test
    void shouldHaveDefaultAppleConfig() {
        OAuthProperties props = new OAuthProperties(null, null, null);
        assertThat(props.getApple()).isNotNull();
        assertThat(props.getApple().getClientId()).isNull();
        assertThat(props.getApple().getTeamId()).isNull();
        assertThat(props.getApple().getKeyId()).isNull();
        assertThat(props.getApple().getPrivateKey()).isNull();
    }

    @Test
    void shouldHaveDefaultGoogleConfig() {
        OAuthProperties props = new OAuthProperties(null, null, null);
        assertThat(props.getGoogle()).isNotNull();
        assertThat(props.getGoogle().getClientId()).isNull();
        assertThat(props.getGoogle().getClientSecret()).isNull();
    }

    @Test
    void shouldSetAndGetWechatProperties() {
        OAuthProperties.Wechat wechat = new OAuthProperties.Wechat(
                "wx-app-id", "wx-app-secret", "https://example.com/callback");
        OAuthProperties props = new OAuthProperties(wechat, null, null);

        assertThat(props.getWechat().getAppId()).isEqualTo("wx-app-id");
        assertThat(props.getWechat().getAppSecret()).isEqualTo("wx-app-secret");
        assertThat(props.getWechat().getRedirectUri()).isEqualTo("https://example.com/callback");
    }

    @Test
    void shouldSetAndGetAppleProperties() {
        OAuthProperties.Apple apple = new OAuthProperties.Apple(
                "com.example.app", "TEAM123", "KEY456", "-----BEGIN PRIVATE KEY-----\n...");
        OAuthProperties props = new OAuthProperties(null, apple, null);

        assertThat(props.getApple().getClientId()).isEqualTo("com.example.app");
        assertThat(props.getApple().getTeamId()).isEqualTo("TEAM123");
        assertThat(props.getApple().getKeyId()).isEqualTo("KEY456");
        assertThat(props.getApple().getPrivateKey()).isEqualTo("-----BEGIN PRIVATE KEY-----\n...");
    }

    @Test
    void shouldSetAndGetGoogleProperties() {
        OAuthProperties.Google google = new OAuthProperties.Google(
                "google-client-id", "google-client-secret");
        OAuthProperties props = new OAuthProperties(null, null, google);

        assertThat(props.getGoogle().getClientId()).isEqualTo("google-client-id");
        assertThat(props.getGoogle().getClientSecret()).isEqualTo("google-client-secret");
    }

    @Test
    void shouldHaveIndependentNestedObjects() {
        OAuthProperties.Wechat wechat = new OAuthProperties.Wechat("wx-id", null, null);
        OAuthProperties.Apple apple = new OAuthProperties.Apple("apple-id", null, null, null);
        OAuthProperties.Google google = new OAuthProperties.Google("google-id", null);
        OAuthProperties props = new OAuthProperties(wechat, apple, google);

        assertThat(props.getWechat().getAppId()).isEqualTo("wx-id");
        assertThat(props.getApple().getClientId()).isEqualTo("apple-id");
        assertThat(props.getGoogle().getClientId()).isEqualTo("google-id");
    }
}
