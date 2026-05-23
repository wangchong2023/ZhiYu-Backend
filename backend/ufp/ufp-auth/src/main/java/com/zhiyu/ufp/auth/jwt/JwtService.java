package com.zhiyu.ufp.auth.jwt;

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

@Component
@RequiredArgsConstructor
public class JwtService {

    private static final int ERR_TOKEN_EXPIRED = 40102;
    private static final int ERR_TOKEN_INVALID = 40101;
    private static final long MS_PER_SECOND = 1000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 86400L;

    private final JwtProperties properties;
    private final JwtKeyLoader keyLoader;

    public record JwtPair(String accessToken, String refreshToken, long expiresIn) { }

    public JwtPair issue(final long userId, final String username, final String scope) {
        Instant now = Instant.now();
        PrivateKey privateKey = keyLoader.loadPrivateKey();

        String accessToken = buildToken(userId, username, scope,
                "ACCESS", now, parseTtl(properties.getAccessTokenTtl()), privateKey);
        String refreshToken = buildToken(userId, username, scope,
                "REFRESH", now, parseTtl(properties.getRefreshTokenTtl()), privateKey);

        return new JwtPair(accessToken, refreshToken, parseTtl(properties.getAccessTokenTtl()));
    }

    public JwtClaims verify(final String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyLoader.loadPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toJwtClaims(claims);
        } catch (ExpiredJwtException e) {
            throw new BizException(ERR_TOKEN_EXPIRED, "Token 已过期");
        } catch (Exception e) {
            throw new BizException(ERR_TOKEN_INVALID, "Token 无效");
        }
    }

    public Long getUserId(final String token) {
        return Long.parseLong(verify(token).sub());
    }

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

    private long parseTtl(final String ttl) {
        String trimmed = ttl.trim();
        char unit = trimmed.charAt(trimmed.length() - 1);
        long value = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
        return switch (unit) {
            case 's' -> value;
            case 'm' -> value * SECONDS_PER_MINUTE;
            case 'h' -> value * SECONDS_PER_HOUR;
            case 'd' -> value * SECONDS_PER_DAY;
            default -> throw new IllegalArgumentException("未知 TTL 单位: " + trimmed);
        };
    }
}
