package com.zhiyu.ufp.auth.oauth;

import com.zhiyu.ufp.common.exception.BizException;

public interface OAuthProvider {

    String getProviderName();

    OAuthUserInfo authorize(OAuthRequest request) throws BizException;
}
