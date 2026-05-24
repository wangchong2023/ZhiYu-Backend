package com.zhiyu.ufp.auth.enums;

import com.zhiyu.ufp.auth.token.TokenType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTypeTest {

    @Test
    void shouldHaveAccessAndRefreshValues() {
        TokenType[] values = TokenType.values();
        assertThat(values).containsExactly(TokenType.ACCESS, TokenType.REFRESH);
    }

    @Test
    void shouldResolveAccessByValueOf() {
        assertThat(TokenType.valueOf("ACCESS")).isEqualTo(TokenType.ACCESS);
    }

    @Test
    void shouldResolveRefreshByValueOf() {
        assertThat(TokenType.valueOf("REFRESH")).isEqualTo(TokenType.REFRESH);
    }
}
