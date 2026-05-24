package com.zhiyu.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthPropertiesTest {

    @Test
    void shouldHaveDefaultWechatConfig() {
        OAuthProperties props = new OAuthProperties();
        assertThat(props.getWechat()).isNotNull();
        assertThat(props.getWechat().getAppId()).isNull();
        assertThat(props.getWechat().getAppSecret()).isNull();
    }

    @Test
    void shouldHaveDefaultAppleConfig() {
        OAuthProperties props = new OAuthProperties();
        assertThat(props.getApple()).isNotNull();
        assertThat(props.getApple().getClientId()).isNull();
        assertThat(props.getApple().getTeamId()).isNull();
        assertThat(props.getApple().getKeyId()).isNull();
        assertThat(props.getApple().getPrivateKey()).isNull();
    }

    @Test
    void shouldHaveDefaultGoogleConfig() {
        OAuthProperties props = new OAuthProperties();
        assertThat(props.getGoogle()).isNotNull();
        assertThat(props.getGoogle().getClientId()).isNull();
        assertThat(props.getGoogle().getClientSecret()).isNull();
    }

    @Test
    void shouldSetAndGetWechatProperties() {
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.Wechat wechat = new OAuthProperties.Wechat();
        wechat.setAppId("wx-app-id");
        wechat.setAppSecret("wx-app-secret");
        wechat.setRedirectUri("https://example.com/callback");
        props.setWechat(wechat);

        assertThat(props.getWechat().getAppId()).isEqualTo("wx-app-id");
        assertThat(props.getWechat().getAppSecret()).isEqualTo("wx-app-secret");
        assertThat(props.getWechat().getRedirectUri()).isEqualTo("https://example.com/callback");
    }

    @Test
    void shouldSetAndGetAppleProperties() {
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.Apple apple = new OAuthProperties.Apple();
        apple.setClientId("com.example.app");
        apple.setTeamId("TEAM123");
        apple.setKeyId("KEY456");
        apple.setPrivateKey("-----BEGIN PRIVATE KEY-----\n...");
        props.setApple(apple);

        assertThat(props.getApple().getClientId()).isEqualTo("com.example.app");
        assertThat(props.getApple().getTeamId()).isEqualTo("TEAM123");
        assertThat(props.getApple().getKeyId()).isEqualTo("KEY456");
        assertThat(props.getApple().getPrivateKey()).isEqualTo("-----BEGIN PRIVATE KEY-----\n...");
    }

    @Test
    void shouldSetAndGetGoogleProperties() {
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.Google google = new OAuthProperties.Google();
        google.setClientId("google-client-id");
        google.setClientSecret("google-client-secret");
        props.setGoogle(google);

        assertThat(props.getGoogle().getClientId()).isEqualTo("google-client-id");
        assertThat(props.getGoogle().getClientSecret()).isEqualTo("google-client-secret");
    }

    @Test
    void shouldHaveIndependentNestedObjects() {
        OAuthProperties props = new OAuthProperties();
        props.getWechat().setAppId("wx-id");
        props.getApple().setClientId("apple-id");
        props.getGoogle().setClientId("google-id");

        assertThat(props.getWechat().getAppId()).isEqualTo("wx-id");
        assertThat(props.getApple().getClientId()).isEqualTo("apple-id");
        assertThat(props.getGoogle().getClientId()).isEqualTo("google-id");
    }
}
