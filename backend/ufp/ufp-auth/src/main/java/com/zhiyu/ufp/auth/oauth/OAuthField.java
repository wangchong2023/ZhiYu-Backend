package com.zhiyu.ufp.auth.oauth;

public final class OAuthField {

    // ── JSON field names ──
    public static final String ERROR = "error";
    public static final String ERROR_DESCRIPTION = "error_description";
    public static final String ERROR_CODE = "errcode";
    public static final String ERROR_MSG = "errmsg";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String OPENID = "openid";
    public static final String UNIONID = "unionid";
    public static final String SUB = "sub";
    public static final String EMAIL = "email";
    public static final String EMAIL_VERIFIED = "email_verified";
    public static final String NAME = "name";
    public static final String PICTURE = "picture";
    public static final String NICKNAME = "nickname";
    public static final String HEAD_IMG_URL = "headimgurl";
    public static final String KID = "kid";
    public static final String KEYS = "keys";
    public static final String N = "n";
    public static final String E = "e";

    // ── Provider names ──
    public static final String PROVIDER_GOOGLE = "GOOGLE";
    public static final String PROVIDER_WECHAT = "WECHAT";
    public static final String PROVIDER_APPLE = "APPLE";
    /** GitHub OAuth 2.0 平台标识 */
    public static final String PROVIDER_GITHUB = "GITHUB";

    // ── Scope values ──
    public static final String SCOPE_ADMIN = "ADMIN";
    public static final String SCOPE_LIMITED = "LIMITED";
    public static final String SCOPE_FULL = "FULL";
    public static final String SCOPE_OPENID = "openid";
    /** 游客匿名 scope，仅允许只读类功能 */
    public static final String SCOPE_GUEST = "GUEST";
    public static final String TOKEN_TYPE = "Bearer";


    private OAuthField() {
    }
}
