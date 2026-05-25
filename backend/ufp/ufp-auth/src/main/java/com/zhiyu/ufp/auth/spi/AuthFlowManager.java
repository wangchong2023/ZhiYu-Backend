package com.zhiyu.ufp.auth.spi;

import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.service.AuthUserLogService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthFlowManager {

    private final List<AuthFlowProvider> providers;
    private final JwtService jwtService;
    private final AuthUserLogService authUserLogService;

    public AuthFlowResult authenticate(final AuthFlowContext context) {
        AuthFlowProvider provider = providers.stream()
                .filter(p -> p.supportedGrantType() == context.getGrantType())
                .findFirst()
                .orElseThrow(() -> new BizException(BizErrorCode.VALIDATION_FAILED));
        return provider.authenticate(context);
    }

    public JwtPair finalizeLogin(final AuthFlowResult result) {
        if (result.isTotpPending()) {
            String pendingToken = jwtService.issuePendingToken(
                    result.getUser().getAuthUserId(),
                    result.getUser().getAuthUserUsername());
            writeLog(result, "PENDING");
            return new JwtPair(pendingToken, null, 300L);
        }

        JwtPair pair = jwtService.issue(
                result.getUser().getAuthUserId(),
                result.getUser().getAuthUserUsername(),
                result.getScope());
        writeLog(result, "SUCCESS");
        return pair;
    }

    private void writeLog(final AuthFlowResult result, final String logResult) {
        AuthUserLog logEntry = new AuthUserLog();
        logEntry.setAuthUserLogUserId(result.getUser().getAuthUserId());
        logEntry.setAuthUserLogUserDisplay(result.getUser().getAuthUserUsername());
        logEntry.setAuthUserLogAction(result.getLogAction());
        logEntry.setAuthUserLogType(result.getLogType());
        logEntry.setAuthUserLogResult(logResult);
        logEntry.setCreatedTime(LocalDateTime.now());
        authUserLogService.insert(logEntry);
    }
}
