package com.zhiyu.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.config.OAuthProperties;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@code GithubOAuthProvider} 单元测试。
 *
 * @author Antigravity
 */
@ExtendWith(MockitoExtension.class)
class GithubOAuthProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OAuthProperties properties;
    private GithubOAuthProvider provider;

    @BeforeEach
    void setUp() {
        OAuthProperties.Github github = new OAuthProperties.Github("git-id", "git-secret");
        properties = new OAuthProperties(null, null, null, github, null);
        provider = new GithubOAuthProvider(properties, restTemplate, objectMapper);
    }

    @Test
    void shouldReturnProviderName() {
        assertThat(provider.getProviderName()).isEqualTo("GITHUB");
    }

    @Test
    void shouldAuthorizeSuccessfullyWithPublicEmail() throws Exception {
        String tokenJson = "{\"access_token\":\"git-at-token\"}";
        String userJson = "{\"id\":12345,\"login\":\"gituser\",\"name\":\"Git Developer\","
                + "\"avatar_url\":\"https://github.com/avatar.png\",\"email\":\"dev@github.com\"}";

        when(restTemplate.postForObject(eq("https://github.com/login/oauth/access_token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));

        ResponseEntity<JsonNode> userResponse = ResponseEntity.ok(objectMapper.readTree(userJson));
        when(restTemplate.exchange(eq("https://api.github.com/user"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(userResponse);

        OAuthRequest request = new OAuthRequest("git-code", "state", null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("12345");
        assertThat(result.nickname()).isEqualTo("Git Developer");
        assertThat(result.avatarUrl()).isEqualTo("https://github.com/avatar.png");
        assertThat(result.email()).isEqualTo("dev@github.com");
        assertThat(result.emailVerified()).isTrue();
    }

    @Test
    void shouldFetchPrimaryEmailIfPublicEmailIsNull() throws Exception {
        String tokenJson = "{\"access_token\":\"git-at-token-2\"}";
        String userJson = "{\"id\":67890,\"login\":\"gitnoemail\",\"name\":\"No Email Guy\","
                + "\"avatar_url\":\"https://github.com/avatar2.png\",\"email\":null}";
        String emailsJson = "[{\"email\":\"other@github.com\",\"primary\":false,\"verified\":true},"
                + "{\"email\":\"primary-verified@github.com\",\"primary\":true,\"verified\":true}]";

        when(restTemplate.postForObject(eq("https://github.com/login/oauth/access_token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(tokenJson));

        ResponseEntity<JsonNode> userResponse = ResponseEntity.ok(objectMapper.readTree(userJson));
        when(restTemplate.exchange(eq("https://api.github.com/user"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(userResponse);

        ResponseEntity<String> emailsResponse = ResponseEntity.ok(emailsJson);
        when(restTemplate.exchange(eq("https://api.github.com/user/emails"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(emailsResponse);

        OAuthRequest request = new OAuthRequest("git-code-2", "state", null);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("67890");
        assertThat(result.email()).isEqualTo("primary-verified@github.com");
        assertThat(result.emailVerified()).isTrue();
    }

    @Test
    void shouldThrowOnTokenExchangeFailure() {
        when(restTemplate.postForObject(eq("https://github.com/login/oauth/access_token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Connection error"));

        OAuthRequest request = new OAuthRequest("bad-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_THIRD_PARTY_ERROR.getCode());
    }

    @Test
    void shouldThrowOnTokenResponseError() throws Exception {
        String errorJson = "{\"error\":\"bad_verification_code\",\"error_description\":\"The code is invalid\"}";
        when(restTemplate.postForObject(eq("https://github.com/login/oauth/access_token"),
                any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(objectMapper.readTree(errorJson));

        OAuthRequest request = new OAuthRequest("expired-code", null, null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }
}
