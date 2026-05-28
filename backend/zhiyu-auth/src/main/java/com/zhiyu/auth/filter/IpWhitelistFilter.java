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

    /**
     * 描述: 校验目标 IP 地址是否在给定的 CIDR 网段内。
     *      使用成熟的 ipaddress 库进行高精度判定，支持完整的 IPv4 及 IPv6 全场景网段检测，防御格式异常导致的过滤链崩溃。
     * @param ip 待校验的客户端 IP
     * @param cidr CIDR 格式的 IP 网段 (如 192.168.1.0/24)
     * @return 若匹配成功则返回 true，否则返回 false
     */
    private static boolean matchesCidr(final String ip, final String cidr) {
        try {
            inet.ipaddr.IPAddressString parent = new inet.ipaddr.IPAddressString(cidr);
            inet.ipaddr.IPAddressString child = new inet.ipaddr.IPAddressString(ip);
            
            // 校验地址有效性，并判断子网网段是否包含目标 IP 地址
            return parent.isValid() && child.isValid() && parent.getAddress().contains(child.getAddress());
        } catch (Exception e) {
            log.warn("CIDR match execution error for ip={}, cidr={}", ip, cidr, e);
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
