# Spec A: OAuth Provider 实现计划

> **目标**：实现 OAuth Provider 抽象层 + 微信/Apple/Google 三个 Provider + 统一第三方登录 API

## Task 1: AuthUserIdentity 实体 + Mapper
**Files**: Create `ufp-auth/.../entity/AuthUserIdentity.java`, `ufp-auth/.../mapper/AuthUserIdentityMapper.java`

## Task 2: OAuth Provider 接口 + DTO
**Files**: Create `ufp-auth/.../oauth/OAuthProvider.java`, `OAuthUserInfo.java`, `OAuthRequest.java`

## Task 3: OAuth 配置属性
**Files**: Create `zhiyu-auth/.../config/OAuthProperties.java`

## Task 4: WechatOAuthProvider
**Files**: Create `zhiyu-auth/.../oauth/WechatOAuthProvider.java`

## Task 5: AppleOAuthProvider
**Files**: Create `zhiyu-auth/.../oauth/AppleOAuthProvider.java`

## Task 6: GoogleOAuthProvider
**Files**: Create `zhiyu-auth/.../oauth/GoogleOAuthProvider.java`

## Task 7: OAuthProviderFactory
**Files**: Create `zhiyu-auth/.../oauth/OAuthProviderFactory.java`

## Task 8: OAuthService（账户匹配 + JWT 签发）
**Files**: Create `zhiyu-auth/.../service/OAuthService.java`

## Task 9: OAuthController + DTO
**Files**: Create `zhiyu-auth/.../controller/OAuthController.java`, `dto/OAuthLoginRequest.java`, `dto/OAuthLoginResponse.java`

## Task 10: 单元测试 + 集成测试
**Files**: Test classes for each Provider, OAuthService, OAuthController

## Task 11: Checkstyle + SpotBugs + 验证
Run full checkstyle, spotbugs, tests
