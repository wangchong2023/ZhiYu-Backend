package com.zhiyu.server;

import com.zhiyu.auth.dto.*;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zhiyu_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.flyway.enabled", () -> "true");
        r.add("zhiyu.auth.jwt.key-dir", () -> "deploy/envs/kubeadm");
    }

    @Test
    @Order(1)
    void shouldFailRegisterWithoutCaptcha() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("test");
        req.setPassword("Abcd1234");
        req.setEmail("test@test.com");
        req.setCaptchaToken("bad");
        req.setCaptchaCode("bad");

        HttpEntity<RegisterRequest> entity = new HttpEntity<>(req);
        var resp = rest.exchange(url("/api/v1/auth/register"), HttpMethod.POST, entity,
                new ParameterizedTypeReference<ApiResponse<RegisterResponse>>() {});

        assertThat(resp.getBody().getCode()).isNotEqualTo(0);
    }

    @Test
    @Order(2)
    void shouldFailLoginWithBadCredentials() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent");
        req.setPassword("BadPass1");

        HttpEntity<LoginRequest> entity = new HttpEntity<>(req);
        var resp = rest.exchange(url("/api/v1/auth/login"), HttpMethod.POST, entity,
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {});

        assertThat(resp.getBody().getCode()).isEqualTo(40105);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
