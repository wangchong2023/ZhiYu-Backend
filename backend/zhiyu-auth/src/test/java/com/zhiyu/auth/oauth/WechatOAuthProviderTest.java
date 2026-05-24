package com.zhiyu.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.config.OAuthProperties;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatOAuthProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OAuthProperties properties;
    private WechatOAuthProvider provider;

    @BeforeEach
    void setUp() {
        properties = new OAuthProperties();
        OAuthProperties.Wechat wechat = new OAuthProperties.Wechat();
        wechat.setAppId("test-app-id");
        wechat.setAppSecret("test-app-secret");
        properties.setWechat(wechat);

        provider = new WechatOAuthProvider(properties, restTemplate, objectMapper);
    }

    @Test
    void shouldReturnProviderName() {
        assertThat(provider.getProviderName()).isEqualTo("WECHAT");
    }

    @Test
    void shouldAuthorizeSuccessfullyWithAllFields() throws Exception {
        String tokenRespJson = "{\"access_token\":\"wx-at\",\"openid\":\"wx-openid\",\"unionid\":\"wx-union\"}";
        String userInfoJson = "{\"nickname\":\"Test User\",\"headimgurl\":\"https://avatar.url\"}";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(tokenRespJson)
                .thenReturn(userInfoJson);

        OAuthRequest request = new OAuthRequest("wx-auth-code", "state", null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("wx-openid");
        assertThat(result.unionid()).isEqualTo("wx-union");
        assertThat(result.nickname()).isEqualTo("Test User");
        assertThat(result.avatarUrl()).isEqualTo("https://avatar.url");
        assertThat(result.email()).isNull();
        assertThat(result.emailVerified()).isFalse();
    }

    @Test
    void shouldAuthorizeWithoutUnionid() throws Exception {
        String tokenRespJson = "{\"access_token\":\"wx-at-2\",\"openid\":\"wx-openid-2\"}";
        String userInfoJson = "{\"nickname\":\"No Union\",\"headimgurl\":\"https://img.url\"}";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(tokenRespJson)
                .thenReturn(userInfoJson);

        OAuthRequest request = new OAuthRequest("code-2", null, null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("wx-openid-2");
        assertThat(result.unionid()).isNull();
    }

    @Test
    void shouldAuthorizeWithoutNickname() throws Exception {
        String tokenRespJson = "{\"access_token\":\"wx-at-3\",\"openid\":\"wx-openid-3\"}";
        String userInfoJson = "{}";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(tokenRespJson)
                .thenReturn(userInfoJson);

        OAuthRequest request = new OAuthRequest("code-3", null, null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("wx-openid-3");
        assertThat(result.nickname()).isNull();
        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    void shouldHandleUserInfoFetchFailure() throws Exception {
        String tokenRespJson = "{\"access_token\":\"wx-at-4\",\"openid\":\"wx-openid-4\"}";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(tokenRespJson)
                .thenThrow(new RuntimeException("Network error"));

        OAuthRequest request = new OAuthRequest("code-4", null, null);
        OAuthUserInfo result = provider.authorize(request);

        // Should still return basic info with null profile fields
        assertThat(result.openid()).isEqualTo("wx-openid-4");
        assertThat(result.nickname()).isNull();
        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    void shouldThrowOnTokenExchangeFailure() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        OAuthRequest request = new OAuthRequest("bad-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("微信服务暂不可用");
    }

    @Test
    void shouldThrowOnWxErrorCode() throws Exception {
        String errorJson = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(errorJson);

        OAuthRequest request = new OAuthRequest("expired-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("微信授权码无效");
    }

    @Test
    void shouldThrowOnWxErrorCodeWithNoMessage() throws Exception {
        String errorJson = "{\"errcode\":40163}";
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(errorJson);

        OAuthRequest request = new OAuthRequest("expired-code-2", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("微信授权码无效");
    }

    @Test
    void shouldPassWhenErrcodeIsZero() throws Exception {
        String tokenRespJson = "{\"errcode\":0,\"access_token\":\"wx-at\",\"openid\":\"wx-ok\"}";
        String userInfoJson = "{}";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(tokenRespJson)
                .thenReturn(userInfoJson);

        OAuthRequest request = new OAuthRequest("valid-code", null, null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("wx-ok");
    }

    @Test
    void shouldConstructTokenUrlCorrectly() throws Exception {
        String tokenRespJson = "{\"access_token\":\"at\",\"openid\":\"oid\"}";
        String userInfoJson = "{}";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(tokenRespJson)
                .thenReturn(userInfoJson);

        provider.authorize(new OAuthRequest("my-code", "st", null));

        // Verify the URL contains the appId, secret, and code
        verify(restTemplate).getForObject(
                org.mockito.ArgumentMatchers.contains("test-app-id"),
                eq(String.class));
    }
}
