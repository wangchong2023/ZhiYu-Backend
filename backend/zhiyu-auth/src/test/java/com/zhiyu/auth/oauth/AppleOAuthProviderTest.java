package com.zhiyu.auth.oauth;

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
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleOAuthProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OAuthProperties properties;
    private AppleOAuthProvider provider;

    @BeforeEach
    void setUp() {
        OAuthProperties.Apple apple = new OAuthProperties.Apple("com.example.app", "TEAM123", "KEY456", null);
        properties = new OAuthProperties(null, apple, null);

        provider = new AppleOAuthProvider(properties, restTemplate, objectMapper);
    }

    @Test
    void shouldReturnProviderName() {
        assertThat(provider.getProviderName()).isEqualTo("APPLE");
    }

    @Test
    void shouldThrowWhenIdTokenIsNull() {
        OAuthRequest request = new OAuthRequest("code", "state", null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowWhenIdTokenIsBlank() {
        OAuthRequest request = new OAuthRequest("code", "state", "   ");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowWhenIdTokenIsEmpty() {
        OAuthRequest request = new OAuthRequest("code", "state", "");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowWhenIdTokenHasInvalidFormat() {
        // A JWT has 3 parts separated by dots
        OAuthRequest request = new OAuthRequest("code", "state", "not-a-jwt");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowWhenIdTokenHasOnlyOnePart() {
        OAuthRequest request = new OAuthRequest("code", "state", "header");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowOnInvalidBase64UrlHeader() {
        // header.pay.sign but header is not valid base64url
        OAuthRequest request = new OAuthRequest("code", "state",
                "!!!invalid!!!.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowOnRestTemplateFailure() {
        // 创建含正确 base64url 头部（包含 "kid"）的有效 JWT
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        // 签名无关紧要，restTemplate 调用会失败
        String idToken = headerB64 + ".fake-payload.fake-sig";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldThrowWhenAppleKeyFetchReturnsNoMatchingKid() {
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"no-match-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        String idToken = headerB64 + ".eyJzdWIiOiIxMjM0NSJ9.signature";

        String keysResp = "{\"keys\":["
                + "{\"kid\":\"other-kid\",\"n\":\"abc\",\"e\":\"AQAB\"}"
                + "]}";
        when(restTemplate.getForObject(eq("https://appleid.apple.com/auth/keys"),
                eq(String.class))).thenReturn(keysResp);

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_THIRD_PARTY_ERROR.getCode());
    }

    @Test
    void shouldThrowWhenAppleKeysResponseIsMalformed() {
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"some-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        String idToken = headerB64 + ".eyJzdWIiOiIxMjM0NSJ9.signature";

        // 返回非法 JSON
        when(restTemplate.getForObject(eq("https://appleid.apple.com/auth/keys"),
                eq(String.class))).thenReturn("not-json");

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldDeduplicateBizExceptionDirectly() {
        // 测试处理过程中抛出的 BizException 会原样传播
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        String idToken = headerB64 + ".eyJzdWIiOiIxMjM0NSJ9.signature";

        // 从 restTemplate Mock 中抛出 BizException
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new BizException(40112, "Code invalid"));

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldPassCodeAndStateThrough() {
        // 测试 code 和 state 不影响授权校验
        OAuthRequest request = new OAuthRequest("code-value", "state-value", null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.OAUTH_CODE_INVALID.getCode());
    }

    @Test
    void shouldVerifyValidAppleIdToken() throws Exception {
        // 生成 RSA 密鑰对
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair keyPair = keyGen.generateKeyPair();
        java.security.interfaces.RSAPublicKey rsaPub = (java.security.interfaces.RSAPublicKey) keyPair.getPublic();

        // 使用公鑰的 n 和 e 创建 JWK 响应
        // 如大整数编码添加了首位置需将其去掉
        byte[] modulusBytes = rsaPub.getModulus().toByteArray();
        if (modulusBytes[0] == 0) {
            modulusBytes = java.util.Arrays.copyOfRange(modulusBytes, 1, modulusBytes.length);
        }
        String nB64 = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modulusBytes);
        String eB64 = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getPublicExponent().toByteArray());
        String keyId = "apple-key-1";

        String jwksJson = "{\"keys\":[{"
                + "\"kid\":\"" + keyId + "\","
                + "\"kty\":\"RSA\","
                + "\"alg\":\"RS256\","
                + "\"use\":\"sig\","
                + "\"n\":\"" + nB64 + "\","
                + "\"e\":\"" + eB64 + "\""
                + "}]}";

        // 使用 jjwt 构建标准 JWT
        java.util.Date now = new java.util.Date();
        String idToken = io.jsonwebtoken.Jwts.builder()
                .header().keyId(keyId).and()
                .issuer("https://appleid.apple.com")
                .audience().add("com.example.app").and()
                .subject("000123.appleid")
                .claim("email", "test@apple.com")
                .claim("email_verified", true)
                .issuedAt(now)
                .expiration(new java.util.Date(now.getTime() + 3600_000))
                .signWith(keyPair.getPrivate())
                .compact();

        when(restTemplate.getForObject(eq("https://appleid.apple.com/auth/keys"),
                eq(String.class))).thenReturn(jwksJson);

        OAuthRequest request = new OAuthRequest(null, null, idToken);
        OAuthUserInfo result = provider.authorize(request);

        assertThat(result.openid()).isEqualTo("000123.appleid");
        assertThat(result.email()).isEqualTo("test@apple.com");
        assertThat(result.emailVerified()).isTrue();
    }
}
