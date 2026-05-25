package com.zhiyu.ufp.auth.spi;

import com.zhiyu.ufp.auth.enums.AuthGrantType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AuthFlowContext {

    private final AuthGrantType grantType;
    private final Map<String, Object> attributes;

    private AuthFlowContext(final AuthGrantType grantType) {
        this.grantType = grantType;
        this.attributes = new HashMap<>();
    }

    public static AuthFlowContext of(final AuthGrantType grantType) {
        return new AuthFlowContext(grantType);
    }

    public AuthFlowContext with(final String key, final Object value) {
        this.attributes.put(key, value);
        return this;
    }

    public AuthGrantType getGrantType() {
        return grantType;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final String key) {
        return (T) attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
