package com.zhiyu.auth.oauth;

import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OAuthProviderFactory {

    private final Map<String, OAuthProvider> providers;

    public OAuthProviderFactory(final List<OAuthProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(Locale.ENGLISH),
                        Function.identity()
                ));
    }

    public OAuthProvider getProvider(final String name) {
        OAuthProvider provider = providers.get(name.toLowerCase(Locale.ENGLISH));
        if (provider == null) {
            log.warn("Unsupported OAuth provider requested: {}", name);
            throw new BizException(BizErrorCode.OAUTH_UNSUPPORTED_PROVIDER);
        }
        return provider;
    }
}
