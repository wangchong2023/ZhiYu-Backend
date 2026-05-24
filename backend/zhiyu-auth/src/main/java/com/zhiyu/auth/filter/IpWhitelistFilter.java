package com.zhiyu.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
public class IpWhitelistFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

    private final Set<String> whitelist;

    public IpWhitelistFilter(
            @Value("${zhiyu.security.admin-ip-whitelist:127.0.0.1}") final String whitelistCsv) {
        this.whitelist = parseCsv(whitelistCsv);
        log.info("Admin IP whitelist loaded: {}", whitelist);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        if (whitelist.contains(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Admin access denied for IP={}, path={}", clientIp, path);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40301,\"message\":\"IP 不在白名单中\"}");
    }

    private static Set<String> parseCsv(final String csv) {
        return Set.of(csv.split("\\s*,\\s*"));
    }
}
