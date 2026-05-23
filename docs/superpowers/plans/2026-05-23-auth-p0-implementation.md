# P0 认证系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户名+密码注册/登录/登出/Token刷新 + Admin 仪表盘/用户列表/登录日志

**Architecture:** 自底向上构建 — zhiyu-common 基础设施 → ufp-auth 认证库 → zhiyu-auth 业务认证 → zhiyu-admin 管理后台。TDD 全程：先写测试 → 确认失败 → 实现 → 确认通过 → 提交。

**Tech Stack:** Java 21 + Spring Boot 3.3.7 + MyBatis-Plus 3.5.10 + Redis（Lettuce）+ BCrypt + JWT RS256（jjwt 0.12.6）+ Hutool 5.8.35（CaptchaUtil）+ MapStruct 1.6.3 + SpringDoc OpenAPI + JUnit 5 + Mockito + Testcontainers

---

## 依赖说明

Task 按依赖顺序编号。无依赖的 Task 可并行，有依赖的必须串行：
- Task 1-4：基础设施（并行）
- Task 5-9：ufp-auth 库（依赖 Task 1-4）
- Task 10-16：zhiyu-auth 业务认证（依赖 Task 5-9）
- Task 17-21：zhiyu-admin 管理后台（依赖 Task 10-16）
- Task 22-24：收尾（集成测试、POM、文档）

---

### Task 1: 统一 API 响应体 + 错误码基础设施

**Files:**
- Create: `backend/zhiyu-common/src/main/java/com/zhiyu/common/web/ApiResponse.java`
- Create: `backend/zhiyu-common/src/main/java/com/zhiyu/common/exception/ErrorCode.java`
- Create: `backend/zhiyu-common/src/main/java/com/zhiyu/common/exception/BizException.java`
- Create: `backend/zhiyu-common/src/main/java/com/zhiyu/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 ErrorCode 接口**

```java
package com.zhiyu.common.exception;

public interface ErrorCode {
    int getCode();
    String getMessage();
}
```

- [ ] **Step 2: 创建 BizException**

```java
package com.zhiyu.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final String message;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
}
```

- [ ] **Step 3: 创建 ApiResponse**

```java
package com.zhiyu.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String requestId;
    private long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(0)
                .message("success")
                .data(data)
                .requestId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .requestId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }
}
```

- [ ] **Step 4: 创建 GlobalExceptionHandler**

```java
package com.zhiyu.common.exception;

