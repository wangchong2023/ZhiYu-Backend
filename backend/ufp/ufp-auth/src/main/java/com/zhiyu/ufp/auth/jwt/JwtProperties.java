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
