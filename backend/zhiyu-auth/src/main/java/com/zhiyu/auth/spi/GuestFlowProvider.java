package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowProvider;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 游客免注册匿名登录认证流程提供者。
 *
 * <p>本类支持用户无感直接进入系统浏览。当客户端请求游客登录时，
 * 系统会根据客户端传入的设备 ID（deviceId）生成或查找对应的 guest 账号，
 * 并签发带有 GUEST 范围限制（仅拥有受限的只读权限）的专属 JWT 令牌。</p>
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestFlowProvider implements AuthFlowProvider {

    /** 游客账号前缀 */
    private static final String GUEST_PREFIX = "guest_";
    /** 游客昵称 */
    private static final String GUEST_DEFAULT_NICK = "Guest User";
    /** 随机游客账号 UUID 的截取长度 */
    private static final int UUID_SUBSTRING_LENGTH = 16;

    private final IAuthUserService authUserService;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.GUEST;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        // 1. 验证隐私政策同意状态
        final Object privacyConsent = context.get("privacyConsent");
        if (!Boolean.TRUE.equals(privacyConsent)) {
            throw new BizException(BizErrorCode.PRIVACY_CONSENT_REQUIRED);
        }

        // 2. 根据设备 ID 确定或生成游客的用户名
        final String deviceId = context.get("deviceId");
        final String username;
        if (deviceId != null && !deviceId.isBlank()) {
            final String deviceHash = DigestUtils.md5DigestAsHex(
                    deviceId.getBytes(StandardCharsets.UTF_8));
            username = GUEST_PREFIX + deviceHash;
        } else {
            // 没有设备 ID 时直接使用 UUID 生成全新匿名用户
            username = GUEST_PREFIX + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, UUID_SUBSTRING_LENGTH);
        }

        // 3. 查找或创建游客用户记录
        AuthUser user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, username));
        boolean newUser = false;

        if (user == null) {
            user = new AuthUser();
            user.setAuthUserUsername(username);
            user.setAuthUserNick(GUEST_DEFAULT_NICK);
            user.setAuthUserScope(OAuthField.SCOPE_GUEST);
            user.setAuthUserEnable(1);
            authUserService.insert(user);
            newUser = true;
            if (log.isInfoEnabled()) {
                log.info("Auto-created new guest user: userId={}, username={}",
                        user.getAuthUserId(), username);
            }
        }

        // 4. 校验用户可用状态
        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        // 游客必须强制为 GUEST scope，以进行接口级细粒度权限限制
        final String scope = OAuthField.SCOPE_GUEST;

        return AuthFlowResult.builder()
                .user(user)
                .scope(scope)
                .logType("GUEST")
                .logAction("LOGIN")
                .newUser(newUser)
                .build();
    }
}