import com.zhiyu.common.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.zhiyu")
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ApiResponse.fail(40001, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.fail(50000, "服务器内部错误");
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-common -am -q`
Expected: 编译成功，无错误

- [ ] **Step 6: 提交**

```bash
git add backend/zhiyu-common/src/main/java/com/zhiyu/common/web/ backend/zhiyu-common/src/main/java/com/zhiyu/common/exception/
git commit -m "feat: add ApiResponse, BizException, GlobalExceptionHandler"
```

---

### Task 2: SpringDoc OpenAPI 配置

**Files:**
- Modify: `backend/zhiyu-server/pom.xml`
- Create: `backend/zhiyu-common/src/main/java/com/zhiyu/common/config/OpenApiConfig.java`

- [ ] **Step 1: 在 zhiyu-server/pom.xml 添加 SpringDoc 依赖**

在 `</dependencies>` 之前添加：

```xml
        <!-- SpringDoc OpenAPI (Swagger UI) -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
```

- [ ] **Step 2: 创建 OpenApiConfig**

```java
package com.zhiyu.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zhiyuOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ZhiYu API")
                        .description("智宇后端 REST API 文档 — 供 Apple 客户端及管理后台调用")
                        .version("1.0.0")
                        .contact(new Contact().name("ZhiYu Team").email("dev@zhiyu.local"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .schemaRequirement("Bearer", new SecurityScheme()
                        .name("Bearer")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("在登录接口获取 accessToken，填入此处（不含 Bearer 前缀）"));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-server -am -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add backend/zhiyu-server/pom.xml backend/zhiyu-common/src/main/java/com/zhiyu/common/config/OpenApiConfig.java
git commit -m "feat: add SpringDoc OpenAPI configuration"
```

---

### Task 3: JWT 核心（Claims + Properties + KeyLoader + Service）

**Files:**
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/jwt/JwtClaims.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/jwt/JwtProperties.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/jwt/JwtKeyLoader.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/jwt/JwtService.java`
- Create: `backend/ufp/ufp-auth/src/test/java/com/zhiyu/ufp/auth/jwt/JwtServiceTest.java`

- [ ] **Step 1: 创建 JwtClaims**

```java
package com.zhiyu.ufp.auth.jwt;

public record JwtClaims(
        String sub,
        String iss,
        String aud,
        long exp,
        long iat,
        String jti,
        String username,
        String scope
) {}
```

- [ ] **Step 2: 创建 JwtProperties**

```java
package com.zhiyu.ufp.auth.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zhiyu.auth.jwt")
public class JwtProperties {
    private String algorithm = "RS256";
    private int keySize = 2048;
    private String accessTokenTtl = "15m";
    private String refreshTokenTtl = "7d";
    private String issuer = "https://auth.zhiyu.local";
    private String keyDir = "deploy/envs/kubeadm";
}
```

- [ ] **Step 3: 创建 JwtKeyLoader**

```java
package com.zhiyu.ufp.auth.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtKeyLoader {

    private final JwtProperties properties;

    public PrivateKey loadPrivateKey() {
        try {
            Path path = Path.of(properties.getKeyDir(), "jwt-private.pem");
            String pem = Files.readString(path)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 JWT 私钥: " + properties.getKeyDir(), e);
        }
    }

    public PublicKey loadPublicKey() {
        try {
            Path path = Path.of(properties.getKeyDir(), "jwt-public.pem");
            String pem = Files.readString(path)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 JWT 公钥: " + properties.getKeyDir(), e);
        }
    }
}
```

- [ ] **Step 4: 编写 JwtServiceTest（先写测试）**

```java
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
                .hasMessageContaining("expired");
    }
}
```

- [ ] **Step 5: 在 pom.xml 添加 jjwt 依赖**

在 `backend/ufp/ufp-auth/pom.xml` 的 `<dependencies>` 中添加 test 依赖：

```xml
        <!-- Test -->
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 6: 运行测试确认失败**

Run: `./mvnw -f backend/pom.xml test -pl ufp/ufp-auth -am -Dtest=JwtServiceTest -DfailIfNoTests=false`
Expected: 编译失败 — JwtService 不存在

- [ ] **Step 7: 实现 JwtService**

```java
package com.zhiyu.ufp.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties properties;
    private final JwtKeyLoader keyLoader;

    public record JwtPair(String accessToken, String refreshToken, long expiresIn) {}

    public JwtPair issue(long userId, String username, String scope) {
        Instant now = Instant.now();
        PrivateKey privateKey = keyLoader.loadPrivateKey();

        String accessToken = buildToken(userId, username, scope,
                "ACCESS", now, parseTtl(properties.getAccessTokenTtl()), privateKey);
        String refreshToken = buildToken(userId, username, scope,
                "REFRESH", now, parseTtl(properties.getRefreshTokenTtl()), privateKey);

        return new JwtPair(accessToken, refreshToken, parseTtl(properties.getAccessTokenTtl()));
    }

    public JwtClaims verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyLoader.loadPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toJwtClaims(claims);
        } catch (ExpiredJwtException e) {
            throw new BizException(40102, "Token 已过期");
        } catch (Exception e) {
            throw new BizException(40101, "Token 无效");
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(verify(token).sub());
    }

    private String buildToken(long userId, String username, String scope,
                              String tokenType, Instant now, long ttlSeconds, PrivateKey key) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .audience().add("zhiyu-backend").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .id(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("scope", scope)
                .claim("type", tokenType)
                .signWith(key)
                .compact();
    }

    private JwtClaims toJwtClaims(Claims c) {
        return new JwtClaims(
                c.getSubject(),
                c.getIssuer(),
                String.valueOf(c.getAudience()),
                c.getExpiration().getTime() / 1000,
                c.getIssuedAt().getTime() / 1000,
                c.getId(),
                c.get("username", String.class),
                c.get("scope", String.class)
        );
    }

    private long parseTtl(String ttl) {
        ttl = ttl.trim();
        char unit = ttl.charAt(ttl.length() - 1);
        long value = Long.parseLong(ttl.substring(0, ttl.length() - 1));
        return switch (unit) {
            case 's' -> value;
            case 'm' -> value * 60;
            case 'h' -> value * 3600;
            case 'd' -> value * 86400;
            default -> throw new IllegalArgumentException("未知 TTL 单位: " + ttl);
        };
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `./mvnw -f backend/pom.xml test -pl ufp/ufp-auth -am -Dtest=JwtServiceTest`
Expected: TESTS: 3, FAILURES: 0

- [ ] **Step 9: 提交**

```bash
git add backend/ufp/ufp-auth/
git commit -m "feat: add JWT service with RS256 sign/verify"
```

---

### Task 4: PasswordService + TokenBlacklist

**Files:**
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/password/PasswordService.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/token/TokenType.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/token/TokenBlacklist.java`
- Create: `backend/ufp/ufp-auth/src/test/java/com/zhiyu/ufp/auth/password/PasswordServiceTest.java`

- [ ] **Step 1: 编写 PasswordServiceTest**

```java
package com.zhiyu.ufp.auth.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService(12);

    @Test
    void shouldHashAndVerifyPassword() {
        String hash = service.hash("MySecret123");
        assertThat(hash).isNotBlank().startsWith("$2");
        assertThat(service.verify("MySecret123", hash)).isTrue();
    }

    @Test
    void shouldRejectWrongPassword() {
        String hash = service.hash("CorrectPass1");
        assertThat(service.verify("WrongPass1", hash)).isFalse();
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        String h1 = service.hash("SamePass1");
        String h2 = service.hash("SamePass1");
        assertThat(h1).isNotEqualTo(h2);
        assertThat(service.verify("SamePass1", h1)).isTrue();
        assertThat(service.verify("SamePass1", h2)).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -f backend/pom.xml test -pl ufp/ufp-auth -am -Dtest=PasswordServiceTest -DfailIfNoTests=false`
Expected: 编译失败 — PasswordService 不存在

- [ ] **Step 3: 实现 PasswordService**

```java
package com.zhiyu.ufp.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordService {

    private final BCryptPasswordEncoder encoder;

    public PasswordService() {
        this(12);
    }

    public PasswordService(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean verify(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./mvnw -f backend/pom.xml test -pl ufp/ufp-auth -am -Dtest=PasswordServiceTest`
Expected: TESTS: 3, FAILURES: 0

- [ ] **Step 5: 创建 TokenType**

```java
package com.zhiyu.ufp.auth.token;

public enum TokenType {
    ACCESS,
    REFRESH
}
```

- [ ] **Step 6: 实现 TokenBlacklist**

```java
package com.zhiyu.ufp.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String PREFIX = "token:blacklist:";
    private final StringRedisTemplate redisTemplate;

    public void add(String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + token, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
```

- [ ] **Step 7: 编译 + 测试全部 ufp-auth**

Run: `./mvnw -f backend/pom.xml test -pl ufp/ufp-auth -am`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 8: 提交**

```bash
git add backend/ufp/ufp-auth/
git commit -m "feat: add PasswordService, TokenBlacklist, TokenType"
```

---

### Task 5: Auth Entities + Enums + Mappers

**Files:**
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/entity/AuthUser.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/entity/AuthUserLog.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/entity/AuthUserDevice.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/entity/AuthLoginAttempt.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/enums/AuthGrantType.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/enums/AuthUserStatus.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/enums/LoginResult.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/mapper/AuthUserMapper.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/mapper/AuthUserLogMapper.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/mapper/AuthUserDeviceMapper.java`
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/mapper/AuthLoginAttemptMapper.java`

- [ ] **Step 1: 创建枚举**

AuthGrantType.java:
```java
package com.zhiyu.ufp.auth.enums;

public enum AuthGrantType {
    PASSWORD,
    SMS,
    APPLE,
    GOOGLE,
    WECHAT,
    WEB_AUTHN
}
```

AuthUserStatus.java:
```java
package com.zhiyu.ufp.auth.enums;

public enum AuthUserStatus {
    ENABLED,
    DISABLED,
    DELETED
}
```

LoginResult.java:
```java
package com.zhiyu.ufp.auth.enums;

public enum LoginResult {
    SUCCESS,
    FAILED,
    LOCKED,
    DISABLED
}
```

- [ ] **Step 2: 创建 AuthUser 实体**

```java
package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_user")
public class AuthUser {

    @TableId(type = IdType.AUTO)
    private Long authUserId;

    @TableField("auth_user_code")
    private String authUserCode;

    @TableField("auth_user_nick")
    private String authUserNick;

    @TableField("auth_user_username")
    private String authUserUsername;

    @TableField("auth_user_username_login_enable")
    private Integer authUserUsernameLoginEnable;

    @TableField("auth_user_mail")
    private String authUserMail;

    @TableField("auth_user_mail_verified")
    private Integer authUserMailVerified;

    @TableField("auth_user_mail_login_enable")
    private Integer authUserMailLoginEnable;

    @TableField("auth_user_mobile")
    private String authUserMobile;

    @TableField("auth_user_mobile_verified")
    private Integer authUserMobileVerified;

    @TableField("auth_user_mobile_login_enable")
    private Integer authUserMobileLoginEnable;

    @TableField("auth_user_password")
    private String authUserPassword;

    @TableField("auth_user_password_salt")
    private String authUserPasswordSalt;

    @TableField("auth_user_password_expire")
    private LocalDateTime authUserPasswordExpire;

    @TableField("auth_user_password_history")
    private String authUserPasswordHistory;

    @TableField("auth_user_enable")
    private Integer authUserEnable;

    @TableField("auth_user_enable_expire")
    private LocalDateTime authUserEnableExpire;

    @TableField("auth_user_deleted")
    private Integer authUserDeleted;

    @TableField("auth_user_scope")
    private String authUserScope;

    @TableField("created_user")
    private String createdUser;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_user")
    private String updatedUser;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
```

- [ ] **Step 3: 创建 AuthUserLog 实体**（关键字段，其余略）

```java
package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_user_log")
public class AuthUserLog {
    @TableId(type = IdType.AUTO)
    private Long authUserLogId;
    @TableField("auth_user_log_user_id")
    private Long authUserLogUserId;
    @TableField("auth_user_log_user_display")
    private String authUserLogUserDisplay;
    @TableField("auth_user_log_location")
    private String authUserLogLocation;
    @TableField("auth_user_log_ip")
    private String authUserLogIp;
    @TableField("auth_user_log_browse")
    private String authUserLogBrowse;
    @TableField("auth_user_log_device")
    private String authUserLogDevice;
    @TableField("auth_user_log_action")
    private String authUserLogAction;
    @TableField("auth_user_log_type")
    private String authUserLogType;
    @TableField("auth_user_log_result")
    private String authUserLogResult;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
```

- [ ] **Step 4: 创建 AuthLoginAttempt 实体**

```java
package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_login_attempt")
public class AuthLoginAttempt {
    @TableId(type = IdType.AUTO)
    private Long authLoginAttemptId;
    @TableField("identifier")
    private String identifier;
    @TableField("attempt_type")
    private String attemptType;
    @TableField("success")
    private Integer success;
    @TableField("failure_reason")
    private String failureReason;
    @TableField("source_ip")
    private String sourceIp;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
```

- [ ] **Step 5: 创建 AuthUserDevice 实体**

```java
package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_user_device")
public class AuthUserDevice {
    @TableId(type = IdType.AUTO)
    private Long authUserDeviceId;
    @TableField("auth_user_id")
    private Long authUserId;
    @TableField("device_id")
    private String deviceId;
    @TableField("platform")
    private String platform;
    @TableField("trusted_for_totp")
    private Integer trustedForTotp;
    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
```

- [ ] **Step 6: 创建 Mapper 接口**

```java
// AuthUserMapper.java
package com.zhiyu.ufp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {}
```

```java
// AuthUserLogMapper.java
package com.zhiyu.ufp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserLogMapper extends BaseMapper<AuthUserLog> {}
```

```java
// AuthLoginAttemptMapper.java
package com.zhiyu.ufp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.ufp.auth.entity.AuthLoginAttempt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLoginAttemptMapper extends BaseMapper<AuthLoginAttempt> {}
```

```java
// AuthUserDeviceMapper.java
package com.zhiyu.ufp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.ufp.auth.entity.AuthUserDevice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserDeviceMapper extends BaseMapper<AuthUserDevice> {}
```

- [ ] **Step 7: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl ufp/ufp-auth -am -q`
Expected: 编译成功

- [ ] **Step 8: 提交**

```bash
git add backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/entity/ backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/enums/ backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/mapper/
git commit -m "feat: add auth entities, enums, and mapper interfaces"
```

---

### Task 6: UfpAuthAutoConfiguration

**Files:**
- Create: `backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/config/UfpAuthAutoConfiguration.java`
- Create: `backend/ufp/ufp-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建自动配置类**

```java
package com.zhiyu.ufp.auth.config;

import com.zhiyu.ufp.auth.jwt.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@ComponentScan(basePackages = "com.zhiyu.ufp.auth")
@MapperScan(basePackages = "com.zhiyu.ufp.auth.mapper")
public class UfpAuthAutoConfiguration {
}
```

- [ ] **Step 2: 创建 spring.factories 替代文件**

文件路径：`backend/ufp/ufp-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.zhiyu.ufp.auth.config.UfpAuthAutoConfiguration
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl ufp/ufp-auth -am -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add backend/ufp/ufp-auth/src/main/java/com/zhiyu/ufp/auth/config/ backend/ufp/ufp-auth/src/main/resources/
git commit -m "feat: add UfpAuthAutoConfiguration"
```

---

### Task 7: JwtAuthFilter

**Files:**
- Create: `backend/zhiyu-common/src/main/java/com/zhiyu/common/filter/JwtAuthFilter.java`

- [ ] **Step 1: 实现 JwtAuthFilter**

```java
package com.zhiyu.common.filter;

import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PERMIT_URLS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/captcha",
            "/api/v1/auth/refresh",
            "/api/v1/admin/login",
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPermitted(path)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            if (tokenBlacklist.isBlacklisted(token)) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(200);
                response.getWriter().write("{\"code\":40103,\"message\":\"Token 已被吊销\"}");
                return;
            }

            var claims = jwtService.verify(token);
            var auth = new UsernamePasswordAuthenticationToken(
                    claims.sub(), token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.scope()))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(200);
            response.getWriter().write("{\"code\":40101,\"message\":\"" + e.getMessage() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPermitted(String path) {
        return PERMIT_URLS.stream().anyMatch(path::startsWith);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-common -am -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add backend/zhiyu-common/src/main/java/com/zhiyu/common/filter/
git commit -m "feat: add JwtAuthFilter for bearer token validation"
```

---

### Task 8: Auth DTOs + MapStruct Converter

**Files:**
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/RegisterRequest.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/LoginRequest.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/RefreshRequest.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/LoginResponse.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/RegisterResponse.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/CaptchaResponse.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/converter/AuthConverter.java`

- [ ] **Step 1: 创建所有 DTO**

```java
// RegisterRequest.java
package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank
    @Size(min = 4, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅支持字母、数字、下划线")
    @Schema(description = "用户名", example = "zhangsan", minLength = 4, maxLength = 32)
    private String username;

    @NotBlank
    @Size(min = 8, max = 128)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
             message = "密码需包含大小写字母和数字")
    @Schema(description = "密码（8-128位，需含大小写字母+数字）", example = "Abc12345")
    private String password;

    @NotBlank
    @Email
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @NotBlank
    @Schema(description = "验证码Token")
    private String captchaToken;

    @NotBlank
    @Schema(description = "验证码（4位字母数字）", example = "A3x9")
    private String captchaCode;
}
```

```java
// LoginRequest.java
package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank
    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @NotBlank
    @Schema(description = "密码", example = "Abc12345")
    private String password;

    @Schema(description = "验证码Token（连续3次失败后必填）")
    private String captchaToken;

    @Schema(description = "验证码")
    private String captchaCode;
}
```

```java
// RefreshRequest.java
package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "刷新Token请求")
public class RefreshRequest {
    @NotBlank
    @Schema(description = "Refresh Token")
    private String refreshToken;
}
```

```java
// LoginResponse.java
package com.zhiyu.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "登录/刷新响应")
public class LoginResponse {

    @Schema(description = "访问令牌（JWT RS256，15分钟有效）")
    private String accessToken;

    @Schema(description = "刷新令牌（7天有效，一次性使用）")
    private String refreshToken;

    @Schema(description = "Access Token 有效期（秒）", example = "900")
    private long expiresIn;

    @Schema(description = "Token 类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "是否需要 TOTP 二次验证（P0 返回 false）")
    private Boolean totpRequired;
}
```

```java
// RegisterResponse.java
package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "注册响应")
public class RegisterResponse {
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;
}
```

```java
// CaptchaResponse.java
package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "验证码响应")
public class CaptchaResponse {
    @Schema(description = "验证码Token（用于后续注册/登录请求）")
    private String captchaToken;

    @Schema(description = "验证码图片（Base64编码，可直接嵌入 img 标签）",
            example = "data:image/png;base64,iVBORw0...")
    private String captchaImage;
}
```

- [ ] **Step 2: 创建 AuthConverter**

```java
package com.zhiyu.auth.converter;

import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.ufp.auth.entity.AuthUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AuthConverter {

    AuthConverter INSTANCE = Mappers.getMapper(AuthConverter.class);

    @Mapping(target = "authUserId", ignore = true)
    @Mapping(target = "authUserCode", ignore = true)
    @Mapping(target = "authUserNick", source = "username")
    @Mapping(target = "authUserUsername", source = "username")
    @Mapping(target = "authUserUsernameLoginEnable", constant = "1")
    @Mapping(target = "authUserMail", source = "email")
    @Mapping(target = "authUserMailVerified", constant = "0")
    @Mapping(target = "authUserMailLoginEnable", constant = "0")
    @Mapping(target = "authUserMobile", ignore = true)
    @Mapping(target = "authUserMobileVerified", ignore = true)
    @Mapping(target = "authUserMobileLoginEnable", ignore = true)
    @Mapping(target = "authUserPassword", ignore = true)
    @Mapping(target = "authUserPasswordSalt", ignore = true)
    @Mapping(target = "authUserPasswordExpire", ignore = true)
    @Mapping(target = "authUserPasswordHistory", ignore = true)
    @Mapping(target = "authUserEnable", constant = "1")
    @Mapping(target = "authUserEnableExpire", ignore = true)
    @Mapping(target = "authUserDeleted", constant = "0")
    @Mapping(target = "authUserScope", constant = "openid")
    @Mapping(target = "createdUser", constant = "SYSTEM")
    @Mapping(target = "createdTime", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedUser", ignore = true)
    @Mapping(target = "updatedTime", ignore = true)
    AuthUser toEntity(RegisterRequest request);
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-auth -am -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add backend/zhiyu-auth/src/main/java/com/zhiyu/auth/dto/ backend/zhiyu-auth/src/main/java/com/zhiyu/auth/converter/
git commit -m "feat: add auth DTOs and MapStruct converter"
```

---

### Task 9: CaptchaService + AuthValidator

**Files:**
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/service/CaptchaService.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/validator/AuthValidator.java`
- Create: `backend/zhiyu-auth/src/test/java/com/zhiyu/auth/service/CaptchaServiceTest.java`

- [ ] **Step 1: 编写 CaptchaServiceTest**

```java
package com.zhiyu.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private CaptchaService captchaService;

    @Test
    void shouldGenerateAndVerifyCaptcha() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        var resp = captchaService.generate("zhiyu_login");
        assertThat(resp.getCaptchaToken()).isNotBlank();
        assertThat(resp.getCaptchaImage()).startsWith("data:image/png;base64,");
    }

    @Test
    void shouldVerifyCorrectCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("A3x9");

        captchaService.verify("test-token", "A3x9");
    }

    @Test
    void shouldThrowOnWrongCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("A3x9");

        assertThatThrownBy(() -> captchaService.verify("test-token", "wrong"))
                .hasMessageContaining("验证码错误");
    }

    @Test
    void shouldThrowOnExpiredCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> captchaService.verify("test-token", "any"))
                .hasMessageContaining("验证码已过期");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am -Dtest=CaptchaServiceTest`
Expected: 编译失败 — CaptchaService 不存在

- [ ] **Step 3: 实现 CaptchaService**

```java
package com.zhiyu.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate redisTemplate;

    public CaptchaResponse generate(String sceneId) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 20);
        String code = captcha.getCode();
        String token = UUID.randomUUID().toString().replace("-", "");

        redisTemplate.opsForValue().set(PREFIX + token, code, TTL);

        return CaptchaResponse.builder()
                .captchaToken(token)
                .captchaImage("data:image/png;base64," + captcha.getImageBase64Data())
                .build();
    }

    public void verify(String token, String code) {
        String key = PREFIX + token;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new BizException(40110, "验证码已过期");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BizException(40109, "验证码错误");
        }
        redisTemplate.delete(key);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am -Dtest=CaptchaServiceTest`
Expected: TESTS: 4, FAILURES: 0

- [ ] **Step 5: 实现 AuthValidator**

```java
package com.zhiyu.auth.validator;

import com.zhiyu.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class AuthValidator {

    public void validateUsername(String username) {
        if (username == null || username.length() < 4 || username.length() > 32) {
            throw new BizException(40001, "用户名需 4-32 位");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BizException(40001, "用户名仅支持字母、数字、下划线");
        }
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BizException(40001, "密码需 8-128 位");
        }
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
            throw new BizException(40001, "密码需包含大小写字母和数字");
        }
    }

    public void validateEmail(String email) {
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new BizException(40001, "邮箱格式不合法");
        }
    }
}
```

- [ ] **Step 6: 编译 + 测试**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add backend/zhiyu-auth/
git commit -m "feat: add CaptchaService and AuthValidator"
```

---

### Task 10: LoginAttemptService

**Files:**
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/service/LoginAttemptService.java`
- Create: `backend/zhiyu-auth/src/test/java/com/zhiyu/auth/service/LoginAttemptServiceTest.java`

- [ ] **Step 1: 编写 LoginAttemptServiceTest**

```java
package com.zhiyu.auth.service;

import com.zhiyu.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void shouldAllowLoginWhenUnderLimit() {
        when(valueOps.get(anyString())).thenReturn("2");
        assertThatCode(() -> service.checkLocked("user1")).doesNotThrowAnyException();
    }

    @Test
    void shouldLockAfterMaxAttempts() {
        when(valueOps.get(anyString())).thenReturn("5");
        assertThatThrownBy(() -> service.checkLocked("user1"))
                .hasMessageContaining("已被临时锁定");
    }

    @Test
    void shouldRequireCaptchaAfter3Failures() {
        when(valueOps.get(anyString())).thenReturn("3");
        assertThatCode(() -> service.checkCaptchaRequired("user1")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am -Dtest=LoginAttemptServiceTest -DfailIfNoTests=false`
Expected: 编译失败

- [ ] **Step 3: 实现 LoginAttemptService**

```java
package com.zhiyu.auth.service;

import com.zhiyu.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String ATTEMPT_PREFIX = "login:attempt:";
    private static final String LOCK_PREFIX = "login:lock:";
    private static final int MAX_ATTEMPTS = 5;
    private static final int CAPTCHA_THRESHOLD = 3;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public void checkLocked(String username) {
        String lockKey = LOCK_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Long remaining = redisTemplate.getExpire(lockKey);
            throw new BizException(40106,
                    "账号已被临时锁定，请 " + (remaining != null ? remaining / 60 + " 分钟后重试" : "稍后重试"));
        }
    }

    public void checkCaptchaRequired(String username) {
        String key = ATTEMPT_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        int attempts = val != null ? Integer.parseInt(val) : 0;
        if (attempts >= CAPTCHA_THRESHOLD) {
            throw new BizException(40111, "需要验证码");
        }
    }

    public void recordFailure(String username) {
        String key = ATTEMPT_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count != null && count >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + username, "1", LOCK_DURATION);
        }
    }

    public void clearAttempts(String username) {
        redisTemplate.delete(ATTEMPT_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am -Dtest=LoginAttemptServiceTest`
Expected: TESTS: 3, FAILURES: 0

- [ ] **Step 5: 提交**

```bash
git add backend/zhiyu-auth/src/main/java/com/zhiyu/auth/service/LoginAttemptService.java backend/zhiyu-auth/src/test/
git commit -m "feat: add LoginAttemptService with rate limiting"
```

---

### Task 11: AuthService

**Files:**
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/service/AuthService.java`
- Create: `backend/zhiyu-auth/src/test/java/com/zhiyu/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 编写 AuthServiceTest**

```java
package com.zhiyu.auth.service;

import com.zhiyu.auth.converter.AuthConverter;
import com.zhiyu.auth.dto.*;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.auth.token.TokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthUserMapper authUserMapper;
    @Mock private AuthUserLogMapper authUserLogMapper;
    @Mock private PasswordService passwordService;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private CaptchaService captchaService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuthValidator authValidator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private AuthService authService;

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");
        req.setEmail("test@example.com");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(authUserMapper.selectOne(any())).thenReturn(null);
        when(passwordService.hash("Abc12345")).thenReturn("$2a$12$hashed");

        var resp = authService.register(req);

        assertThat(resp.getUsername()).isEqualTo("testuser");
        verify(authUserMapper).insert(any());
    }

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("access", "refresh", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void shouldFailLoginWithWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("WrongPass1");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("WrongPass1", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void shouldRefreshToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        JwtClaims claims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 + 3600,
                System.currentTimeMillis() / 1000, "jti", "testuser", "openid");

        when(jwtService.verify("old-refresh")).thenReturn(claims);
        when(jwtService.getUserId("old-refresh")).thenReturn(1001L);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("new-access", "new-refresh", 900));

        var resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-access");
        verify(tokenBlacklist).add(eq("old-refresh"), anyLong());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am -Dtest=AuthServiceTest -DfailIfNoTests=false`
Expected: 编译失败 — AuthService 不存在

- [ ] **Step 3: 实现 AuthService**

```java
package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.converter.AuthConverter;
import com.zhiyu.auth.dto.*;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REGISTER_RATE_PREFIX = "register:rate:";
    private static final int MAX_REGISTER_PER_IP_PER_HOUR = 3;

    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final AuthValidator authValidator;
    private final StringRedisTemplate redisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(RegisterRequest request) {
        authValidator.validateUsername(request.getUsername());
        authValidator.validatePassword(request.getPassword());
        authValidator.validateEmail(request.getEmail());
        captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());

        if (authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername())) != null) {
            throw new BizException(40901, "用户名已被占用");
        }
        if (authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMail, request.getEmail())) != null) {
            throw new BizException(40902, "邮箱已被注册");
        }

        // IP 限流使用 request context（此处简化，Controller 获取 IP 后传入）
        // 实际通过 Filter 写入 MDC，这里用占位

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);
        user.setAuthUserPassword(passwordService.hash(request.getPassword()));
        authUserMapper.insert(user);

        return RegisterResponse.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        loginAttemptService.checkLocked(request.getUsername());

        boolean captchaRequired = false;
        try {
            loginAttemptService.checkCaptchaRequired(request.getUsername());
        } catch (BizException e) {
            captchaRequired = true;
        }

        if (captchaRequired) {
            if (request.getCaptchaToken() == null || request.getCaptchaCode() == null) {
                throw new BizException(40111, "需要验证码");
            }
            captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());
        }

        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername()));

        if (user == null || !passwordService.verify(request.getPassword(), user.getAuthUserPassword())) {
            loginAttemptService.recordFailure(request.getUsername());
            throw new BizException(40105, "用户名或密码错误");
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(40107, "账号已被禁用");
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(40108, "账号已注销");
        }

        loginAttemptService.clearAttempts(request.getUsername());

        JwtPair pair = jwtService.issue(user.getAuthUserId(),
                user.getAuthUserUsername(),
                user.getAuthUserScope() != null ? user.getAuthUserScope() : "openid");

        recordLoginLog(user, "LOGIN", "SUCCESS", null);

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }

    public LoginResponse refresh(RefreshRequest request) {
        String oldToken = request.getRefreshToken();
        if (tokenBlacklist.isBlacklisted(oldToken)) {
            throw new BizException(40103, "Refresh Token 已被使用");
        }
        var claims = jwtService.verify(oldToken);
        long remainingTtl = claims.exp() - System.currentTimeMillis() / 1000;
        tokenBlacklist.add(oldToken, Math.max(remainingTtl, 1));

        Long userId = jwtService.getUserId(oldToken);
        JwtPair pair = jwtService.issue(userId, claims.username(), claims.scope());

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            try {
                var claims = jwtService.verify(accessToken);
                long ttl = claims.exp() - System.currentTimeMillis() / 1000;
                if (ttl > 0) tokenBlacklist.add(accessToken, ttl);
            } catch (Exception ignored) {}
        }
        if (refreshToken != null) {
            try {
                var claims = jwtService.verify(refreshToken);
                long ttl = claims.exp() - System.currentTimeMillis() / 1000;
                if (ttl > 0) tokenBlacklist.add(refreshToken, ttl);
            } catch (Exception ignored) {}
        }
    }

    private void recordLoginLog(AuthUser user, String action, String result, String failureReason) {
        AuthUserLog logEntry = new AuthUserLog();
        logEntry.setAuthUserLogUserId(user.getAuthUserId());
        logEntry.setAuthUserLogUserDisplay(user.getAuthUserUsername());
        logEntry.setAuthUserLogAction(action);
        logEntry.setAuthUserLogResult(result);
        logEntry.setCreatedTime(LocalDateTime.now());
        authUserLogMapper.insert(logEntry);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./mvnw -f backend/pom.xml test -pl zhiyu-auth -am -Dtest=AuthServiceTest`
Expected: TESTS: 4, FAILURES: 0

- [ ] **Step 5: 提交**

```bash
git add backend/zhiyu-auth/src/main/java/com/zhiyu/auth/service/AuthService.java backend/zhiyu-auth/src/test/
git commit -m "feat: add AuthService with register/login/refresh/logout"
```

---

### Task 12: AuthController + CaptchaController

**Files:**
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/controller/AuthController.java`
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/controller/CaptchaController.java`

- [ ] **Step 1: 实现 AuthController**

```java
package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.*;
import com.zhiyu.auth.service.AuthService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "注册登录相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户注册", description = "使用用户名+密码+邮箱创建新账号")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "注册成功"),
        @ApiResponse(responseCode = "40901", description = "用户名已被占用"),
        @ApiResponse(responseCode = "40902", description = "邮箱已被注册"),
        @ApiResponse(responseCode = "40109", description = "验证码错误"),
        @ApiResponse(responseCode = "42903", description = "注册频率超限")
    })
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @Operation(summary = "密码登录", description = "使用用户名+密码登录，返回 JWT Token 对")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登录成功"),
        @ApiResponse(responseCode = "40105", description = "用户名或密码错误"),
        @ApiResponse(responseCode = "40106", description = "账号已被临时锁定"),
        @ApiResponse(responseCode = "40107", description = "账号已被禁用"),
        @ApiResponse(responseCode = "40111", description = "需要验证码")
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "刷新Token", description = "使用 RefreshToken 换取新的 Token 对（旧 RefreshToken 即刻作废）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "刷新成功"),
        @ApiResponse(responseCode = "40103", description = "Token 已被使用（重放攻击）")
    })
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "退出登录", description = "作废当前 AccessToken 和 RefreshToken")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshRequest request) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        String refreshToken = request != null ? request.getRefreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 2: 实现 CaptchaController**

