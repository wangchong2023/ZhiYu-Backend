package com.zhiyu.ufp.auth.token;
 
import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.cache.ICacheOperate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
 
import java.util.concurrent.TimeUnit;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class TokenBlacklistTest {
 
    @Mock
    private ICacheOperate cacheOperate;
 
    @InjectMocks
    private TokenBlacklist tokenBlacklist;
 
    @Test
    void shouldAddTokenToBlacklist() {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
        long ttlSeconds = 3600L;
 
        tokenBlacklist.add(token, ttlSeconds);
 
        verify(cacheOperate).set(CacheKeys.TOKEN_BLACKLIST + token, "1", ttlSeconds, TimeUnit.SECONDS);
    }
 
    @Test
    void shouldReturnTrueWhenTokenIsBlacklisted() {
        String token = "blacklisted-token";
        when(cacheOperate.get(CacheKeys.TOKEN_BLACKLIST + token)).thenReturn("1");
 
        boolean result = tokenBlacklist.isBlacklisted(token);
 
        assertThat(result).isTrue();
        verify(cacheOperate).get(CacheKeys.TOKEN_BLACKLIST + token);
    }
 
    @Test
    void shouldReturnFalseWhenTokenIsNotBlacklisted() {
        String token = "clean-token";
        when(cacheOperate.get(CacheKeys.TOKEN_BLACKLIST + token)).thenReturn(null);
 
        boolean result = tokenBlacklist.isBlacklisted(token);
 
        assertThat(result).isFalse();
        verify(cacheOperate).get(CacheKeys.TOKEN_BLACKLIST + token);
    }
 
    @Test
    void shouldUseCorrectPrefix() {
        String token = "test-token";
 
        tokenBlacklist.add(token, 60L);
 
        verify(cacheOperate).set(CacheKeys.TOKEN_BLACKLIST + token, "1", 60L, TimeUnit.SECONDS);
    }
}
