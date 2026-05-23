package com.zhiyu.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zhiyu.auth.oauth")
public class OAuthProperties {

    private Wechat wechat = new Wechat();
    private Apple apple = new Apple();
    private Google google = new Google();

    @Data
    public static class Wechat {
        private String appId;
        private String appSecret;
        private String redirectUri;
    }

    @Data
    public static class Apple {
        private String clientId;
        private String teamId;
        private String keyId;
        private String privateKey;
    }

    @Data
    public static class Google {
        private String clientId;
        private String clientSecret;
    }
}