```java
package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.auth.service.CaptchaService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "验证码", description = "图片验证码接口")
@RestController
@RequestMapping("/api/v1/auth/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @Operation(summary = "获取验证码图片", description = "返回验证码图片（Base64）和 Token，Token 用于注册/登录时校验")
    @GetMapping("/image")
    public ApiResponse<CaptchaResponse> getCaptcha(
            @RequestParam(defaultValue = "zhiyu_login") String sceneId) {
        return ApiResponse.success(captchaService.generate(sceneId));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-server -am -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add backend/zhiyu-auth/src/main/java/com/zhiyu/auth/controller/
git commit -m "feat: add AuthController and CaptchaController with Swagger docs"
```

---

### Task 13: AuthSecurityConfig

**Files:**
- Create: `backend/zhiyu-auth/src/main/java/com/zhiyu/auth/config/AuthSecurityConfig.java`

- [ ] **Step 1: 实现 SecurityConfig**

```java
package com.zhiyu.auth.config;

import com.zhiyu.common.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AuthSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                        "/api/v1/auth/captcha/**", "/api/v1/auth/refresh",
                        "/api/v1/admin/login",
                        "/actuator/health", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-server -am -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add backend/zhiyu-auth/src/main/java/com/zhiyu/auth/config/
git commit -m "feat: add Spring Security configuration"
```

