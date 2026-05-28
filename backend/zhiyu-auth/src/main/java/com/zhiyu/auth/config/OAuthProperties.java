package com.zhiyu.auth.config;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "zhiyu.auth.oauth")
public class OAuthProperties {

    /** 微信登录配置 */
    private final Wechat wechat;
    /** Apple登录配置 */
    private final Apple apple;
    /** Google登录配置 */
    private final Google google;
    /** GitHub登录配置 */
    private final Github github;
    /** 运营商一键登录配置 */
    private final Carrier carrier;

    /**
     * OAuth配置构造器。
     *
     * @param wechat 微信配置
     * @param apple Apple配置
     * @param google Google配置
     * @param github GitHub配置
     * @param carrier 运营商配置
     */
    @ConstructorBinding
    public OAuthProperties(final Wechat wechat, final Apple apple,
                           final Google google, final Github github,
                           final Carrier carrier) {
        this.wechat = wechat != null ? wechat : new Wechat(null, null, null);
        this.apple = apple != null ? apple : new Apple(null, null, null, null);
        this.google = google != null ? google : new Google(null, null);
        this.github = github != null ? github : new Github(null, null);
        this.carrier = carrier != null ? carrier : new Carrier(null, null, null);
    }

    /**
     * 获取微信配置。
     *
     * @return 微信配置对象
     */
    public Wechat getWechat() {
        return wechat;
    }

    /**
     * 获取Apple配置。
     *
     * @return Apple配置对象
     */
    public Apple getApple() {
        return apple;
    }

    /**
     * 获取Google配置。
     *
     * @return Google配置对象
     */
    public Google getGoogle() {
        return google;
    }

    /**
     * 获取GitHub配置。
     *
     * @return GitHub配置对象
     */
    public Github getGithub() {
        return github;
    }

    /**
     * 获取运营商配置。
     *
     * @return 运营商一键登录配置对象
     */
    public Carrier getCarrier() {
        return carrier;
    }

    /**
     * 微信配置属性。
     */
    @Value
    public static class Wechat {
        /** 微信 appId */
        String appId;
        /** 微信 appSecret */
        String appSecret;
        /** 微信重定向 URI */
        String redirectUri;
    }

    /**
     * Apple 登录配置属性。
     */
    @Value
    public static class Apple {
        /** Apple 客户端 ID */
        String clientId;
        /** Apple 团队 ID */
        String teamId;
        /** Apple 密钥 ID */
        String keyId;
        /** Apple 私钥 */
        String privateKey;
    }

    /**
     * Google 登录配置属性。
     */
    @Value
    public static class Google {
        /** Google 客户端 ID */
        String clientId;
        /** Google 客户端密钥 */
        String clientSecret;
    }

    /**
     * GitHub 登录配置属性。
     */
    @Value
    public static class Github {
        /** GitHub 客户端 ID */
        String clientId;
        /** GitHub 客户端密钥 */
        String clientSecret;
    }

    /**
     * 运营商一键登录配置属性（阿里云号码认证服务）。
     */
    @Value
    public static class Carrier {
        /** 阿里云 AccessKey ID */
        String accessKeyId;
        /** 阿里云 AccessKey Secret */
        String accessKeySecret;
        /** 阿里云 Region ID */
        String regionId;
    }
}
