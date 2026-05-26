package com.zhiyu.common.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AuthClientFallbackFactory implements FallbackFactory<AuthClient> {

    @Override
    public AuthClient create(final Throwable cause) {
        if (log.isErrorEnabled()) {
            log.error("AuthClient fallback activated: {}", cause.getMessage(), cause);
        }
        return new AuthClient() {
            @Override
            public Map<String, Object> getUserById(final Long userId) {
                throw new UnsupportedOperationException("Auth service unavailable");
            }

            @Override
            public Map<String, Object> getUserByUsername(final String username) {
                throw new UnsupportedOperationException("Auth service unavailable");
            }

            @Override
            public Map<String, Object> getRoleById(final Long roleId) {
                throw new UnsupportedOperationException("Auth service unavailable");
            }
        };
    }
}