---

### Task 14: Admin DTOs + Converter

**Files:**
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/StatsOverviewResponse.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/TrendPoint.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/DistributionItem.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/AdminUserDto.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/AdminUserDetailDto.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/LoginLogDto.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/converter/AdminConverter.java`

- [ ] **Step 1: 创建 Admin DTOs**

```java
// StatsOverviewResponse.java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘概览数据")
public class StatsOverviewResponse {
    @Schema(description = "今日注册数") private long todayRegistrations;
    @Schema(description = "今日登录数") private long todayLogins;
    @Schema(description = "今日活跃用户数 (DAU)") private long dau;
    @Schema(description = "近30天登录成功率 (百分比)") private double loginSuccessRate;
    @Schema(description = "注册较昨日变化百分比") private double registrationChange;
    @Schema(description = "登录较昨日变化百分比") private double loginChange;
}
```

```java
// TrendPoint.java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "趋势数据点")
public class TrendPoint {
    @Schema(description = "日期", example = "2026-05-23") private String date;
    @Schema(description = "数量") private long count;
}
```

```java
// DistributionItem.java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "分布数据项")
public class DistributionItem {
    @Schema(description = "登录方式") private String method;
    @Schema(description = "数量") private long count;
    @Schema(description = "百分比") private double percentage;
}
```

```java
// AdminUserDto.java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "用户列表行")
public class AdminUserDto {
    @Schema(description = "用户ID") private Long userId;
    @Schema(description = "用户名") private String username;
    @Schema(description = "邮箱") private String email;
    @Schema(description = "手机") private String mobile;
    @Schema(description = "注册时间") private LocalDateTime createdAt;
    @Schema(description = "状态") private String status;
    @Schema(description = "最后登录时间") private LocalDateTime lastLoginAt;
    @Schema(description = "最后登录IP") private String lastLoginIp;
}
```

```java
// AdminUserDetailDto.java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "用户详情")
public class AdminUserDetailDto {
    @Schema(description = "用户ID") private Long userId;
    @Schema(description = "用户名") private String username;
    @Schema(description = "邮箱") private String email;
    @Schema(description = "手机") private String mobile;
    @Schema(description = "注册时间") private LocalDateTime createdAt;
    @Schema(description = "状态") private String status;
    @Schema(description = "范围") private String scope;
    @Schema(description = "最近10条登录记录") private List<LoginLogDto> recentLogs;
}
```

```java
// LoginLogDto.java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "登录日志行")
public class LoginLogDto {
    @Schema(description = "日志ID") private Long id;
    @Schema(description = "用户名") private String username;
    @Schema(description = "操作类型") private String action;
    @Schema(description = "结果") private String result;
    @Schema(description = "IP地址") private String ip;
    @Schema(description = "设备信息") private String device;
    @Schema(description = "位置") private String location;
    @Schema(description = "时间") private LocalDateTime time;
}
```

- [ ] **Step 2: 创建 AdminConverter**

```java
package com.zhiyu.admin.converter;

