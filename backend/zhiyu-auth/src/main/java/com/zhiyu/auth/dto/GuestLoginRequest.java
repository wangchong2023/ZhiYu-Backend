package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 游客免注册登录请求数据传输对象（DTO）。
 *
 * <p>提供可选的设备标识以便进行同一个游客会话的持久绑定，
 * 并支持上报隐私政策同意状态。</p>
 *
 * @author Antigravity
 */
@Data
@Schema(description = "游客免注册登录请求对象")
public class GuestLoginRequest {

    /**
     * 客户端唯一设备标识。
     *
     * <p>若上报此属性，后端可通过设备指纹生成一致的匿名账户，
     * 避免因重复请求生成多条无主游客用户数据。</p>
     */
    @Schema(description = "客户端唯一设备标识（设备指纹），可选", example = "device_fingerprint_example_456")
    private String deviceId;

    /**
     * 隐私政策同意状态。
     *
     * <p>在某些合规地区，游客访问也需要同意用户服务及隐私协议，
     * 值为 true 表示用户已同意。</p>
     */
    @Schema(description = "用户是否同意隐私政策，可选", example = "true")
    private Boolean privacyConsent;
}
