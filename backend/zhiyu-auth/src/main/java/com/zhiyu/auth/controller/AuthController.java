package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.dto.SendRegisterCodeRequest;
import com.zhiyu.auth.dto.SendRegisterCodeResponse;
import com.zhiyu.auth.dto.SendSmsRequest;
import com.zhiyu.auth.service.AuthService;
import com.zhiyu.common.web.ApiResponse;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "注册登录相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int BEARER_PREFIX_LENGTH = 7;

    private final AuthService authService;

    @Operation(summary = "用户注册", description = "使用用户名+密码+邮箱创建新账号")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "注册成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40901", description = "用户名已被占用"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40902", description = "邮箱已被注册"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40109", description = "验证码错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "42903", description = "注册频率超限")
    })
    @PostMapping("/register")
    @SentinelResource("auth-register")
    @com.zhiyu.ufp.common.annotation.Trim
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody final RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @Operation(summary = "密码登录", description = "使用用户名+密码登录，返回 JWT Token 对")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40105", description = "用户名或密码错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40106", description = "账号已被临时锁定"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40107", description = "账号已被禁用"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40111", description = "需要验证码")
    })
    @PostMapping("/login")
    @SentinelResource("auth-login")
    @com.zhiyu.ufp.common.annotation.Trim
    public ApiResponse<LoginResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 运营商一键登录端点。
     *
     * @param request 包含运营商 Token 与 AppKey 的请求体
     * @return 统一登录响应体
     */
    @Operation(summary = "运营商一键登录", description = "使用运营商 SDK 返回的认证 token 进行免密登录，首次自动注册")
    @PostMapping("/carrier")
    @SentinelResource("auth-carrier-login")
    @com.zhiyu.ufp.common.annotation.Trim
    public ApiResponse<LoginResponse> carrierLogin(
            @Valid @RequestBody final com.zhiyu.auth.dto.CarrierLoginRequest request) {
        return ApiResponse.success(authService.carrierLogin(request));
    }

    @Operation(summary = "游客跳过登录", description = "匿名模式直接访问，签发受限 GUEST 权限的短效凭证")
    @PostMapping("/guest")
    @SentinelResource("auth-guest-login")
    public ApiResponse<LoginResponse> guestLogin(
            @RequestBody(required = false) final com.zhiyu.auth.dto.GuestLoginRequest request) {
        return ApiResponse.success(authService.guestLogin(request));
    }

    @Operation(summary = "刷新Token", description = "使用 RefreshToken 换取新的 Token 对（旧 RefreshToken 即刻作废）")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "刷新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "40103", description = "Token 已被使用（重放攻击）")
    })
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody final RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "发送短信验证码", description = "向指定手机号发送短信验证码用于登录或验证")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "发送成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "42903", description = "发送频率超限")
    })
    @PostMapping("/sms/send")
    @SentinelResource("auth-sms-send")
    public ApiResponse<Void> sendSms(@Valid @RequestBody final SendSmsRequest request) {
        authService.sendSms(request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "发送注册邮箱验证码", description = "向指定邮箱发送6位数字验证码用于注册")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "发送成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "42903", description = "发送频率超限")
    })
    @PostMapping("/send-register-code")
    @SentinelResource("auth-send-register-code")
    public ApiResponse<SendRegisterCodeResponse> sendRegisterCode(
            @Valid @RequestBody final SendRegisterCodeRequest request) {
        return ApiResponse.success(authService.sendRegisterCode(request));
    }

    @Operation(summary = "退出登录", description = "作废当前 AccessToken 和 RefreshToken")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) final String authHeader,
            @RequestBody(required = false) final RefreshRequest request) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(BEARER_PREFIX_LENGTH);
        }
        String refreshToken = request != null ? request.getRefreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ApiResponse.success(null);
    }
}
