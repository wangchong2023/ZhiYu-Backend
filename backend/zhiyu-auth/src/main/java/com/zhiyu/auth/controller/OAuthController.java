package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.OAuthLoginRequest;
import com.zhiyu.auth.service.OAuthService;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "第三方登录", description = "微信/Apple/Google OAuth 登录接口")
@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    @Operation(summary = "微信登录", description = "使用微信授权码登录，首次登录自动创建账户")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40112", description = "授权码无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40113", description = "第三方平台返回错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40903", description = "邮箱已注册，请绑定"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40904", description = "该三方账号已绑定其他用户")
    })
    @PostMapping("/wechat")
    public ApiResponse<LoginResponse> wechatLogin(
            @Valid @RequestBody final OAuthLoginRequest request) {
        return ApiResponse.success(oauthService.login("wechat",
                new OAuthRequest(request.getCode(), request.getState(), request.getIdToken())));
    }

    @Operation(summary = "Apple 登录", description = "使用 Apple ID Token 登录，首次登录自动创建账户")
    @PostMapping("/apple")
    public ApiResponse<LoginResponse> appleLogin(
            @Valid @RequestBody final OAuthLoginRequest request) {
        return ApiResponse.success(oauthService.login("apple",
                new OAuthRequest(request.getCode(), request.getState(), request.getIdToken())));
    }

    @Operation(summary = "Google 登录", description = "使用 Google 授权码登录，首次登录自动创建账户")
    @PostMapping("/google")
    public ApiResponse<LoginResponse> googleLogin(
            @Valid @RequestBody final OAuthLoginRequest request) {
        return ApiResponse.success(oauthService.login("google",
                new OAuthRequest(request.getCode(), request.getState(), request.getIdToken())));
    }
}
