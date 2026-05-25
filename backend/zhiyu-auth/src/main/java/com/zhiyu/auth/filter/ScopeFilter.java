package com.zhiyu.auth.filter;

import com.zhiyu.common.web.FilterResponseUtil;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
public class ScopeFilter extends OncePerRequestFilter {

    private static final String LIMITED_SCOPE = "ROLE_limited";
    private static final Set<String> LIMITED_ALLOWED_PATHS = Set.of(
            "/api/v1/user/profile",
            "/api/v1/auth/devices"
    );

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            chain.doFilter(request, response);
            return;
        }

        boolean isLimited = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(LIMITED_SCOPE::equalsIgnoreCase);

        if (!isLimited) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean allowed = LIMITED_ALLOWED_PATHS.stream().anyMatch(path::startsWith);
        if (allowed) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("LIMITED user denied access to path={}", path);
        FilterResponseUtil.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                BizErrorCode.ACCESS_DENIED.getCode(),
                "Account restricted, only email-related functions are allowed");
    }
}
