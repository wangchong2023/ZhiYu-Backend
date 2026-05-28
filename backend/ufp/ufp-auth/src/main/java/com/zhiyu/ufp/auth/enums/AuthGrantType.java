package com.zhiyu.ufp.auth.enums;

/**
 * 认证授权类型枚举。
 *
 * <p>每个值对应一种登录/注册方式，由 {@code AuthFlowManager} 分发给对应的 {@code AuthFlowProvider}。</p>
 * <ul>
 *   <li>{@link #PASSWORD}   — 用户名/邮箱/手机号 + 密码</li>
 *   <li>{@link #SMS}        — 手机号 + 短信验证码（自动注册）</li>
 *   <li>{@link #CARRIER}    — 运营商本机号码一键登录（阿里云号码认证服务）</li>
 *   <li>{@link #GUEST}      — 游客匿名模式，签发受限 scope JWT</li>
 *   <li>{@link #TOTP}       — 两步验证 TOTP 二次确认</li>
 *   <li>{@link #APPLE}      — Sign in with Apple</li>
 *   <li>{@link #GOOGLE}     — Google OAuth 2.0</li>
 *   <li>{@link #WECHAT}     — 微信开放平台 OAuth</li>
 *   <li>{@link #WEB_AUTHN} — WebAuthn 通行密钥</li>
 * </ul>
 */
public enum AuthGrantType {
    /** 用户名/邮箱/手机号 + 密码登录 */
    PASSWORD,
    /** 手机号 + 短信验证码，首次自动注册 */
    SMS,
    /** 运营商本机号码一键登录（阿里云号码认证服务），App 侧 SDK 获取 accessToken 后传给后端换取手机号 */
    CARRIER,
    /** 游客匿名模式，无需注册，签发 scope=GUEST 受限 JWT */
    GUEST,
    /** TOTP 二次验证 */
    TOTP,
    /** Sign in with Apple */
    APPLE,
    /** Google OAuth 2.0 */
    GOOGLE,
    /** 微信开放平台 OAuth */
    WECHAT,
    /** WebAuthn 通行密钥 */
    WEB_AUTHN
}
