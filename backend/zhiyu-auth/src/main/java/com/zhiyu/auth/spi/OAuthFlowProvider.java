package com.zhiyu.auth.spi;

import com.zhiyu.auth.service.OAuthService;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowProvider;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthFlowProvider implements AuthFlowProvider {

    private final OAuthService oAuthService;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.GOOGLE;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        String providerName = context.get("providerName");
        OAuthRequest request = context.get("oauthRequest");
        return oAuthService.authenticate(providerName, request);
    }
}
