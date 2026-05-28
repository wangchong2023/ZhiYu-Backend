package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowProvider;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsFlowProvider implements AuthFlowProvider {

    private final IAuthUserService authUserService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.SMS;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        String phone = context.get("phone");
        if (phone == null || phone.isBlank()) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        String smsCode = context.get("smsCode");
        if (smsCode == null || smsCode.isBlank()) {
            throw new BizException(BizErrorCode.SMS_CODE_INCORRECT);
        }
        String redisKey = CacheKeys.key(CacheKeys.SMS_CODE, "admin_login", phone);
        String storedCode = redisTemplate.opsForValue().get(redisKey);
        if (storedCode == null || !storedCode.equals(smsCode)) {
            throw new BizException(BizErrorCode.SMS_CODE_INCORRECT);
        }
        redisTemplate.delete(redisKey);

        AuthUser user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMobile, phone));
        boolean newUser = false;
        if (user == null) {
            user = new AuthUser();
            user.setAuthUserMobile(phone);
            user.setAuthUserMobileVerified(1);
            user.setAuthUserScope(OAuthField.SCOPE_OPENID);
            user.setAuthUserEnable(1);
            authUserService.insert(user);
            newUser = true;
            if (log.isInfoEnabled()) {
                log.info("Auto-registered user from SMS login: userId={}, phone={}",
                        user.getAuthUserId(), phone);
            }
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID;

        return AuthFlowResult.builder()
                .user(user)
                .scope(scope)
                .logType("SMS")
                .logAction("LOGIN")
                .newUser(newUser)
                .build();
    }
}
