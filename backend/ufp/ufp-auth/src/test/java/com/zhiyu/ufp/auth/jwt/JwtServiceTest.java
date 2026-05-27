package com.zhiyu.ufp.auth.jwt;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        // 此处必须等待 Token 自然过期（TTL=1s），无法用 Mock 替代真实时钟行为
        @SuppressWarnings("java:S2925")
        long sleepMs = 1500;
        Thread.sleep(sleepMs);

        assertThatThrownBy(() -> shortLived.verify(pair.accessToken()))
                .hasMessageContaining("expired");
    }

    // ── issuePendingToken() ───────────────────────────────────────────

    @Test
    void shouldIssuePendingToken() {
        String pendingToken = jwtService.issuePendingToken(1001L, "zhangsan");

        assertThat(pendingToken).isNotBlank();

        // 挂起 Token 应可通过 verify() 正常核验
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

    // ── 畸形 Token 核验测试 ───────────────────────────────────────

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

    // ── 篡改 Token 核验测试 ───────────────────────────────────────

    @Test
    void shouldRejectTamperedToken() {
        var pair = jwtService.issue(1001L, "zhangsan", "openid");
        // 在 Token 末尾追加垃圾字符以制造非法签名
        String tamperedToken = pair.accessToken() + "tampered";

        assertThatThrownBy(() -> jwtService.verify(tamperedToken))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    @Test
    void shouldRejectTokenWithModifiedPayload() {
        var pair = jwtService.issue(1001L, "zhangsan", "openid");
        // 修改 JWT 中间部分（Payload 段）的某个字符
        String[] parts = pair.accessToken().split("\\.");
        // 将 Payload 首字符替换为 'A'，破坏 Base64 编码
        String corruptedPayload = "A" + parts[1].substring(1);
        String corruptedToken = parts[0] + "." + corruptedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.verify(corruptedToken))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    // ── getUserId 边界场景测试 ───────────────────────────────────

    @Test
    void shouldThrowForGetUserIdWithInvalidToken() {
        assertThatThrownBy(() -> jwtService.getUserId("invalid-token"))
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INVALID_TOKEN.getCode());
    }

    // ── 不同 TTL 单位的 issue 测试 ───────────────────────────────

    @Test
    void shouldIssueTokenWithSecondsTtl() throws Exception {
        Path localKeyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(localKeyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(localKeyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(localKeyDir.toString());
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
        Path localKeyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(localKeyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(localKeyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(localKeyDir.toString());
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
        Path localKeyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(localKeyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(localKeyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(localKeyDir.toString());
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
        Path localKeyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(localKeyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(localKeyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(localKeyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("10x"); // 无效的 TTL 单位

        JwtService service = new JwtService(props, new JwtKeyLoader(props));

        assertThatThrownBy(() -> service.issue(1L, "test", "openid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown TTL unit");
    }

    // ── 默认配置场景测试 ─────────────────────────────────────────

    @Test
    void shouldWorkWithDefaultProperties() {
        JwtProperties props = new JwtProperties();
        props.setKeyDir(keyDir.toString());
        JwtService defaultService = new JwtService(props, new JwtKeyLoader(props));

        var pair = defaultService.issue(1L, "test", "openid");
        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
    }

    // ── 分钟 TTL 的 expiresIn 计算验证 ───────────────────────────

    @Test
    void shouldComputeExpiresInForHours() throws Exception {
        Path localKeyDir = Files.createTempDirectory("jwt-ttl-test");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair2 = gen.generateKeyPair();
        Files.writeString(localKeyDir.resolve("jwt-private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        Files.writeString(localKeyDir.resolve("jwt-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair2.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");

        JwtProperties props = new JwtProperties();
        props.setKeyDir(localKeyDir.toString());
        props.setIssuer("test");
        props.setAccessTokenTtl("2h");
        JwtService hourService = new JwtService(props, new JwtKeyLoader(props));

        var pair = hourService.issue(1L, "test", "openid");
        assertThat(pair.expiresIn()).isEqualTo(7200L);
    }
}
