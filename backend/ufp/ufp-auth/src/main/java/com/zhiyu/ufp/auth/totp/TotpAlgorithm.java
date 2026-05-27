/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: TotpAlgorithm.java
 * 创建时间: 2026-05-27
 * 描述: 基于 HMAC-SHA1 算法的 TOTP（基于时间的一次性密码）算法工具类，支持 Base32 编码解码与双因素验证。
 */
package com.zhiyu.ufp.auth.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 类名: TotpAlgorithm
 * 描述: 实现符合 RFC 6238 规范的 TOTP 算法核心组件，提供两步验证所必须的动态口令生成与校验方法。
 */
public final class TotpAlgorithm {

    // Base32 编码表（包含 A-Z 和 2-7，共 32 个字符）
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    // Base32 查找索引表
    private static final int[] BASE32_LOOKUP = buildBase32Lookup();
    // Base32 掩码值 (二进制 11111)
    private static final int BASE32_MASK = 0x1F;
    // 每个 Base32 字符所占的二进制位数
    private static final int BITS_PER_BASE32_CHAR = 5;
    // 每个字节所占的二进制位数
    private static final int BITS_PER_BYTE = 8;
    // Long 类型数值对应的字节数
    private static final int LONG_BYTES = 8;
    // 字节的最大掩码值 (0xFF)
    private static final int MASK_BYTE = 0xFF;
    // 过滤符号位的掩码值 (0x7F)
    private static final int MASK_SIGN_BIT = 0x7F;
    // 动态验证码的取模基数（6位验证码取 1,000,000）
    private static final int TOTP_MODULUS = 1_000_000;
    // 动态口令的指定位数
    private static final int TOTP_DIGITS = 6;
    // TOTP 默认时间窗口步长（秒）
    private static final int TIME_STEP_SECONDS = 30;
    // 每秒对应的毫秒数
    private static final int MILLIS_PER_SECOND = 1000;
    // 获取偏移量所用的掩码 (0x0F)
    private static final int TOTP_OFFSET_MASK = 0x0F;
    // 位移操作常量值
    private static final int SHIFT_24 = 24;
    private static final int SHIFT_16 = 16;
    private static final int SHIFT_8 = 8;
    private static final int OFFSET_3 = 3;
    // ASCII 字符索引查找表大小
    private static final int ASCII_LOOKUP_SIZE = 128;
    // HMAC-SHA1 算法签名常量名
    private static final String HMAC_SHA1 = "HmacSHA1";

    /**
     * 描述: 私有构造函数，防止被实例化
     */
    private TotpAlgorithm() { }

    /**
     * 描述: 根据共享密钥及当前计数器生成 6 位 TOTP 动态密码。
     * @param key 共享密钥的字节数组
     * @param counter 当前时间戳计算出的计数器
     * @return 6 位格式化的动态验证码字符串
     * @throws NoSuchAlgorithmException 当找不到指定的 HmacSHA1 算法时抛出
     * @throws InvalidKeyException 当密钥非法时抛出
     */
    public static String generateTotp(final byte[] key, final long counter) throws
            NoSuchAlgorithmException, InvalidKeyException {
        byte[] counterBytes = longToBytes(counter);

        Mac mac = Mac.getInstance(HMAC_SHA1);
        mac.init(new SecretKeySpec(key, HMAC_SHA1));
        byte[] hash = mac.doFinal(counterBytes);

        int offset = hash[hash.length - 1] & TOTP_OFFSET_MASK;
        int binary = ((hash[offset] & MASK_SIGN_BIT) << SHIFT_24)
                | ((hash[offset + 1] & MASK_BYTE) << SHIFT_16)
                | ((hash[offset + 2] & MASK_BYTE) << SHIFT_8)
                | (hash[offset + OFFSET_3] & MASK_BYTE);

        int otp = binary % TOTP_MODULUS;
        return String.format("%0" + TOTP_DIGITS + "d", otp);
    }

    /**
     * 描述: 校对客户端输入的动态口令与服务端通过共享密钥计算出的期望口令是否相符。
     * @param secret Base32 编码格式的共享密钥字符串
     * @param code 客户端提交的 6 位动态口令
     * @return 匹配成功返回 true，否则返回 false
     */
    public static boolean verifyCode(final String secret, final String code) {
        if (code == null || code.length() != TOTP_DIGITS) {
            return false;
        }
        try {
            byte[] key = base32Decode(secret);
            long counter = System.currentTimeMillis() / MILLIS_PER_SECOND / TIME_STEP_SECONDS;
            String expected = generateTotp(key, counter);
            return code.equals(expected);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    /**
     * 描述: 将 Base32 编码的密钥字符串解码还原为原始的字节数组。
     * @param input Base32 编码的密钥字符串
     * @return 解码后的字节数组
     */
    public static byte[] base32Decode(final String input) {
        String normalized = input.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z2-7]", "");
        int outputLength = normalized.length() * BITS_PER_BASE32_CHAR / BITS_PER_BYTE;
        byte[] result = new byte[outputLength];
        int buffer = 0;
        int bitsInBuffer = 0;
        int resultIndex = 0;

        for (int i = 0; i < normalized.length(); i++) {
            int value = BASE32_LOOKUP[normalized.charAt(i)];
            if (value == -1) {
                continue;
            }
            buffer = (buffer << BITS_PER_BASE32_CHAR) | value;
            bitsInBuffer += BITS_PER_BASE32_CHAR;
            if (bitsInBuffer >= BITS_PER_BYTE) {
                bitsInBuffer -= BITS_PER_BYTE;
                result[resultIndex++] = (byte) ((buffer >> bitsInBuffer) & MASK_BYTE);
            }
        }
        return result;
    }

    /**
     * 描述: 将给定的字节数组编码为符合 Base32 格式规范的字符串。
     * @param data 原始的字节数组
     * @return Base32 编码字符串
     */
    static String base32Encode(final byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (byte b : data) {
            buffer = (buffer << BITS_PER_BYTE) | (b & MASK_BYTE);
            bitsInBuffer += BITS_PER_BYTE;
            while (bitsInBuffer >= BITS_PER_BASE32_CHAR) {
                bitsInBuffer -= BITS_PER_BASE32_CHAR;
                sb.append(BASE32_ALPHABET.charAt((buffer >> bitsInBuffer) & BASE32_MASK));
            }
        }
        if (bitsInBuffer > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (BITS_PER_BASE32_CHAR - bitsInBuffer)) & BASE32_MASK));
        }
        return sb.toString();
    }

    /**
     * 描述: 私有辅助方法，将 long 数值转换为长度为 8 的字节数组（大端序字节填充）。
     */
    private static byte[] longToBytes(final long value) {
        byte[] result = new byte[LONG_BYTES];
        for (int i = LONG_BYTES - 1; i >= 0; i--) {
            result[i] = (byte) (value & MASK_BYTE);
        }
        return result;
    }

    /**
     * 描述: 初始化构建 Base32 ASCII 字符检索索引映射表。
     */
    private static int[] buildBase32Lookup() {
        int[] lookup = new int[ASCII_LOOKUP_SIZE];
        for (int i = 0; i < ASCII_LOOKUP_SIZE; i++) {
            lookup[i] = -1;
        }
        for (int i = 0; i < BASE32_ALPHABET.length(); i++) {
            lookup[BASE32_ALPHABET.charAt(i)] = i;
        }
        // 提供容错映射：将易混淆的数字和字母进行索引重定向归一化
        lookup['0'] = lookup['O'];
        lookup['1'] = lookup['L'];
        lookup['8'] = lookup['B'];
        return lookup;
    }
}