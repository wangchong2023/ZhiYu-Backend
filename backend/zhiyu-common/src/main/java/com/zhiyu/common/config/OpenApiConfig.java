package com.zhiyu.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zhiyuOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ZhiYu API")
                        .description("智宇后端 REST API 文档 — 供 Apple 客户端及管理后台调用")
                        .version("1.0.0")
                        .contact(new Contact().name("ZhiYu Team").email("dev@zhiyu.local"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .schemaRequirement("Bearer", new SecurityScheme()
                        .name("Bearer")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("在登录接口获取 accessToken，填入此处（不含 Bearer 前缀）"));
    }
}
