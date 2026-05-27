package com.zhiyu.ufp.auth.token;

import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.cache.ICacheOperate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private final ICacheOperate cacheOperate;

    public void add(final String token, final long ttlSeconds) {
        cacheOperate.set(CacheKeys.TOKEN_BLACKLIST + token, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(final String token) {
        return cacheOperate.get(CacheKeys.TOKEN_BLACKLIST + token) != null;
    }
}
