package com.zhiyu.ufp.auth.oauth;

public record OAuthRequest(String code, String state, String idToken) { }
