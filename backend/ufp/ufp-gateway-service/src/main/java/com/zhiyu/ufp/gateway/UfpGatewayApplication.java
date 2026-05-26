package com.zhiyu.ufp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@SuppressWarnings("PMD.UseUtilityClass")
public class UfpGatewayApplication {

    public static void main(final String[] args) {
        SpringApplication.run(UfpGatewayApplication.class, args);
    }
}
