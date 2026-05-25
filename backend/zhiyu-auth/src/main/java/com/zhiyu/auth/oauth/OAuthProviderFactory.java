package com.zhiyu.auth.oauth;

import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OAuthProviderFactory {

    private static final int ERR_UNSUPPORTED_PROVIDER = 41603;

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
            throw new BizException(ERR_UNSUPPORTED_PROVIDER, "Unsupported login provider: " + name);
        }
        return provider;
    }
}
