package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.oauth.OAuthProviderFactory;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserIdentityMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private AuthUserIdentityMapper authUserIdentityMapper;

    @Mock
    private AuthUserLogMapper authUserLogMapper;

    @Mock
    private JwtService jwtService;

    @Mock
    private OAuthProviderFactory providerFactory;

    @Mock
    private OAuthProvider oAuthProvider;

    @InjectMocks
    private OAuthService oAuthService;

    // ── Existing Identity Login ───────────────────────────────

    @Test
    void shouldLoginWithExistingIdentity() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-123", "union-456",
                "Test User", "https://avatar.url", "test@example.com", true);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        identity.setProvider("github");
        identity.setOpenid("openid-123");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("github_user")
                .authUserScope("openid").build();

        JwtPair pair = new JwtPair("access-token", "refresh-token", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(identity);
        when(authUserMapper.selectById(1001L)).thenReturn(user);
        when(jwtService.issue(1001L, "github_user", "openid")).thenReturn(pair);

        LoginResponse resp = oAuthService.login("github", request);

        assertThat(resp.getAccessToken()).isEqualTo("access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(resp.getExpiresIn()).isEqualTo(900);
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        assertThat(resp.getTotpRequired()).isFalse();
        assertThat(resp.getIsNewUser()).isFalse();
        verify(authUserIdentityMapper).updateById(any(AuthUserIdentity.class));
        verify(authUserLogMapper).insert(any(AuthUserLog.class));
    }

    // ── Identity exists but user not found ────────────────────

    @Test
    void shouldThrowWhenIdentityUserNotFound() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-123", null,
                "Test User", null, null, false);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        identity.setProvider("github");

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(identity);
        when(authUserMapper.selectById(1001L)).thenReturn(null);

        assertThatThrownBy(() -> oAuthService.login("github", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Account data anomaly");
    }

    // ── New User Registration via OAuth ───────────────────────

    @Test
    void shouldRegisterNewUserViaOAuth() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-new", null,
                "NewOAuthUser", null, "new@example.com", true);

        JwtPair pair = new JwtPair("access-new", "refresh-new", 900);

        when(providerFactory.getProvider("google")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("google");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(jwtService.issue(anyLong(), anyString(), eq("LIMITED")))
                .thenReturn(pair);

        // Capture the inserted user
        when(authUserMapper.insert(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser u = invocation.getArgument(0);
            u.setAuthUserId(2002L);
            return 1;
        });

        LoginResponse resp = oAuthService.login("google", request);

        assertThat(resp.getAccessToken()).isEqualTo("access-new");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-new");
        assertThat(resp.getIsNewUser()).isTrue();
        verify(authUserMapper).insert(any(AuthUser.class));
        verify(authUserIdentityMapper).insert(any(AuthUserIdentity.class));
        verify(authUserLogMapper).insert(any(AuthUserLog.class));
    }

    // ── Email Conflict ────────────────────────────────────────

    @Test
    void shouldThrowWhenEmailAlreadyRegistered() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-new", null,
                "New User", null, "existing@example.com", false);

        AuthUser existingUser = AuthUser.builder()
                .authUserId(3001L).authUserUsername("existing")
                .authUserMail("existing@example.com").build();

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingUser);

        assertThatThrownBy(() -> oAuthService.login("github", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("This email is already registered");
    }

    // ── OAuth with null email (no email conflict check) ───────

    @Test
    void shouldSkipEmailCheckWhenEmailIsNull() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-no-email", null,
                "NoEmailUser", null, null, false);

        JwtPair pair = new JwtPair("access-token", "refresh-token", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(jwtService.issue(anyLong(), anyString(), eq("LIMITED")))
                .thenReturn(pair);
        when(authUserMapper.insert(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser u = invocation.getArgument(0);
            u.setAuthUserId(4001L);
            return 1;
        });

        // Email is null → no email lookup should happen
        LoginResponse resp = oAuthService.login("github", request);

        assertThat(resp.getIsNewUser()).isTrue();
        verify(authUserMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    // ── Nickname-based username generation ────────────────────

    @Test
    void shouldGenerateUsernameFromNickname() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-gen", null,
                "Cool.User!123", null, null, false);

        JwtPair pair = new JwtPair("access", "refresh", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(jwtService.issue(anyLong(), anyString(), eq("LIMITED")))
                .thenReturn(pair);
        when(authUserMapper.insert(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser u = invocation.getArgument(0);
            u.setAuthUserId(5001L);
            // Username should have special chars replaced with underscores
            assertThat(u.getAuthUserUsername()).startsWith("Cool_User_123_");
            return 1;
        });

        oAuthService.login("github", request);
    }

    @Test
    void shouldGenerateDefaultUsernameWhenNicknameNull() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-null-nick", null,
                null, null, null, false);

        JwtPair pair = new JwtPair("access", "refresh", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(jwtService.issue(anyLong(), anyString(), eq("LIMITED")))
                .thenReturn(pair);
        when(authUserMapper.insert(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser u = invocation.getArgument(0);
            u.setAuthUserId(6001L);
            assertThat(u.getAuthUserUsername()).startsWith("user_");
            return 1;
        });

        oAuthService.login("github", request);
    }

    // ── Identity info update on re-login ──────────────────────

    @Test
    void shouldUpdateIdentityWhenNicknameChanged() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-123", null,
                "Updated Name", "https://new-avatar.url", "test@example.com", true);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        identity.setProvider("github");
        identity.setOpenid("openid-123");
        identity.setNickname("Old Name");
        identity.setAvatarUrl("https://old-avatar.url");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("github_user")
                .authUserScope("openid").build();

        JwtPair pair = new JwtPair("access", "refresh", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(identity);
        when(authUserMapper.selectById(1001L)).thenReturn(user);
        when(jwtService.issue(1001L, "github_user", "openid")).thenReturn(pair);

        oAuthService.login("github", request);

        verify(authUserIdentityMapper).updateById(identity);
        assertThat(identity.getNickname()).isEqualTo("Updated Name");
        assertThat(identity.getAvatarUrl()).isEqualTo("https://new-avatar.url");
    }

    @Test
    void shouldNotUpdateIdentityWhenUnchanged() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-123", null,
                "SameName", "https://same.url", "test@example.com", true);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        identity.setProvider("github");
        identity.setOpenid("openid-123");
        identity.setNickname("SameName");
        identity.setAvatarUrl("https://same.url");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("github_user")
                .authUserScope("openid").build();

        JwtPair pair = new JwtPair("access", "refresh", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(identity);
        when(authUserMapper.selectById(1001L)).thenReturn(user);
        when(jwtService.issue(1001L, "github_user", "openid")).thenReturn(pair);

        oAuthService.login("github", request);

        // No update since nickname and avatar are unchanged
        verify(authUserIdentityMapper, never()).updateById(any(AuthUserIdentity.class));
    }

    // ── Token issue with scope fallback ───────────────────────

    @Test
    void shouldIssueTokenWithFullScopeWhenNull() {
        OAuthRequest request = new OAuthRequest("auth-code", "state", null);
        OAuthUserInfo userInfo = new OAuthUserInfo("openid-123", null,
                "Test User", null, "test@example.com", true);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        identity.setProvider("github");
        identity.setOpenid("openid-123");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("github_user")
                .authUserScope(null).build();

        JwtPair pair = new JwtPair("access", "refresh", 900);

        when(providerFactory.getProvider("github")).thenReturn(oAuthProvider);
        when(oAuthProvider.getProviderName()).thenReturn("github");
        when(oAuthProvider.authorize(request)).thenReturn(userInfo);
        when(authUserIdentityMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(identity);
        when(authUserMapper.selectById(1001L)).thenReturn(user);
        when(jwtService.issue(1001L, "github_user", "FULL")).thenReturn(pair);

        LoginResponse resp = oAuthService.login("github", request);

        assertThat(resp.getAccessToken()).isEqualTo("access");
    }
}
