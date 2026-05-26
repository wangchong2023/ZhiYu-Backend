package com.zhiyu.ufp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

class SpaWebFilterTest {

    private SpaWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SpaWebFilter();
    }

    @Test
    void shouldPassThroughApiPaths() {
        String[] paths = {"/api/v1/auth/login", "/actuator/health", "/swagger-ui/index.html", "/v3/api-docs/default"};
        for (String path : paths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
            );
            WebFilterChain chain = filterExchange -> {
                assertThat(filterExchange.getRequest().getURI().getPath()).isEqualTo(path);
                return Mono.empty();
            };
            filter.filter(exchange, chain).block();
        }
    }

    @Test
    void shouldPassThroughAssetFiles() {
        String[] paths = {"/assets/main.js", "/css/style.css", "/images/logo.png", "/fonts/roboto.woff2"};
        for (String path : paths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
            );
            WebFilterChain chain = filterExchange -> {
                assertThat(filterExchange.getRequest().getURI().getPath()).isEqualTo(path);
                return Mono.empty();
            };
            filter.filter(exchange, chain).block();
        }
    }

    @Test
    void shouldRewriteSpaRouteToIndexHtml() {
        String[] paths = {"/dashboard", "/admin/users", "/settings/profile", "/monitor/overview", "/"};
        for (String path : paths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
            );
            WebFilterChain chain = filterExchange -> {
                assertThat(filterExchange.getRequest().getURI().getPath()).isEqualTo("/index.html");
                return Mono.empty();
            };
            filter.filter(exchange, chain).block();
        }
    }

    @Test
    void shouldPassThroughFavicon() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/favicon.ico").build()
        );
        WebFilterChain chain = filterExchange -> {
            assertThat(filterExchange.getRequest().getURI().getPath()).isEqualTo("/favicon.ico");
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
    }
}
