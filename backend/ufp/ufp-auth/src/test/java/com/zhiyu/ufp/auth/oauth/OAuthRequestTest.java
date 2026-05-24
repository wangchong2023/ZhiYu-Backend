package com.zhiyu.ufp.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthRequestTest {

    @Test
    void shouldConstructRecord() {
        OAuthRequest request = new OAuthRequest("auth-code", "state-value", "id-token-value");

        assertThat(request.code()).isEqualTo("auth-code");
        assertThat(request.state()).isEqualTo("state-value");
        assertThat(request.idToken()).isEqualTo("id-token-value");
    }

    @Test
    void shouldAllowNullFields() {
        OAuthRequest request = new OAuthRequest(null, null, null);

        assertThat(request.code()).isNull();
        assertThat(request.state()).isNull();
        assertThat(request.idToken()).isNull();
    }

    @Test
    void shouldHaveCorrectToString() {
        OAuthRequest request = new OAuthRequest("code", "state", "token");
        String str = request.toString();

        assertThat(str).contains("code").contains("state").contains("token");
    }

    @Test
    void shouldBeEqualWhenFieldsEqual() {
        OAuthRequest r1 = new OAuthRequest("code", "state", "token");
        OAuthRequest r2 = new OAuthRequest("code", "state", "token");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenFieldsDiffer() {
        OAuthRequest r1 = new OAuthRequest("code1", "state", "token");
        OAuthRequest r2 = new OAuthRequest("code2", "state", "token");

        assertThat(r1).isNotEqualTo(r2);
    }
}
