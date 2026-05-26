/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: JwtService.java
 * 创建时间: 2026-05-27
 * 描述: JSON Web Token 核心管理服务，支持 ACCESS、REFRESH、PENDING 类型的 Token 签发、解密与失效校验。
 */
package com.zhiyu.ufp.auth.jwt;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 类名: JwtService
 * 描述: JWT 核心服务组件，基于 RS256 算法对 Token 进行非对称加解密签名，支持生命周期时长动态解析与脱敏验证。
 */
@Component
@RequiredArgsConstructor
public class JwtService {

    // 毫秒与秒的换算基数
    private static final long MS_PER_SECOND = 1000L;
    // 每分钟的秒数
    private static final long SECONDS_PER_MINUTE = 60L;
    // 每小时的秒数
    private static final long SECONDS_PER_HOUR = 3600L;
    // 每天的秒数
    private static final long SECONDS_PER_DAY = 86400L;
    // 待验证 TOTP 的临时授权 Token 生效时长（5分钟）
    private static final long TOTP_PENDING_TTL_SECONDS = 300L;
    
    // 异常消息：不支持的未定义 TTL 周期单位
    private static final String ERR_UNKNOWN_TTL_UNIT = "未知 TTL 单位: ";

    private final JwtProperties properties;
    private final JwtKeyLoader keyLoader;

    /**
     * 描述: 内置记录对象，用于封装签发的 Token 双重令牌对（Access Token 与 Refresh Token）
     */
    public record JwtPair(String accessToken, String refreshToken, long expiresIn) { }

    /**
     * 描述: 为登录成功的用户签发包含 scope 权限范围的双重 JWT 令牌对
     * @param userId 用户 ID
     * @param username 用户账号名
     * @param scope 权限域
     * @return 包含双令牌与有效截止期的 JwtPair 对象
     */
    public JwtPair issue(final long userId, final String username, final String scope) {
        Instant now = Instant.now();
        PrivateKey privateKey = keyLoader.loadPrivateKey();

        // 构造 Access Token 令牌
        String accessToken = buildToken(userId, username, scope,
                "ACCESS", now, parseTtl(properties.getAccessTokenTtl()), privateKey);
        // 构造 Refresh Token 令牌
        String refreshToken = buildToken(userId, username, scope,
                "REFRESH", now, parseTtl(properties.getRefreshTokenTtl()), privateKey);

        return new JwtPair(accessToken, refreshToken, parseTtl(properties.getAccessTokenTtl()));
    }

    /**
     * 描述: 校验 JWT 令牌的合法性与时效，若校验通过则解出完整的 Payload 声明信息
     * @param token 客户端传入的 Bearer Token 字符串
     * @return 解析后的 JwtClaims 数据传输模型
     * @throws BizException 若令牌已过期或签名无效则抛出相应的业务异常
     */
    public JwtClaims verify(final String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyLoader.loadPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toJwtClaims(claims);
        } catch (ExpiredJwtException e) {
            // 抛出 Token 过期业务异常
            throw new BizException(BizErrorCode.TOKEN_EXPIRED, e);
        } catch (Exception e) {
            // 抛出 Token 非法/签名损坏业务异常
            throw new BizException(BizErrorCode.INVALID_TOKEN, e);
        }
    }

    /**
     * 描述: 签发 TOTP 双因子待验证状态下的 PENDING 临时凭证 Token，用于在多因素认证中作为中间过渡凭证
     * @param userId 用户 ID
     * @param username 用户账号名
     * @return 签发出的单次 PENDING Token 字符串
     */
    public String issuePendingToken(final long userId, final String username) {
        Instant now = Instant.now();
        PrivateKey privateKey = keyLoader.loadPrivateKey();
        return buildToken(userId, username, "totp_pending",
                "PENDING", now, TOTP_PENDING_TTL_SECONDS, privateKey);
    }

    /**
     * 描述: 从传入的 Token 中解析出归属的用户唯一标识 ID，常用于鉴权拦截器解析上下文
     * @param token 令牌字符串
     * @return 用户 ID
     */
    public Long getUserId(final String token) {
        return Long.parseLong(verify(token).sub());
    }

    /**
     * 描述: 私有辅助方法，调用 io.jsonwebtoken API 构造标准 JWT 结构并用私钥执行 RS256 签名压缩
     */
    private String buildToken(final long userId, final String username, final String scope,
                              final String tokenType, final Instant now, final long ttlSeconds,
                              final PrivateKey key) {
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

    /**
     * 描述: 私有辅助方法，将底层 Claims 转换为脱敏的数据载体模型
     */
    private JwtClaims toJwtClaims(final Claims c) {
        return new JwtClaims(
                c.getSubject(),
                c.getIssuer(),
                String.valueOf(c.getAudience()),
                c.getExpiration().getTime() / MS_PER_SECOND,
                c.getIssuedAt().getTime() / MS_PER_SECOND,
                c.getId(),
                c.get("username", String.class),
                c.get("scope", String.class)
        );
    }

    /**
     * 描述: 解析配置文件中具有时间单位后缀（s/m/h/d）的 TTL 时间字符串为对应的秒数值
     */
    private long parseTtl(final String ttl) {
        String trimmed = ttl.trim();
        char unit = trimmed.charAt(trimmed.length() - 1);
        long value = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
        return switch (unit) {
            case 's' -> value;
            case 'm' -> value * SECONDS_PER_MINUTE;
            case 'h' -> value * SECONDS_PER_HOUR;
            case 'd' -> value * SECONDS_PER_DAY;
            default -> throw new IllegalArgumentException(ERR_UNKNOWN_TTL_UNIT + trimmed);
        };
    }
}

