/**
 * 文件名: SpaWebFilter.java
 * 描述: 单页应用 (SPA) 路由过滤器。负责将非 API、非静态资源的请求重定向到 /index.html，以支持前端路由。
 */
package com.zhiyu.ufp.gateway.filter;

import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 类名: SpaWebFilter
 * 描述: Web 过滤器，用于 SPA 路由支持，优先级设为 -10。
 */
@Component
@Order(SpaWebFilter.FILTER_ORDER)
public class SpaWebFilter implements WebFilter {

    /**
     * 过滤器优先级顺序值
     */
    public static final int FILTER_ORDER = -10;

    private static final Set<String> API_PREFIXES = Set.of(
        "/api/", "/actuator/", "/swagger-ui/", "/v3/api-docs/",
        "/webjars/", "/favicon.ico"
    );

    private static final Set<String> ASSET_EXTENSIONS = Set.of(
        ".js", ".css", ".png", ".jpg", ".jpeg", ".svg", ".gif", ".ico",
        ".woff", ".woff2", ".ttf", ".eot", ".map", ".json", ".xml", ".txt"
    );


    /**
     * 描述: 对进入网关的请求进行过滤拦截。
     *      若请求路径匹配 API 前缀或静态资源扩展名，则直接放行传给下一个过滤器；
     *      否则，将其内部重定向（mutate path）为 /index.html，以使前端单页应用（SPA）接管路由。
     * @param exchange 服务网络交换器，持有请求与响应信息
     * @param chain 过滤器链
     * @return Mono<Void> 表示过滤处理完毕的信号
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        for (String prefix : API_PREFIXES) {
            if (path.startsWith(prefix)) {
                return chain.filter(exchange);
            }
        }

        for (String ext : ASSET_EXTENSIONS) {
            if (path.toLowerCase(java.util.Locale.ROOT).endsWith(ext)) {
                return chain.filter(exchange);
            }
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
            .path("/index.html")
            .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
