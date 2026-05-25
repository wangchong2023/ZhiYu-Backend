package com.zhiyu.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.config.OAuthProperties;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component("wechatOAuthProvider")
@RequiredArgsConstructor
public class WechatOAuthProvider implements OAuthProvider {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token"
            + "?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
    private static final String USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo"
            + "?access_token=%s&openid=%s";

    private final OAuthProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return OAuthField.PROVIDER_WECHAT;
    }

    @Override
    public OAuthUserInfo authorize(final OAuthRequest request) throws BizException {
        OAuthProperties.Wechat cfg = properties.getWechat();
        String tokenUrl = String.format(TOKEN_URL, cfg.getAppId(), cfg.getAppSecret(), request.code());

        JsonNode tokenResp;
        try {
            String body = restTemplate.getForObject(tokenUrl, String.class);
            tokenResp = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("WeChat token exchange failed", e);
            throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR, e);
        }

        if (tokenResp.has(OAuthField.ERROR_CODE) && tokenResp.get(OAuthField.ERROR_CODE).asInt() != 0) {
            if (log.isWarnEnabled()) {
                String errMsg = tokenResp.has(OAuthField.ERROR_MSG) ? tokenResp.get(OAuthField.ERROR_MSG).asText() : "unknown";
                log.warn("WeChat token error: {} {}", tokenResp.get(OAuthField.ERROR_CODE), errMsg);
            }
            throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
        }

        String accessToken = tokenResp.get(OAuthField.ACCESS_TOKEN).asText();
        String openid = tokenResp.get(OAuthField.OPENID).asText();
        String unionid = tokenResp.has(OAuthField.UNIONID) ? tokenResp.get(OAuthField.UNIONID).asText() : null;

        try {
            String userUrl = String.format(USERINFO_URL, accessToken, openid);
            String userBody = restTemplate.getForObject(userUrl, String.class);
            JsonNode userResp = objectMapper.readTree(userBody);
            String nickname = userResp.has(OAuthField.NICKNAME) ? userResp.get(OAuthField.NICKNAME).asText() : null;
            String avatar = userResp.has(OAuthField.HEAD_IMG_URL) ? userResp.get(OAuthField.HEAD_IMG_URL).asText() : null;
            return new OAuthUserInfo(openid, unionid, nickname, avatar, null, false);
        } catch (Exception e) {
            log.error("WeChat userinfo fetch failed", e);
            return new OAuthUserInfo(openid, unionid, null, null, null, false);
        }
    }
}
