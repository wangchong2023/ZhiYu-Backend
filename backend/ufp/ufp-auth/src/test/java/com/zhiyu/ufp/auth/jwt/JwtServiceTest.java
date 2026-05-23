package com.zhiyu.ufp.auth.jwt;

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
                .hasMessageContaining("过期");
    }
}
