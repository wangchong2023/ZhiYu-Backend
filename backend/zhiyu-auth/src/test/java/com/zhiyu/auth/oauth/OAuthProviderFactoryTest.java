package com.zhiyu.auth.oauth;

import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProviderFactoryTest {

    private OAuthProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OAuthProviderFactory(List.of(
                new TestOAuthProvider("APPLE"),
                new TestOAuthProvider("GOOGLE"),
                new TestOAuthProvider("WECHAT")
        ));
    }

    @Test
    void shouldReturnProviderByName() {
        OAuthProvider provider = factory.getProvider("apple");
        assertThat(provider.getProviderName()).isEqualTo("APPLE");
    }

    @Test
    void shouldReturnProviderCaseInsensitively() {
        OAuthProvider provider = factory.getProvider("Google");
        assertThat(provider.getProviderName()).isEqualTo("GOOGLE");
    }

    @Test
    void shouldReturnProviderWithMixedCase() {
        OAuthProvider provider = factory.getProvider("WeChat");
        assertThat(provider.getProviderName()).isEqualTo("WECHAT");
    }

    @Test
    void shouldReturnProviderWithUppercase() {
        OAuthProvider provider = factory.getProvider("APPLE");
        assertThat(provider.getProviderName()).isEqualTo("APPLE");
    }

    @Test
    void shouldReturnProviderWithLowercase() {
        OAuthProvider provider = factory.getProvider("google");
        assertThat(provider.getProviderName()).isEqualTo("GOOGLE");
    }

    @Test
    void shouldThrowOnUnknownProvider() {
        assertThatThrownBy(() -> factory.getProvider("github"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的登录方式");
    }

    @Test
    void shouldThrowOnEmptyProviderName() {
        assertThatThrownBy(() -> factory.getProvider(""))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的登录方式");
    }

    @Test
    void shouldThrowNpeWhenNullProviderName() {
        assertThatThrownBy(() -> factory.getProvider(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleEmptyProviderList() {
        OAuthProviderFactory emptyFactory = new OAuthProviderFactory(List.of());
        assertThatThrownBy(() -> emptyFactory.getProvider("apple"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的登录方式");
    }

    @Test
    void shouldHandleSingleProvider() {
        OAuthProviderFactory singleFactory = new OAuthProviderFactory(
                List.of(new TestOAuthProvider("GITHUB")));
        OAuthProvider provider = singleFactory.getProvider("github");
        assertThat(provider.getProviderName()).isEqualTo("GITHUB");
    }

    // ── Test Provider implementation ────────────────────────────

    private static class TestOAuthProvider implements OAuthProvider {
        private final String name;

        TestOAuthProvider(String name) {
            this.name = name;
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public OAuthUserInfo authorize(OAuthRequest request) throws BizException {
            return new OAuthUserInfo("test-openid", null, "Test", null, null, false);
        }
    }
}
