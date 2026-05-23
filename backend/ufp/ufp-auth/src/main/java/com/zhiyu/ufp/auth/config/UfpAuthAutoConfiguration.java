package com.zhiyu.ufp.auth.config;

import com.zhiyu.ufp.auth.jwt.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@ComponentScan(basePackages = "com.zhiyu.ufp.auth")
@MapperScan(basePackages = "com.zhiyu.ufp.auth.mapper")
public class UfpAuthAutoConfiguration {
}
