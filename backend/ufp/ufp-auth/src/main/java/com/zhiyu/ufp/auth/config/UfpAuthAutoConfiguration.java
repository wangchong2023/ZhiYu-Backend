package com.zhiyu.ufp.auth.config;

import com.zhiyu.ufp.auth.jwt.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@ComponentScan(basePackages = "com.zhiyu.ufp.auth")
@MapperScan(basePackages = "com.zhiyu.ufp.auth.mapper")
public class UfpAuthAutoConfiguration {

    private static final String DS_KEY_UFP_AUTH = "ufp_auth";

    @Bean
    @Primary
    public UfpRoutingDataSource ufpRoutingDataSource(final DataSource dataSource) {
        return new UfpRoutingDataSource(dataSource,
                Map.of(DS_KEY_UFP_AUTH, dataSource));
    }
}
