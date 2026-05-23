package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.WebAuthnRequest;
import com.zhiyu.auth.dto.WebAuthnResponse;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.webauthn.WebAuthnService;
import com.zhiyu.ufp.auth.webauthn.WebAuthnStartResult;
import com.zhiyu.ufp.common.exception.BizException;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "通行密钥", description = "WebAuthn/Passkey 注册认证接口")
@RestController
@RequestMapping("/api/v1/auth/webauthn")
@RequiredArgsConstructor
public class WebAuthnController {

    private static final int ERR_USER_NOT_FOUND = 40401;

    private final WebAuthnService webAuthnService;
    private final JwtService jwtService;
    private final AuthUserMapper authUserMapper;

    @Operation(summary = "开始注册通行密钥", description = "返回创建选项 JSON，客户端调用 navigator.credentials.create()")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/register/begin")
    public ApiResponse<WebAuthnResponse> registerBegin() throws IOException {
        Long userId = getCurrentUserId();
        WebAuthnStartResult result = webAuthnService.startRegistration(userId);
        return ApiResponse.success(WebAuthnResponse.builder()
                .challengeId(result.challengeId())
                .optionsJson(result.optionsJson())
                .build());
    }

    @Operation(summary = "完成注册通行密钥", description = "验证 attestation 并存储公钥")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/register/finish")
    public ApiResponse<Void> registerFinish(@Valid @RequestBody final WebAuthnRequest request)
            throws IOException, RegistrationFailedException {
        webAuthnService.finishRegistration(request.getChallengeId(), request.getCredentialJson());
        return ApiResponse.success(null);
    }

    @Operation(summary = "开始通行密钥认证", description = "返回请求选项 JSON，客户端调用 navigator.credentials.get()")
    @PostMapping("/authenticate/begin")
    public ApiResponse<WebAuthnResponse> authenticateBegin(
            @Valid @RequestBody final WebAuthnRequest request) throws IOException {
        WebAuthnStartResult result = webAuthnService.startAuthentication(request.getUsername());
        return ApiResponse.success(WebAuthnResponse.builder()
                .challengeId(result.challengeId())
                .optionsJson(result.optionsJson())
                .build());
    }

    @Operation(summary = "完成通行密钥认证", description = "验证 assertion 签名并返回 JWT Token")
    @PostMapping("/authenticate/finish")
    public ApiResponse<LoginResponse> authenticateFinish(
            @Valid @RequestBody final WebAuthnRequest request) throws IOException, AssertionFailedException {
        var result = webAuthnService.finishAuthentication(
                request.getChallengeId(), request.getCredentialJson());

        String username = result.getUsername();
        AuthUser user = authUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthUser>()
                        .eq(AuthUser::getAuthUserUsername, username));
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "用户不存在");
        }

        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : "FULL";
        var pair = jwtService.issue(user.getAuthUserId(), user.getAuthUserUsername(), scope);

        return ApiResponse.success(LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build());
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        AuthUser user = authUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthUser>()
                        .eq(AuthUser::getAuthUserUsername, username));
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "用户不存在");
        }
        return user.getAuthUserId();
    }
}
