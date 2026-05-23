package com.zhiyu.ufp.auth.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zhiyu.auth.jwt")
public class JwtProperties {
    private String algorithm = "RS256";
    private static final int DEFAULT_KEY_SIZE = 2048;
    private int keySize = DEFAULT_KEY_SIZE;
    private String accessTokenTtl = "15m";
    private String refreshTokenTtl = "7d";
    private String issuer = "https://auth.zhiyu.local";
    private String keyDir = "deploy/envs/kubeadm";
}
