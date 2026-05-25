package com.zhiyu.ufp.common.cache;

public final class CacheKeys {

    private CacheKeys() {}

    // -- Login --
    public static final String LOGIN_ATTEMPT = "login:attempt:%s";
    public static final String LOGIN_LOCK = "login:lock:%s";
    public static final String LOGIN_CAPTCHA_REQUIRED = "login:captcha:%s";

    // -- Registration --
    public static final String REGISTER_RATE = "register:rate:%s";

    // -- SMS --
    public static final String SMS_CODE = "sms:%s:%s";

    // -- Captcha --
    public static final String CAPTCHA_PREFIX = "captcha:";

    // -- Rate limiting --
    public static final String RATE_WINDOW = "rate:%s:%s";

    // -- Token blacklist --
    public static final String TOKEN_BLACKLIST = "token:blacklist:";

    // -- WebAuthn challenge --
    public static final String WEBAUTHN_CHALLENGE = "webauthn:challenge:";

    // -- Action token --
    public static final String ACTION_TOKEN = "action:token:%s";

    public static String key(final String template, final Object... args) {
        return String.format(template, args);
    }
}
