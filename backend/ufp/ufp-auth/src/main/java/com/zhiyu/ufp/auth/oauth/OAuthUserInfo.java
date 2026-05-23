package com.zhiyu.ufp.auth.oauth;

public record OAuthUserInfo(String openid, String unionid,
                             String nickname, String avatarUrl,
                             String email, boolean emailVerified) { }
