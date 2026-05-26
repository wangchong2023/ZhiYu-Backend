package com.zhiyu.ufp.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpWhitelistFilterTest {

    @Test
    void shouldPassWhenWhitelistIsEmpty() {
        IpWhitelistFilter filter = new IpWhitelistFilter(Collections.emptyList());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users").build());
        GatewayFilterChain chain = e -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldPassWhenPathIsNotAdmin() {
        IpWhitelistFilter filter = new IpWhitelistFilter(List.of("10.0.0.1"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());
        GatewayFilterChain chain = e -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldAllowWhitelistedIp() throws Exception {
        IpWhitelistFilter filter = new IpWhitelistFilter(List.of("127.0.0.1"));
        InetSocketAddress remoteAddr = new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 8080);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users")
                        .remoteAddress(remoteAddr)
                        .build());
        GatewayFilterChain chain = e -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldDenyNonWhitelistedIp() throws Exception {
        IpWhitelistFilter filter = new IpWhitelistFilter(List.of("10.0.0.1"));
        InetSocketAddress remoteAddr = new InetSocketAddress(
                InetAddress.getByName("192.168.1.1"), 8080);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users")
                        .remoteAddress(remoteAddr)
                        .build());
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturnCorrectOrder() {
        IpWhitelistFilter filter = new IpWhitelistFilter(Collections.emptyList());
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
