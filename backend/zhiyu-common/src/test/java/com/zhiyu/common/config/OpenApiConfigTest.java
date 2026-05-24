package com.zhiyu.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void shouldCreateOpenAPIWithTitle() {
        OpenAPI api = config.zhiyuOpenAPI();

        assertThat(api).isNotNull();
        assertThat(api.getInfo()).isNotNull();
        assertThat(api.getInfo().getTitle()).isEqualTo("ZhiYu API");
    }

    @Test
    void shouldSetApiVersion() {
        OpenAPI api = config.zhiyuOpenAPI();

        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldConfigureContactInfo() {
        OpenAPI api = config.zhiyuOpenAPI();

        assertThat(api.getInfo().getContact()).isNotNull();
        assertThat(api.getInfo().getContact().getName()).isEqualTo("ZhiYu Team");
        assertThat(api.getInfo().getContact().getEmail()).isEqualTo("dev@zhiyu.local");
    }

    @Test
    void shouldSetLicense() {
        OpenAPI api = config.zhiyuOpenAPI();

        assertThat(api.getInfo().getLicense()).isNotNull();
        assertThat(api.getInfo().getLicense().getName()).isEqualTo("Proprietary");
    }

    @Test
    void shouldAddBearerSecurityScheme() {
        OpenAPI api = config.zhiyuOpenAPI();

        assertThat(api.getSecurity()).isNotNull().hasSize(1);
        SecurityRequirement req = api.getSecurity().get(0);
        assertThat(req.get("Bearer")).isNotNull();

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("Bearer");
        assertThat(scheme).isNotNull();
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }
}