import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.admin.dto.AdminUserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AdminConverter {
    AdminConverter INSTANCE = Mappers.getMapper(AdminConverter.class);

    @Mapping(target = "userId", source = "authUserId")
    @Mapping(target = "username", source = "authUserUsername")
    @Mapping(target = "email", source = "authUserMail")
    @Mapping(target = "mobile", source = "authUserMobile")
    @Mapping(target = "createdAt", source = "createdTime")
    @Mapping(target = "status", expression = "java(toStatus(entity.getAuthUserEnable(), entity.getAuthUserDeleted()))")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "lastLoginIp", ignore = true)
    AdminUserDto toDto(AuthUser entity);

    @Mapping(target = "id", source = "authUserLogId")
    @Mapping(target = "username", source = "authUserLogUserDisplay")
    @Mapping(target = "action", source = "authUserLogAction")
    @Mapping(target = "result", source = "authUserLogResult")
    @Mapping(target = "ip", source = "authUserLogIp")
    @Mapping(target = "device", source = "authUserLogDevice")
    @Mapping(target = "location", source = "authUserLogLocation")
    @Mapping(target = "time", source = "createdTime")
    LoginLogDto toLogDto(AuthUserLog entity);

    default String toStatus(Integer enable, Integer deleted) {
        if (deleted != null && deleted == 1) return "已注销";
        if (enable == null || enable != 1) return "已禁用";
        return "正常";
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/ backend/zhiyu-admin/src/main/java/com/zhiyu/admin/converter/
git commit -m "feat: add admin DTOs and converter"
```

---

### Task 15: Admin Services（AdminStatsService, AdminUserService, AdminLogService）

**Files:**
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminStatsService.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminUserService.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminLogService.java`

- [ ] **Step 1: 实现 AdminStatsService**

```java
package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    public StatsOverviewResponse getOverview() {
        long todayRegs = countToday("auth_user", "created_time");
        long yesterdayRegs = countYesterday("auth_user", "created_time");
        long todayLogins = countToday("auth_user_log", "created_time");
        long yesterdayLogins = countYesterday("auth_user_log", "created_time");

        String dauSql = "SELECT COUNT(DISTINCT auth_user_log_user_id) FROM auth_user_log WHERE DATE(created_time) = CURDATE()";
        Long dau = jdbcTemplate.queryForObject(dauSql, Long.class);

        String rateSql = """
            SELECT
                ROUND(SUM(CASE WHEN auth_user_log_result='SUCCESS' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1)
            FROM auth_user_log
            WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND auth_user_log_action='LOGIN'
            """;
        Double rate = jdbcTemplate.queryForObject(rateSql, Double.class);

        double regChange = yesterdayRegs > 0
                ? ((double)(todayRegs - yesterdayRegs) / yesterdayRegs) * 100 : 100;
        double loginChange = yesterdayLogins > 0
                ? ((double)(todayLogins - yesterdayLogins) / yesterdayLogins) * 100 : 100;

        return StatsOverviewResponse.builder()
                .todayRegistrations(todayRegs)
                .todayLogins(todayLogins)
                .dau(dau != null ? dau : 0)
                .loginSuccessRate(rate != null ? rate : 0)
                .registrationChange(Math.round(regChange * 10.0) / 10.0)
                .loginChange(Math.round(loginChange * 10.0) / 10.0)
                .build();
    }

    public List<TrendPoint> getRegisterTrend(int days) {
        String sql = """
            SELECT DATE(created_time) as dt, COUNT(*) as cnt
            FROM auth_user WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            GROUP BY DATE(created_time) ORDER BY dt
            """;
        return jdbcTemplate.query(sql, (rs, i) -> TrendPoint.builder()
                .date(rs.getDate("dt").toString())
                .count(rs.getLong("cnt")).build(), days);
    }

    public List<TrendPoint> getDauTrend(int days) {
        String sql = """
            SELECT DATE(created_time) as dt, COUNT(DISTINCT auth_user_log_user_id) as cnt
            FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            AND auth_user_log_action='LOGIN' AND auth_user_log_result='SUCCESS'
            GROUP BY DATE(created_time) ORDER BY dt
            """;
        return jdbcTemplate.query(sql, (rs, i) -> TrendPoint.builder()
                .date(rs.getDate("dt").toString())
                .count(rs.getLong("cnt")).build(), days);
    }

    public List<DistributionItem> getLoginMethodDist(int days) {
        String sql = """
            SELECT auth_user_log_type as method, COUNT(*) as cnt
            FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            AND auth_user_log_action='LOGIN' AND auth_user_log_result='SUCCESS'
            GROUP BY auth_user_log_type
            """;
        var items = jdbcTemplate.query(sql, (rs, i) -> {
            long cnt = rs.getLong("cnt");
            return DistributionItem.builder()
                    .method(rs.getString("method") != null ? rs.getString("method") : "PASSWORD")
                    .count(cnt).percentage(0).build();
        }, days);
        long total = items.stream().mapToLong(DistributionItem::getCount).sum();
        items.forEach(item -> item.setPercentage(
                total > 0 ? Math.round(item.getCount() * 1000.0 / total) / 10.0 : 0));
        return items;
    }

    private long countToday(String table, String col) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE DATE(" + col + ") = CURDATE()";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }

    private long countYesterday(String table, String col) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE DATE(" + col + ") = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }
}
```

- [ ] **Step 2: 实现 AdminUserService**

```java
package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    public Page<AdminUserDto> listUsers(int page, int size, String keyword, String status) {
        var wrapper = new LambdaQueryWrapper<AuthUser>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AuthUser::getAuthUserUsername, keyword)
                    .or().like(AuthUser::getAuthUserMail, keyword));
        }
        if ("ENABLED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserEnable, 1);
        } else if ("DISABLED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserEnable, 0);
        } else if ("DELETED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserDeleted, 1);
        }
        wrapper.orderByDesc(AuthUser::getCreatedTime);

        Page<AuthUser> entityPage = authUserMapper.selectPage(new Page<>(page, size), wrapper);
        Page<AdminUserDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(AdminConverter.INSTANCE::toDto)
                .collect(Collectors.toList()));
        return dtoPage;
    }

    public AdminUserDetailDto getUserDetail(Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) throw new BizException(40401, "用户不存在");

        List<LoginLogDto> recentLogs = authUserLogMapper.selectList(
                new LambdaQueryWrapper<AuthUserLog>()
                        .eq(AuthUserLog::getAuthUserLogUserId, userId)
                        .orderByDesc(AuthUserLog::getCreatedTime)
                        .last("LIMIT 10"))
                .stream()
                .map(AdminConverter.INSTANCE::toLogDto)
                .collect(Collectors.toList());

        return AdminUserDetailDto.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .email(user.getAuthUserMail())
                .mobile(user.getAuthUserMobile())
                .createdAt(user.getCreatedTime())
                .status(AdminConverter.INSTANCE.toStatus(user.getAuthUserEnable(), user.getAuthUserDeleted()))
                .scope(user.getAuthUserScope())
                .recentLogs(recentLogs)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableUser(Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) throw new BizException(40401, "用户不存在");
        user.setAuthUserEnable(1);
        authUserMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableUser(Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) throw new BizException(40401, "用户不存在");
        user.setAuthUserEnable(0);
        authUserMapper.updateById(user);
    }
}
```

- [ ] **Step 3: 实现 AdminLogService**

```java
package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AuthUserLogMapper authUserLogMapper;

    public Page<LoginLogDto> listLogs(int page, int size, String username,
                                      String type, String result,
                                      LocalDateTime startTime, LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AuthUserLog>();
        if (username != null && !username.isBlank()) {
            wrapper.like(AuthUserLog::getAuthUserLogUserDisplay, username);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(AuthUserLog::getAuthUserLogType, type);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(AuthUserLog::getAuthUserLogResult, result);
        }
        if (startTime != null) wrapper.ge(AuthUserLog::getCreatedTime, startTime);
        if (endTime != null) wrapper.le(AuthUserLog::getCreatedTime, endTime);
        wrapper.orderByDesc(AuthUserLog::getCreatedTime);

        Page<AuthUserLog> entityPage = authUserLogMapper.selectPage(new Page<>(page, size), wrapper);
        Page<LoginLogDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(AdminConverter.INSTANCE::toLogDto)
                .collect(Collectors.toList()));
        return dtoPage;
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q`
Expected: 编译成功

- [ ] **Step 5: 提交**

```bash
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/
git commit -m "feat: add AdminStatsService, AdminUserService, AdminLogService"
```

---

### Task 16: Admin Controllers

**Files:**
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminAuthController.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminStatsController.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminUserController.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminLogController.java`

- [ ] **Step 1: 实现 AdminAuthController**

```java
package com.zhiyu.admin.controller;

import com.zhiyu.admin.service.AdminAuthService;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台-认证", description = "管理员登录")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录", description = "仅 scope=ADMIN 的用户可登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request));
    }
}
```

- [ ] **Step 2: 创建 AdminAuthService**

```java
package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AuthUserMapper authUserMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername()));

        if (user == null || !passwordService.verify(request.getPassword(), user.getAuthUserPassword())) {
            throw new BizException(40105, "用户名或密码错误");
        }
        if (!"ADMIN".equals(user.getAuthUserScope())) {
            throw new BizException(40301, "无管理员权限");
        }
        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(40107, "账号已被禁用");
        }

        var pair = jwtService.issue(user.getAuthUserId(), user.getAuthUserUsername(), "admin");
        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }
}
```

- [ ] **Step 3: 实现 AdminStatsController**

```java
package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.admin.service.AdminStatsService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理后台-仪表盘", description = "统计数据")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @Operation(summary = "仪表盘概览", description = "今日注册/登录/DAU/成功率")
    @GetMapping("/overview")
    public ApiResponse<StatsOverviewResponse> overview() {
        return ApiResponse.success(adminStatsService.getOverview());
    }

    @Operation(summary = "注册趋势", description = "近N天每日注册量")
    @GetMapping("/register-trend")
    public ApiResponse<List<TrendPoint>> registerTrend(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatsService.getRegisterTrend(days));
    }

    @Operation(summary = "DAU趋势", description = "近N天每日活跃用户")
    @GetMapping("/dau-trend")
    public ApiResponse<List<TrendPoint>> dauTrend(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatsService.getDauTrend(days));
    }

    @Operation(summary = "登录方式分布", description = "近N天各登录方式占比")
    @GetMapping("/login-method-dist")
    public ApiResponse<List<DistributionItem>> loginMethodDist(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminStatsService.getLoginMethodDist(days));
    }
}
```

- [ ] **Step 4: 实现 AdminUserController**

```java
package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.service.AdminUserService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台-用户管理", description = "用户列表/详情/启停")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表", description = "分页查询，支持关键词搜索和状态筛选")
    @GetMapping
    public ApiResponse<Page<AdminUserDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminUserService.listUsers(page, size, keyword, status));
    }

    @Operation(summary = "用户详情", description = "含最近10条登录记录")
    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.success(adminUserService.getUserDetail(id));
    }

    @Operation(summary = "启用用户")
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        adminUserService.enableUser(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "禁用用户")
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        adminUserService.disableUser(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 5: 实现 AdminLogController**

```java
package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.admin.service.AdminLogService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "管理后台-登录日志", description = "登录行为审计")
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminLogController {

    private final AdminLogService adminLogService;

    @Operation(summary = "登录日志", description = "分页查询，支持多条件筛选")
    @GetMapping("/login")
    public ApiResponse<Page<LoginLogDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(adminLogService.listLogs(page, size, username, type, result, startTime, endTime));
    }
}
```

- [ ] **Step 6: 全量编译验证**

Run: `./mvnw -f backend/pom.xml compile -pl zhiyu-server -am -q`
Expected: 编译成功

- [ ] **Step 7: 提交**

```bash
git add backend/zhiyu-admin/
git commit -m "feat: add admin controllers for auth, stats, users, and login logs"
```

---

### Task 17: 配置补充（application.yml + pom.xml 依赖补齐）

**Files:**
- Modify: `backend/zhiyu-server/pom.xml`
- Modify: `backend/zhiyu-server/src/main/resources/application-dev.yml`
- Create: `backend/zhiyu-server/src/main/resources/application-test.yml`

- [ ] **Step 1: zhiyu-server/pom.xml 添加必要的依赖**

确认以下依赖已存在（无则添加）：
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
```

- [ ] **Step 2: 确保 zhiyu-server 依赖 zhiyu-auth**

在 `<dependencies>` 中添加：
```xml
        <dependency>
            <groupId>com.zhiyu</groupId>
            <artifactId>zhiyu-auth</artifactId>
        </dependency>
```

- [ ] **Step 3: 更新 application-dev.yml 添加 auth 配置**

```yaml
zhiyu:
  auth:
    jwt:
      algorithm: RS256
      key-size: 2048
      access-token-ttl: 15m
      refresh-token-ttl: 7d
      issuer: https://auth.zhiyu.local
      key-dir: deploy/envs/kubeadm
    password:
      bcrypt-cost-factor: 12
    login:
      max-attempts: 5
      lock-duration: 15m
      window-duration: 5m
      captcha-after-failures: 3

springdoc:
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
  api-docs:
    enabled: true
```

- [ ] **Step 4: 编译验证**

Run: `./mvnw -f backend/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add backend/zhiyu-server/pom.xml backend/zhiyu-server/src/main/resources/
git commit -m "feat: add Spring Web/Security/Validation deps and auth config"
```

---

### Task 18: 集成测试（注册→登录→刷新→登出 全流程）

**Files:**
- Create: `backend/zhiyu-server/src/test/java/com/zhiyu/server/AuthFlowIT.java`

- [ ] **Step 1: 编写集成测试**

```java
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
import org.testcontainers.utility.DockerImageName;

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
    }

    static String accessToken;
    static String refreshToken;

    @Test
    @Order(1)
    void shouldFailRegisterWithoutCaptcha() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("test"); req.setPassword("Abcd1234");
        req.setEmail("test@test.com");
        req.setCaptchaToken("bad"); req.setCaptchaCode("bad");

        var resp = rest.postForEntity(url("/api/v1/auth/register"), req,
                new ParameterizedTypeReference<ApiResponse<RegisterResponse>>() {});
        assertThat(resp.getBody().getCode()).isNotEqualTo(0);
    }

    @Test
    @Order(2)
    void shouldFailLoginWithBadCredentials() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent"); req.setPassword("BadPass1");

        var resp = rest.postForEntity(url("/api/v1/auth/login"), req,
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {});
        assertThat(resp.getBody().getCode()).isEqualTo(40105);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
