package com.zhiyu.auth.spi;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dypnsapi.model.v20170525.GetMobileRequest;
import com.aliyuncs.dypnsapi.model.v20170525.GetMobileResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.config.OAuthProperties;
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

/**
 * 运营商本机号码一键登录认证流程提供者。
 *
 * <p>本类接入阿里云号码认证服务 SDK。当 App 侧通过运营商 SDK 获取到 AccessToken 后，
 * 后端利用此 Token 向阿里云服务换取真实手机号，并据此手机号查找或自动注册用户账户。</p>
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarrierFlowProvider implements AuthFlowProvider {

    /** 阿里云接口调用成功状态码 */
    private static final String ALIYUN_SUCCESS_CODE = "OK";
    /** Mock 手机号前缀 */
    private static final String MOCK_PHONE_PREFIX = "1880000";
    /** 默认的 Region ID */
    private static final String DEFAULT_REGION_ID = "cn-hangzhou";
    /** 散列因子限制 */
    private static final int HASH_MODULO = 10000;

    private final IAuthUserService authUserService;
    private final OAuthProperties oAuthProperties;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.CARRIER;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        // 1. 验证隐私政策同意状态
        final Object privacyConsent = context.get("privacyConsent");
        if (!Boolean.TRUE.equals(privacyConsent)) {
            throw new BizException(BizErrorCode.PRIVACY_CONSENT_REQUIRED);
        }

        // 2. 提取并验证参数
        final String carrierToken = context.get("carrierToken");
        if (carrierToken == null || carrierToken.isBlank()) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        // 3. 换取手机号（支持真实阿里云 API 及优雅的 Mock 回退机制）
        final String phone = fetchMobileNumber(carrierToken);

        // 4. 根据手机号获取用户或自动注册
        AuthUser user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMobile, phone));
        boolean newUser = false;
        if (user == null) {
            user = new AuthUser();
            user.setAuthUserMobile(phone);
            user.setAuthUserMobileVerified(1);
            user.setAuthUserScope(OAuthField.SCOPE_OPENID);
            user.setAuthUserEnable(1);
            authUserService.insert(user);
            newUser = true;
            if (log.isInfoEnabled()) {
                log.info("Auto-registered user via Carrier login: userId={}, phone={}",
                        user.getAuthUserId(), phone);
            }
        }

        // 5. 校验用户状态
        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        final String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID;

        return AuthFlowResult.builder()
                .user(user)
                .scope(scope)
                .logType("CARRIER")
                .logAction("LOGIN")
                .newUser(newUser)
                .build();
    }

    /**
     * 核心业务：向运营商/阿里云号码认证服务发起请求以换取真实的手机号码。
     *
     * <p>若未配置阿里云密钥，或 Token 以 mock_ 开头，则自动触发优雅退避机制，返回 Mock 手机号。</p>
     *
     * @param carrierToken 客户端上报的 AccessToken
     * @return 手机号码
     */
    private String fetchMobileNumber(final String carrierToken) {
        final OAuthProperties.Carrier config = oAuthProperties.getCarrier();
        final boolean noConfig = config == null
                || config.getAccessKeyId() == null || config.getAccessKeyId().isBlank()
                || config.getAccessKeySecret() == null || config.getAccessKeySecret().isBlank();

        // 触发退避机制的条件：无云配置，或者 Token 表明为 mock 模式
        if (noConfig || carrierToken.startsWith("mock_") || "mock".equals(config.getAccessKeyId())) {
            log.warn("Aliyun carrier config is empty or mock token received. Falling back to mock phone resolution.");
            return generateMockPhone(carrierToken);
        }

        try {
            final String regionId = config.getRegionId() != null ? config.getRegionId() : DEFAULT_REGION_ID;
            final DefaultProfile profile = DefaultProfile.getProfile(
                    regionId, config.getAccessKeyId(), config.getAccessKeySecret());
            final IAcsClient client = new DefaultAcsClient(profile);

            final GetMobileRequest request = new GetMobileRequest();
            request.setAccessToken(carrierToken);

            final GetMobileResponse response = client.getAcsResponse(request);
            if (response != null && ALIYUN_SUCCESS_CODE.equals(response.getCode())
                    && response.getGetMobileResultDTO() != null) {
                final String mobile = response.getGetMobileResultDTO().getMobile();
                if (mobile != null && !mobile.isBlank()) {
                    return mobile;
                }
            }
            log.error("Failed to fetch mobile from Aliyun, response: {}", response);
            throw new BizException(BizErrorCode.CARRIER_TOKEN_INVALID);
        } catch (Exception e) {
            log.error("Aliyun Carrier GetMobile API call error", e);
            // 异常时的最后防线：非生产环境且异常时可做兜底，在此若非开发测试标识则抛出异常
            throw new BizException(BizErrorCode.CARRIER_TOKEN_INVALID);
        }
    }

    /**
     * 根据 carrierToken 的哈希值生成格式一致的 Mock 手机号，用作退避流程。
     */
    private String generateMockPhone(final String token) {
        final int hash = Math.abs(token.hashCode() % HASH_MODULO);
        return MOCK_PHONE_PREFIX + String.format("%04d", hash);
    }
}
