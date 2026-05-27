/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.gateway.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.HttpHandler;

/**
 * Netty 副 HTTP 端口自动配置类。
 *
 * <p>在 WebFlux 响应式 Web 环境下，若主端口配置为 TLS/HTTPS（例如 8443 端口），
 * 可以通过配置 {@code server.http.port}（例如 8080 端口）启用一个额外的、不支持 TLS 的 HTTP 端口。
 * 该端口主要用于 Kubernetes 健康检查（Liveness/Readiness）或者本地指标拉取，保证内部探活流量不需要经过繁重的 TLS 握手。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "server.http.port")
@ConditionalOnClass(HttpHandler.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class NettyHttpPortAutoConfiguration {

    @Value("${server.http.port}")
    private Integer httpPort;

    /**
     * 构建并注册 Netty 响应式副端口 Web 容器。
     *
     * @param httpHandler WebFlux 核心处理器
     * @return 封装了副 WebServer 生命周期的容器实例
     */
    @Bean
    public NettyReactiveWeb httpPortWebServerFactory(final HttpHandler httpHandler) {
        return new NettyReactiveWeb(httpHandler, httpPort);
    }

    /**
     * 内部 Netty 响应式服务器生命周期包装类。
     */
    public static class NettyReactiveWeb {

        private final WebServer webServer;

        /**
         * 构造并初始化副 Web 服务器实例。
         *
         * @param httpHandler WebFlux 处理器
         * @param httpPort 副服务器监听端口
         */
        public NettyReactiveWeb(final HttpHandler httpHandler, final Integer httpPort) {
            NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory(httpPort);
            this.webServer = factory.getWebServer(httpHandler);
        }

        /**
         * 在 Bean 初始化完成后，优雅启动副 Web 容器，监听 HTTP 请求。
         */
        @PostConstruct
        public void init() {
            this.webServer.start();
        }

        /**
         * 在容器销毁前，优雅停止副 Web 容器，释放网络监听及绑定的端口。
         */
        @PreDestroy
        public void stop() {
            this.webServer.stop();
        }
    }
}
