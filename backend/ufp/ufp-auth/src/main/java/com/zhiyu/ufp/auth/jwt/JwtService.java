package com.zhiyu.ufp.auth.jwt;

import com.zhiyu.ufp.common.exception.BizException;
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
