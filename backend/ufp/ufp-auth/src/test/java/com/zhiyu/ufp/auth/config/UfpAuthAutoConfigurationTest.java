package com.zhiyu.ufp.auth.config;

import com.zhiyu.ufp.auth.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class UfpAuthAutoConfigurationTest {

    @Test
    void shouldHaveConfigurationAnnotation() {
        Configuration annotation = UfpAuthAutoConfiguration.class.getAnnotation(Configuration.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void shouldHaveEnableConfigurationPropertiesAnnotation() {
        EnableConfigurationProperties annotation =
                UfpAuthAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(JwtProperties.class);
    }

    @Test
    void shouldHaveComponentScanAnnotation() {
        ComponentScan annotation = UfpAuthAutoConfiguration.class.getAnnotation(ComponentScan.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages()).contains("com.zhiyu.ufp.auth");
    }

    @Test
    void shouldBeInstantiable() {
        UfpAuthAutoConfiguration config = new UfpAuthAutoConfiguration();
        assertThat(config).isNotNull();
    }
}
