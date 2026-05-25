package com.zhiyu.auth.spi;

import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowProvider;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.totp.TotpService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TotpFlowProvider implements AuthFlowProvider {

    private final TotpService totpService;
    private final AuthUserMapper authUserMapper;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.TOTP;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        Long userId = context.get("userId");
        String code = context.get("totpCode");

        if (!totpService.verifyTotp(userId, code)) {
            throw new BizException(BizErrorCode.TOTP_INCORRECT);
        }

        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID;

        return AuthFlowResult.builder()
                .user(user)
                .scope(scope)
                .logType("PASSWORD")
                .logAction("LOGIN")
                .build();
    }
}
