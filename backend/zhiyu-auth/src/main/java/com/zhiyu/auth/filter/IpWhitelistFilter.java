package com.zhiyu.auth.filter;

import com.zhiyu.common.web.FilterResponseUtil;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

@Slf4j
@Component
public class IpWhitelistFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";
    private static final Set<String> WHITELIST_EXEMPT = Set.of(
            "/api/v1/admin/login"
    );

    private final Set<String> whitelist;
    private final boolean allowAll;

    public IpWhitelistFilter(
            @Value("${zhiyu.security.admin-ip-whitelist:127.0.0.1}") final String whitelistCsv) {
        this.whitelist = parseCsv(whitelistCsv);
        this.allowAll = whitelist.contains("*");
        log.info("Admin IP whitelist loaded: {} (allowAll={})", whitelist, allowAll);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith(ADMIN_PATH_PREFIX) || WHITELIST_EXEMPT.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (allowAll) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String remoteAddr = request.getRemoteAddr();

        if (isAllowed(clientIp) || isAllowed(remoteAddr)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Admin access denied for clientIp={}, remoteAddr={}, path={}", clientIp, remoteAddr, path);
        FilterResponseUtil.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                BizErrorCode.ACCESS_DENIED.getCode(), "IP not in whitelist");
    }

    private boolean isAllowed(final String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (whitelist.contains(ip)) {
            return true;
        }
        for (String entry : whitelist) {
            if (entry.contains("/") && matchesCidr(ip, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(final String ip, final String cidr) {
        try {
            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress netAddr = InetAddress.getByName(cidr.substring(0, cidr.indexOf('/')));
            int prefix = Integer.parseInt(cidr.substring(cidr.indexOf('/') + 1));
            byte[] ipBytes = ipAddr.getAddress();
            byte[] netBytes = netAddr.getAddress();
            if (ipBytes.length != netBytes.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != netBytes[i]) {
                    return false;
                }
            }
            if (remBits > 0) {
                int mask = (0xFF << (8 - remBits)) & 0xFF;
                if ((ipBytes[fullBytes] & mask) != (netBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException | IllegalArgumentException e) {
            log.debug("CIDR match failed for {} against {}", ip, cidr, e);
            return false;
        }
    }

    private static String resolveClientIp(final HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private static Set<String> parseCsv(final String csv) {
        return Set.of(csv.split("\\s*,\\s*"));
    }
}
