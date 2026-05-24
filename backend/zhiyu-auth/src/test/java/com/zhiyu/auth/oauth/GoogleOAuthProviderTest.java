package com.zhiyu.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.config.OAuthProperties;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OAuthProperties properties;
    private GoogleOAuthProvider provider;

    @BeforeEach
    void setUp() {
        properties = new OAuthProperties();
        OAuthProperties.Google google = new OAuthProperties.Google();
        google.setClientId("google-client-id");
        google.setClientSecret("google-client-secret");
        properties.setGoogle(google);

        provider = new GoogleOAuthProvider(properties, restTemplate, objectMapper);
    }

    @Test
    void shouldReturnProviderName() {
        assertThat(provider.getProviderName()).isEqualTo("GOOGLE");
    }

    @Test
    void shouldAuthorizeSuccessfullyWithAllFields() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.token\"}";
        String userJson = "{\"sub\":\"12345\",\"email\":\"test@gmail.com\","
                + "\"email_verified\":true,\"name\":\"Test User\",\"picture\":\"https://pic.url\"}";

        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(userJson));

        OAuthRequest request = new OAuthRequest("google-auth-code", "state", null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("12345");
        assertThat(result.email()).isEqualTo("test@gmail.com");
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.nickname()).isEqualTo("Test User");
        assertThat(result.avatarUrl()).isEqualTo("https://pic.url");
    }

    @Test
    void shouldAuthorizeSuccessfullyWithMinimalFields() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.minimal\"}";
        String userJson = "{\"sub\":\"minimal-sub\"}";

        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(userJson));

        OAuthRequest request = new OAuthRequest("minimal-code", null, null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("minimal-sub");
        assertThat(result.email()).isNull();
        assertThat(result.emailVerified()).isFalse();
        assertThat(result.nickname()).isNull();
        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    void shouldAuthorizeWithUnverifiedEmail() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.token\"}";
        String userJson = "{\"sub\":\"unverified\",\"email\":\"unverified@gmail.com\","
                + "\"email_verified\":false,\"name\":\"User\"}";

        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(userJson));

        OAuthRequest request = new OAuthRequest("code-unver", null, null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.email()).isEqualTo("unverified@gmail.com");
        assertThat(result.emailVerified()).isFalse();
    }

    @Test
    void shouldThrowOnTokenExchangeHttpError() {
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("HTTP 500"));

        OAuthRequest request = new OAuthRequest("bad-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Google 服务暂不可用");
    }

    @Test
    void shouldThrowOnTokenErrorResponse() throws Exception {
        String errorJson = "{\"error\":\"invalid_grant\",\"error_description\":\"Bad code\"}";
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(errorJson));

        OAuthRequest request = new OAuthRequest("invalid-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Google 授权码无效");
    }

    @Test
    void shouldThrowOnTokenNullResponse() {
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(null);

        OAuthRequest request = new OAuthRequest("null-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Google 授权码无效");
    }

    @Test
    void shouldThrowOnTokenErrorWithoutDescription() throws Exception {
        String errorJson = "{\"error\":\"unauthorized_client\"}";
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(errorJson));

        OAuthRequest request = new OAuthRequest("unauthorized-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Google 授权码无效");
    }

    @Test
    void shouldThrowOnUserInfoFetchFailure() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.ok\"}";
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        OAuthRequest request = new OAuthRequest("ok-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Google 用户信息获取失败");
    }

    @Test
    void shouldThrowOnUserInfoNullResponse() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.null\"}";
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(null);

        OAuthRequest request = new OAuthRequest("null-user-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Google 用户信息获取失败");
    }

    @Test
    void shouldIncludeClientCredentialsInTokenRequest() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.token\"}";
        String userJson = "{\"sub\":\"123\"}";

        ArgumentCaptor<HttpEntity> tokenEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                tokenEntityCaptor.capture(), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(userJson));

        provider.authorize(new OAuthRequest("test-code", null, null));

        // Verify token request was made with the correct URL and body
        verify(restTemplate).postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void shouldUseBearerTokenForUserInfoRequest() throws Exception {
        String tokenJson = "{\"access_token\":\"ya29.bearer-test\"}";
        String userJson = "{\"sub\":\"bearer-sub\"}";

        ArgumentCaptor<HttpEntity> userEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));
        when(restTemplate.postForObject(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                userEntityCaptor.capture(), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(userJson));

        provider.authorize(new OAuthRequest("bearer-code", null, null));

        // Verify the user info request has Authorization header with Bearer token
        HttpEntity captured = userEntityCaptor.getValue();
        assertThat(captured.getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer ya29.bearer-test");
    }
}