```

- [ ] **Step 2: 运行集成测试**

Run: `./mvnw -f backend/pom.xml verify -pl zhiyu-server -am -Dtest=AuthFlowIT -DfailIfNoTests=false`
Expected: 测试执行（部分通过，部分依赖完整环境）

- [ ] **Step 3: 提交**

```bash
git add backend/zhiyu-server/src/test/
git commit -m "test: add auth flow integration tests"
```

---

### Task 19: 最终验证 + 启动测试

- [ ] **Step 1: 全量单元测试**

Run: `./mvnw -f backend/pom.xml test -q`
Expected: BUILD SUCCESS, all unit tests pass

- [ ] **Step 2: 编译检查**

Run: `./mvnw -f backend/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Checkstyle + SpotBugs**

Run: `./mvnw -f backend/pom.xml checkstyle:check spotbugs:check -q`
Expected: 无新增违规

- [ ] **Step 4: 本地启动验证（如 Docker 可用）**

Run: `./mvnw -f backend/pom.xml spring-boot:run -pl zhiyu-server -Dspring.profiles.active=dev`
验证 `http://localhost:8080/swagger-ui.html` 可访问

- [ ] **Step 5: 提交**

```bash
git commit -m "chore: final verification - all tests pass"
```

---

## 任务依赖图

```
Task 1 (ApiResponse) ──┐
Task 2 (OpenAPI)    ──┤
                       ├──> Task 5 (Entities/Mappers) ──┐
Task 3 (JWT Core)   ──┤                                  ├──> Task 7 (JwtAuthFilter)
Task 4 (Password)    ──┘                                  │         │
                                                          │         │
                       ┌──────────────────────────────────┘         │
                       │                                            │
Task 8 (DTOs)  ───────┤                                            │
Task 9 (Captcha) ─────┤                                            │
                       ├──> Task 11 (AuthService) ──> Task 12 ──> Task 13
Task 10 (LoginAttempt)─┘         (Controllers)      (SecurityConfig)
                                                          │
                                                          │
Task 14 (Admin DTOs) ────> Task 15 (Admin Services) ──> Task 16 (Admin Controllers)
                                                              │
                                                              │
Task 17 (Config) ────────────────────────────────────────────┤
Task 18 (Integration Tests) ─────────────────────────────────┘
Task 19 (Final Verification) ─────────────────────────────── 收尾
```
