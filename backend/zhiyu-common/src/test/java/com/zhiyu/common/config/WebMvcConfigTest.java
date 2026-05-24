package com.zhiyu.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private org.springframework.web.servlet.config.annotation.InterceptorRegistry registry;

    @Mock
    private org.springframework.web.servlet.config.annotation.InterceptorRegistration registration;

    @InjectMocks
    private WebMvcConfig config;

    @Test
    void shouldCreateCookieLocaleResolver() {
        var resolver = config.localeResolver();

        assertThat(resolver).isInstanceOf(CookieLocaleResolver.class);
    }

    @Test
    void shouldAddLocaleChangeInterceptor() {
        when(registry.addInterceptor(any(LocaleChangeInterceptor.class))).thenReturn(registration);

        config.addInterceptors(registry);

        ArgumentCaptor<LocaleChangeInterceptor> captor =
                ArgumentCaptor.forClass(LocaleChangeInterceptor.class);
        verify(registry).addInterceptor(captor.capture());
        assertThat(captor.getValue().getParamName()).isEqualTo("lang");
    }
}
