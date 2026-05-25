package com.zhiyu.user.controller;

import com.zhiyu.auth.service.IdentityService;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.service.AuthWebAuthnService;
import com.zhiyu.user.dto.WebAuthnCredentialDto;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "认证身份", description = "查看和解除绑定的认证方式")
@RestController
@RequestMapping("/api/v1/user")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class UserIdentityController {

    private final IdentityService identityService;
    private final AuthWebAuthnService authWebAuthnService;

    @Operation(summary = "获取已绑定的认证方式", description = "列出当前用户所有已绑定的第三方登录方式")
    @GetMapping("/identities")
    public ApiResponse<List<AuthUserIdentity>> listIdentities() {
        return ApiResponse.success(identityService.listIdentities(getCurrentUserId()));
    }

    @Operation(summary = "解绑认证方式", description = "解除绑定指定的第三方登录方式（不得解绑最后一种方式）")
    @DeleteMapping("/identities/{identityId}")
    public ApiResponse<Void> unbindIdentity(@PathVariable("identityId") final Long identityId) {
        identityService.unbindIdentity(getCurrentUserId(), identityId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "获取通行密钥列表", description = "返回当前用户已注册的 WebAuthn 通行密钥")
    @GetMapping("/webauthn/credentials")
    public ApiResponse<List<WebAuthnCredentialDto>> listWebAuthnCredentials() {
        Long userId = getCurrentUserId();
        List<AuthUserWebAuthn> entities = authWebAuthnService.selectList(
                new LambdaQueryWrapper<AuthUserWebAuthn>()
                        .eq(AuthUserWebAuthn::getAuthUserId, userId)
                        .eq(AuthUserWebAuthn::getEnabled, 1));
        return ApiResponse.success(entities.stream()
                .map(e -> WebAuthnCredentialDto.builder()
                        .credentialId(e.getCredentialId())
                        .deviceName(e.getDeviceName())
                        .createdAt(e.getCreatedTime())
                        .lastUsedTime(e.getLastUsedTime())
                        .build())
                .collect(Collectors.toList()));
    }

    private Long getCurrentUserId() {
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
