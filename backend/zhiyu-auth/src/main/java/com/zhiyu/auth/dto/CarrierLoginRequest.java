package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运营商一键登录请求数据传输对象（DTO）。
 *
 * <p>用于接收 iOS/Android App 调用运营商 SDK 获取的 accessToken 等凭证，
 * 并将其传递给后端进行手机号换取并完成登录流程。</p>
 *
 * @author Antigravity
 */
@Data
@Schema(description = "运营商一键登录请求对象")
public class CarrierLoginRequest {

    /**
     * 运营商 SDK 返回的认证 AccessToken。
     */
    @NotBlank(message = "Carrier token cannot be blank")
    @Schema(description = "运营商 SDK 返回的 accessToken",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "carrier_token_example_123")
    private String carrierToken;

    /**
     * 移动应用对应的 AppKey。
     *
     * <p>由于 iOS 和 Android 服务端可能会使用不同的号码认证 Key 配置，
     * 客户端需上报 AppKey 以供后端匹配正确的解密凭证。</p>
     */
    @NotBlank(message = "App key cannot be blank")
    @Schema(description = "移动应用 AppKey", requiredMode = Schema.RequiredMode.REQUIRED, example = "app_key_ios_098")
    private String appKey;

    /**
     * 隐私政策同意状态。
     *
     * <p>按国家个保法要求，用户必须手动勾选同意隐私政策后方可执行登录，
     * 值为 true 表示用户已同意。</p>
     */
    @Schema(description = "用户是否同意隐私政策", example = "true")
    private Boolean privacyConsent;
}
