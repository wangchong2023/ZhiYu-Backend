package com.zhiyu.ufp.auth.jwt;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private Path keyDir;

    @BeforeEach
    void setUp() throws Exception {
        keyDir = Files.createTempDirectory("jwt-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        Files.writeString(keyDir.resolve("jwt-private.pem"), privatePem);
        Files.writeString(keyDir.resolve("jwt-public.pem"), publicPem);

        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("1h");
        props.setRefreshTokenTtl("24h");

        jwtService = new JwtService(props, new JwtKeyLoader(props));
    }

    @Test
    void shouldIssueAndVerifyToken() {
        var pair = jwtService.issue(1001L, "zhangsan", "openid");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.expiresIn()).isEqualTo(3600);

        JwtClaims claims = jwtService.verify(pair.accessToken());
        assertThat(claims.sub()).isEqualTo("1001");
        assertThat(claims.username()).isEqualTo("zhangsan");
        assertThat(claims.scope()).isEqualTo("openid");
    }

    @Test
    void shouldExtractUserId() {
        var pair = jwtService.issue(1001L, "zhangsan", "openid");
        assertThat(jwtService.getUserId(pair.accessToken())).isEqualTo(1001L);
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("1s");
        JwtService shortLived = new JwtService(props, new JwtKeyLoader(props));

        var pair = shortLived.issue(1L, "test", "openid");
        Thread.sleep(1500);

        assertThatThrownBy(() -> shortLived.verify(pair.accessToken()))
                .hasMessageContaining("expired");
    }

    // ── issuePendingToken() ───────────────────────────────────────

    @Test
    void shouldIssuePendingToken() {
        String pendingToken = jwtService.issuePendingToken(1001L, "zhangsan");

        assertThat(pendingToken).isNotBlank();

        // Pending token should be verifiable
        JwtClaims claims = jwtService.verify(pendingToken);
        assertThat(claims.sub()).isEqualTo("1001");
        assertThat(claims.username()).isEqualTo("zhangsan");
        assertThat(claims.scope()).isEqualTo("totp_pending");
    }

    @Test
    void shouldExtractUserIdFromPendingToken() {
        String pendingToken = jwtService.issuePendingToken(1001L, "zhangsan");
        Long userId = jwtService.getUserId(pendingToken);

        assertThat(userId).isEqualTo(1001L);
    }

    // ── verify with malformed tokens ──────────────────────────────

    @Test
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> jwtService.verify("not-a-valid-jwt"))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    @Test
    void shouldRejectEmptyToken() {
        assertThatThrownBy(() -> jwtService.verify(""))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    @Test
    void shouldRejectNullToken() {
        assertThatThrownBy(() -> jwtService.verify(null))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    // ── verify with tampered token ────────────────────────────────

    @Test
    void shouldRejectTamperedToken() {
        var pair = jwtService.issue(1001L, "zhangsan", "openid");
        // Append garbage to the token to create an invalid signature
        String tamperedToken = pair.accessToken() + "tampered";

        assertThatThrownBy(() -> jwtService.verify(tamperedToken))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    @Test
    void shouldRejectTokenWithModifiedPayload() {
        var pair = jwtService.issue(1001L, "zhangsan", "openid");
        // Change one character in the middle (payload section)
        String[] parts = pair.accessToken().split("\\.");
        // Corrupt the payload by changing one character
        String corruptedPayload = "A" + parts[1].substring(1);
        String corruptedToken = parts[0] + "." + corruptedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.verify(corruptedToken))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    // ── getUserId with edge cases ─────────────────────────────────

    @Test
    void shouldThrowForGetUserIdWithInvalidToken() {
        assertThatThrownBy(() -> jwtService.getUserId("invalid-token"))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    // ── issue with different TTL units ────────────────────────────

    @Test
    void shouldIssueTokenWithSecondsTtl() throws Exception {
        Path keyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(keyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(keyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("30s");
        JwtService secService = new JwtService(props, new JwtKeyLoader(props));

        var pair = secService.issue(1L, "test", "openid");
        assertThat(pair.expiresIn()).isEqualTo(30L);

        JwtClaims claims = secService.verify(pair.accessToken());
        assertThat(claims.sub()).isEqualTo("1");
    }

    @Test
    void shouldIssueTokenWithMinutesTtl() throws Exception {
        Path keyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(keyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(keyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("5m");
        JwtService minService = new JwtService(props, new JwtKeyLoader(props));

        var pair = minService.issue(1L, "test", "openid");
        assertThat(pair.expiresIn()).isEqualTo(300L);

        JwtClaims claims = minService.verify(pair.accessToken());
        assertThat(claims.sub()).isEqualTo("1");
    }

    @Test
    void shouldIssueTokenWithDaysTtl() throws Exception {
        Path keyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(keyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(keyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("2d");
        JwtService dayService = new JwtService(props, new JwtKeyLoader(props));

        var pair = dayService.issue(1L, "test", "openid");
        assertThat(pair.expiresIn()).isEqualTo(172800L);

        JwtClaims claims = dayService.verify(pair.accessToken());
        assertThat(claims.sub()).isEqualTo("1");
    }

    @Test
    void shouldRejectInvalidTtlUnit() throws Exception {
        Path keyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(keyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(keyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("10x"); // invalid unit

        JwtService service = new JwtService(props, new JwtKeyLoader(props));

        assertThatThrownBy(() -> service.issue(1L, "test", "openid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知 TTL 单位");
    }

    // ── issue with default properties ─────────────────────────────

    @Test
    void shouldWorkWithDefaultProperties() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        JwtService defaultService = new JwtService(props, new JwtKeyLoader(props));

        var pair = defaultService.issue(1L, "test", "openid");
        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
    }

    // ── expiresIn value for minutes TTL ───────────────────────────

    @Test
    void shouldComputeExpiresInForHours() throws Exception {
        Path keyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(keyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(keyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("2h");
        JwtService hourService = new JwtService(props, new JwtKeyLoader(props));

        var pair = hourService.issue(1L, "test", "openid");
        assertThat(pair.expiresIn()).isEqualTo(7200L);
    }
}
