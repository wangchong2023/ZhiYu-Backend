package com.zhiyu.server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class ZhiYuApplicationSmokeTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zhiyu_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @BeforeAll
    static void createDatabases() throws Exception {
        var result = mysql.execInContainer(
                "mysql", "-uroot", "-ptest", "-e",
                "CREATE DATABASE IF NOT EXISTS ufp_auth;"
                        + " GRANT ALL PRIVILEGES ON ufp_auth.* TO 'test'@'%';"
                        + " CREATE TABLE IF NOT EXISTS ufp_auth.auth_user ("
                        + " auth_user_id BIGINT NOT NULL AUTO_INCREMENT,"
                        + " auth_user_username VARCHAR(100),"
                        + " auth_user_password VARCHAR(255),"
                        + " auth_user_code CHAR(32),"
                        + " auth_user_scope VARCHAR(32),"
                        + " auth_user_enable INT DEFAULT 1,"
                        + " PRIMARY KEY (auth_user_id)"
                        + " ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                        + " FLUSH PRIVILEGES;");
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Failed to initialize ufp_auth database: "
                    + result.getStderr());
        }
    }

    @Autowired
    ApplicationContext context;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("zhiyu.auth.jwt.key-dir", () -> "deploy/envs/kubeadm");
    }

    @Test
    void shouldStartApplicationContext() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }
}
