/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: IpWhitelistFilter.java
 * 创建时间: 2026-05-28
 * 描述: 全局网关管理接口 IP 准入白名单过滤器。专职拦截所有进入后台管理端（/api/v1/admin）的请求并实施严格的 IP 白名单准入拦截，保障核心管理服务的安全性。
 */
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

/**
 * 类名: IpWhitelistFilter
 * 描述: 网关全局拦截过滤器。当客户端请求后台管理员相关的端点（路径匹配 /api/v1/admin）时，
 * 提取客户端真实 IP 并与 Nacos 动态下发的白名单列表进行匹配校验。不匹配者将直接阻断并响应 403 Forbidden。
 */
@Slf4j
@Component
public class IpWhitelistFilter implements GlobalFilter, Ordered {

    /**
     * 过滤器执行顺序。优先级定义在最前列，确保在鉴权和审计日志之后，进入具体微服务分发之前阻断非法流量。
     */
    private static final int IP_WHITELIST_FILTER_ORDER = HIGHEST_PRECEDENCE + 10;

    /**
     * 后端管理端路径前缀特征字
     */
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";

    /**
     * 从配置中心注入的 IP 白名单列表（对应配置 zhiyu.security.ip-whitelist）
     */
    private final List<String> whitelist;

    /**
     * 描述: 构造器注入方式加载 IP 白名单
     * @param whitelist 从 Nacos 或 properties 中注入的合法 IP 字符串集合
     */
    public IpWhitelistFilter(@Value("${zhiyu.security.ip-whitelist:}") final List<String> whitelist) {
        this.whitelist = whitelist;
    }

    /**
     * 描述: 拦截过滤核心业务逻辑。检查管理端路径，实施白名单比对并按比对结果决定放行或直接拦截。
     * @param exchange 网关核心交互上下文
     * @param chain 过滤器责任链
     * @return 响应式的 Mono 异步流空结果
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        // 关键步骤 1：若白名单配置为空，则默认对所有流量开放放行（方便开发期本地调试）
        if (whitelist.isEmpty()) {
            return chain.filter(exchange);
        }
        
        // 关键步骤 2：获取客户端请求的真实 IP 地址
        String remoteAddr;
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            remoteAddr = remoteAddress.getAddress().getHostAddress();
        } else {
            remoteAddr = "unknown";
        }
        
        // 关键步骤 3：路径特征比对。非管理端路径（非 /api/v1/admin 开头）直接放行
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        
        // 关键步骤 4：白名单包含校验。包含当前客户端 IP 时放行
        if (whitelist.contains(remoteAddr)) {
            return chain.filter(exchange);
        }
        
        // 关键步骤 5：阻断逻辑。非法访问记录警告审计日志并直接终止响应 403 Forbidden
        if (log.isWarnEnabled()) {
            log.warn("Admin access denied for IP {} to {}", remoteAddr, path);
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    /**
     * 描述: 获取当前过滤器的优先级
     * @return 越小的值代表越高执行顺序
     */
    @Override
    public int getOrder() {
        return IP_WHITELIST_FILTER_ORDER;
    }
}

