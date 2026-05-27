/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: JwtKeyLoader.java
 * 创建时间: 2026-05-27
 * 描述: 用于从指定的文件路径加载 RSA 公钥和私钥的安全加载器，供 JWT 签名与验签使用。
 */
package com.zhiyu.ufp.auth.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 类名: JwtKeyLoader
 * 描述: 负责读取并解析 PEM 格式的非对称加密密钥。将 Base64 编码的密钥字符串反序列化为 Java 安全密钥对象。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtKeyLoader {

    // 私钥文件名
    private static final String PRIVATE_KEY_FILE = "jwt-private.pem";
    // 公钥文件名
    private static final String PUBLIC_KEY_FILE = "jwt-public.pem";

    // PEM 私钥边界标识符
    private static final String BEGIN_PRIVATE = "-----BEGIN PRIVATE KEY-----";
    private static final String END_PRIVATE = "-----END PRIVATE KEY-----";

    // PEM 公钥边界标识符
    private static final String BEGIN_PUBLIC = "-----BEGIN PUBLIC KEY-----";
    private static final String END_PUBLIC = "-----END PUBLIC KEY-----";

    // 密钥加密算法类型
    private static final String KEY_ALGORITHM = "RSA";

    private final JwtProperties properties;

    /**
     * 描述: 从配置的密钥目录读取并加载 JWT 签名的 RSA 私钥。
     * @return 初始化的 PrivateKey 私钥对象
     * @throws IllegalStateException 若读取或解析私钥失败则抛出非法状态异常
     */
    public PrivateKey loadPrivateKey() {
        try {
            Path path = Path.of(properties.getKeyDir(), PRIVATE_KEY_FILE);
            String content = Files.readString(path);
            String pem = extractPemBody(content, BEGIN_PRIVATE, END_PRIVATE);
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT private key: " + properties.getKeyDir(), e);
        }
    }

    /**
     * 描述: 从配置的密钥目录读取并加载 JWT 验签的 RSA 公钥。
     * @return 初始化的 PublicKey 公钥对象
     * @throws IllegalStateException 若读取或解析公钥失败则抛出非法状态异常
     */
    public PublicKey loadPublicKey() {
        try {
            Path path = Path.of(properties.getKeyDir(), PUBLIC_KEY_FILE);
            String content = Files.readString(path);
            String pem = extractPemBody(content, BEGIN_PUBLIC, END_PUBLIC);
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT public key: " + properties.getKeyDir(), e);
        }
    }

    /**
     * Extract the base64 body from a PEM file, handling both raw PEM and
     * pre-base64-encoded content (legacy K8s Secret double-encoding).
     */
    private String extractPemBody(String content, String beginMarker, String endMarker) {
        if (content.contains(beginMarker)) {
            return content
                    .replace(beginMarker, "")
                    .replace(endMarker, "")
                    .replaceAll("\\s", "");
        }
        return content.replaceAll("\\s", "");
    }
}
