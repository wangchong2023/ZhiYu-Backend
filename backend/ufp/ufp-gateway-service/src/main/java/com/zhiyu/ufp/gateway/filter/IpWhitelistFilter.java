package com.zhiyu.ufp.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class IpWhitelistFilter implements GlobalFilter, Ordered {

    private static final int IP_WHITELIST_FILTER_ORDER = HIGHEST_PRECEDENCE + 10;

    private final List<String> whitelist;

    public IpWhitelistFilter(@Value("${zhiyu.security.ip-whitelist:}") final List<String> whitelist) {
        this.whitelist = whitelist;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        if (whitelist.isEmpty()) {
            return chain.filter(exchange);
        }
        String remoteAddr;
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            remoteAddr = remoteAddress.getAddress().getHostAddress();
        } else {
            remoteAddr = "unknown";
        }
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/v1/admin")) {
            return chain.filter(exchange);
        }
        if (whitelist.contains(remoteAddr)) {
            return chain.filter(exchange);
        }
        if (log.isWarnEnabled()) {
            log.warn("Admin access denied for IP {} to {}", remoteAddr, path);
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return IP_WHITELIST_FILTER_ORDER;
    }
}
