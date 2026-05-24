package com.zhiyu.auth.config;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "zhiyu.auth.oauth")
public class OAuthProperties {

    private final Wechat wechat;
    private final Apple apple;
    private final Google google;

    @ConstructorBinding
    public OAuthProperties(Wechat wechat, Apple apple, Google google) {
        this.wechat = wechat != null ? wechat : new Wechat(null, null, null);
        this.apple = apple != null ? apple : new Apple(null, null, null, null);
        this.google = google != null ? google : new Google(null, null);
    }

    public Wechat getWechat() { return wechat; }
    public Apple getApple() { return apple; }
    public Google getGoogle() { return google; }

    @Value
    public static class Wechat {
        String appId;
        String appSecret;
        String redirectUri;
    }

    @Value
    public static class Apple {
        String clientId;
        String teamId;
        String keyId;
        String privateKey;
    }

    @Value
    public static class Google {
        String clientId;
        String clientSecret;
    }
}
