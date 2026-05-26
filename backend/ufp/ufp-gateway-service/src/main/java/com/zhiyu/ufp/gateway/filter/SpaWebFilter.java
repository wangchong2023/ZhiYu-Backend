package com.zhiyu.ufp.gateway.filter;

import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-10)
public class SpaWebFilter implements WebFilter {

    private static final Set<String> API_PREFIXES = Set.of(
        "/api/", "/actuator/", "/swagger-ui/", "/v3/api-docs/",
        "/webjars/", "/favicon.ico"
    );

    private static final Set<String> ASSET_EXTENSIONS = Set.of(
        ".js", ".css", ".png", ".jpg", ".jpeg", ".svg", ".gif", ".ico",
        ".woff", ".woff2", ".ttf", ".eot", ".map", ".json", ".xml", ".txt"
    );

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        for (String prefix : API_PREFIXES) {
            if (path.startsWith(prefix)) {
                return chain.filter(exchange);
            }
        }

        for (String ext : ASSET_EXTENSIONS) {
            if (path.toLowerCase().endsWith(ext)) {
                return chain.filter(exchange);
            }
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
            .path("/index.html")
            .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
