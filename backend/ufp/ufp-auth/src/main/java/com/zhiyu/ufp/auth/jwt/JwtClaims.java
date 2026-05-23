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
) { }
