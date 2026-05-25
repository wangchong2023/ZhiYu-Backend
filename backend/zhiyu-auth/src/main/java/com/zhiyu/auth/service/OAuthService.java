package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.oauth.OAuthProviderFactory;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserIdentityMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final int DEFAULT_ENABLE = 1;
    private static final int MAX_PREFIX_LENGTH = 20;
    private static final int RANDOM_SUFFIX_LENGTH = 8;

    private final AuthUserMapper authUserMapper;
    private final AuthUserIdentityMapper authUserIdentityMapper;
    private final OAuthProviderFactory providerFactory;
    private final AuthFlowManager authFlowManager;

    @Transactional(rollbackFor = Exception.class)
    public AuthFlowResult authenticate(final String providerName, final OAuthRequest request) {
        OAuthProvider provider = providerFactory.getProvider(providerName);
        OAuthUserInfo userInfo = provider.authorize(request);

        AuthUserIdentity identity = authUserIdentityMapper.selectOne(
                new LambdaQueryWrapper<AuthUserIdentity>()
                        .eq(AuthUserIdentity::getProvider, provider.getProviderName())
                        .eq(AuthUserIdentity::getOpenid, userInfo.openid()));

        if (identity != null) {
            AuthUser user = authUserMapper.selectById(identity.getAuthUserId());
            if (user == null) {
                throw new BizException(BizErrorCode.OAUTH_IDENTITY_CONFLICT);
            }
            updateIdentityInfo(identity, userInfo);
            String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_FULL;
            return AuthFlowResult.builder()
                    .user(user)
                    .scope(scope)
                    .logType(provider.getProviderName())
                    .logAction("LOGIN")
                    .build();
        }

        if (userInfo.email() != null) {
            AuthUser emailUser = authUserMapper.selectOne(
                    new LambdaQueryWrapper<AuthUser>()
                            .eq(AuthUser::getAuthUserMail, userInfo.email()));
            if (emailUser != null) {
                throw new BizException(BizErrorCode.OAUTH_EMAIL_CONFLICT);
            }
        }

        AuthUser newUser = createUser(userInfo);
        createIdentity(newUser.getAuthUserId(), userInfo, provider.getProviderName());
        String scope = newUser.getAuthUserScope() != null ? newUser.getAuthUserScope() : OAuthField.SCOPE_FULL;
        return AuthFlowResult.builder()
                .user(newUser)
                .scope(scope)
                .logType(provider.getProviderName())
                .logAction("REGISTER")
                .newUser(true)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(final String providerName, final OAuthRequest request) {
        AuthFlowResult result = authenticate(providerName, request);
        JwtPair pair = authFlowManager.finalizeLogin(result);
        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType(OAuthField.TOKEN_TYPE)
                .totpRequired(false)
                .isNewUser(result.isNewUser())
                .build();
    }

    private AuthUser createUser(final OAuthUserInfo userInfo) {
        String uniqueUsername = generateUniqueUsername(userInfo);
        AuthUser user = AuthUser.builder()
                .authUserUsername(uniqueUsername)
                .authUserNick(userInfo.nickname())
                .authUserCode(UUID.randomUUID().toString().replace("-", ""))
                .authUserScope(OAuthField.SCOPE_LIMITED)
                .authUserEnable(DEFAULT_ENABLE)
                .authUserMail(userInfo.email())
                .authUserMailVerified(userInfo.emailVerified() ? 1 : 0)
                .build();
        authUserMapper.insert(user);
        return user;
    }

    private String generateUniqueUsername(final OAuthUserInfo userInfo) {
        String prefix = userInfo.nickname() != null
                ? userInfo.nickname().replaceAll("[^a-zA-Z0-9_]", "_") : "user";
        if (prefix.length() > MAX_PREFIX_LENGTH) {
            prefix = prefix.substring(0, MAX_PREFIX_LENGTH);
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, RANDOM_SUFFIX_LENGTH);
        return prefix + "_" + suffix;
    }

    private void createIdentity(final Long userId, final OAuthUserInfo userInfo, final String provider) {
        AuthUserIdentity identity = AuthUserIdentity.builder()
                .authUserId(userId)
                .provider(provider)
                .openid(userInfo.openid())
                .unionid(userInfo.unionid())
                .nickname(userInfo.nickname())
                .avatarUrl(userInfo.avatarUrl())
                .enabled(DEFAULT_ENABLE)
                .createdTime(LocalDateTime.now())
                .build();
        authUserIdentityMapper.insert(identity);
    }

    private void updateIdentityInfo(final AuthUserIdentity identity, final OAuthUserInfo userInfo) {
        boolean changed = false;
        if (userInfo.nickname() != null && !userInfo.nickname().equals(identity.getNickname())) {
            identity.setNickname(userInfo.nickname());
            changed = true;
        }
        if (userInfo.avatarUrl() != null && !userInfo.avatarUrl().equals(identity.getAvatarUrl())) {
            identity.setAvatarUrl(userInfo.avatarUrl());
            changed = true;
        }
        if (changed) {
            authUserIdentityMapper.updateById(identity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void upgradeScopeAfterEmailBind(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (OAuthField.SCOPE_LIMITED.equals(user.getAuthUserScope())) {
            user.setAuthUserScope(OAuthField.SCOPE_FULL);
            user.setAuthUserMailVerified(DEFAULT_ENABLE);
            authUserMapper.updateById(user);
            log.info("Scope upgraded to FULL for userId={}", userId);
        }
    }
}
