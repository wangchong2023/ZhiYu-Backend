package com.zhiyu.auth.config;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "zhiyu.auth.oauth")
public class OAuthProperties {

    private final Wechat wechat;
    private final Apple apple;
    private final Google google;
    private final Github github;

    @ConstructorBinding
    public OAuthProperties(final Wechat wechat, final Apple apple,
                           final Google google, final Github github) {
        this.wechat = wechat != null ? wechat : new Wechat(null, null, null);
        this.apple = apple != null ? apple : new Apple(null, null, null, null);
        this.google = google != null ? google : new Google(null, null);
        this.github = github != null ? github : new Github(null, null);
    }

    public Wechat getWechat() {
        return wechat;
    }
    public Apple getApple() {
        return apple;
    }
    public Google getGoogle() {
        return google;
    }
    public Github getGithub() {
        return github;
    }

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

    @Value
    public static class Github {
        String clientId;
        String clientSecret;
    }
}
