package com.zhiyu.auth.oauth;

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
                .hasMessageContaining("Apple ID Token 不能为空");
    }

    @Test
    void shouldThrowWhenIdTokenIsBlank() {
        OAuthRequest request = new OAuthRequest("code", "state", "   ");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 不能为空");
    }

    @Test
    void shouldThrowWhenIdTokenIsEmpty() {
        OAuthRequest request = new OAuthRequest("code", "state", "");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 不能为空");
    }

    @Test
    void shouldThrowWhenIdTokenHasInvalidFormat() {
        // A JWT has 3 parts separated by dots
        OAuthRequest request = new OAuthRequest("code", "state", "not-a-jwt");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 格式无效");
    }

    @Test
    void shouldThrowWhenIdTokenHasOnlyOnePart() {
        OAuthRequest request = new OAuthRequest("code", "state", "header");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 格式无效");
    }

    @Test
    void shouldThrowOnInvalidBase64UrlHeader() {
        // header.pay.sign but header is not valid base64url
        OAuthRequest request = new OAuthRequest("code", "state",
                "!!!invalid!!!.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature");

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 验证失败");
    }

    @Test
    void shouldThrowOnRestTemplateFailure() {
        // Create a valid-looking JWT with proper base64url header containing a "kid"
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        String payloadB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"sub\":\"12345\"}".getBytes());
        // Signature doesn't matter since restTemplate call will fail
        String idToken = headerB64 + ".fake-payload.fake-sig";

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 验证失败");
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
                .hasMessageContaining("Apple 公钥获取失败");
    }

    @Test
    void shouldThrowWhenAppleKeysResponseIsMalformed() {
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"some-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        String idToken = headerB64 + ".eyJzdWIiOiIxMjM0NSJ9.signature";

        // Return invalid JSON
        when(restTemplate.getForObject(eq("https://appleid.apple.com/auth/keys"),
                eq(String.class))).thenReturn("not-json");

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 验证失败");
    }

    @Test
    void shouldDeduplicateBizExceptionDirectly() {
        // Tests that BizException thrown during processing is propagated as-is
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-kid\"}";
        String headerB64 = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes());
        String idToken = headerB64 + ".eyJzdWIiOiIxMjM0NSJ9.signature";

        // Throw a BizException from the restTemplate mock
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new BizException(40112, "Code invalid"));

        OAuthRequest request = new OAuthRequest("code", "state", idToken);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldPassCodeAndStateThrough() {
        // Tests that code and state don't affect authorization validation
        OAuthRequest request = new OAuthRequest("code-value", "state-value", null);

        assertThatThrownBy(() -> provider.authorize(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Apple ID Token 不能为空");
    }

    @Test
    void shouldVerifyValidAppleIdToken() throws Exception {
        // Generate RSA key pair
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair keyPair = keyGen.generateKeyPair();
        java.security.interfaces.RSAPublicKey rsaPub = (java.security.interfaces.RSAPublicKey) keyPair.getPublic();

        // Create JWK response with the public key's n and e
        // Strip leading zero byte from modulus if present (BigInteger encoding adds it)
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

        // Use jjwt to build a proper JWT
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
