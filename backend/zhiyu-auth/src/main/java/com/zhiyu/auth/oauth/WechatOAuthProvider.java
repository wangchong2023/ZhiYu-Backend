package com.zhiyu.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.config.OAuthProperties;
import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component("wechatOAuthProvider")
@RequiredArgsConstructor
public class WechatOAuthProvider implements OAuthProvider {

    private static final String PROVIDER = "WECHAT";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token"
            + "?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
    private static final String USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo"
            + "?access_token=%s&openid=%s";
    private static final int ERR_THIRD_PARTY = 40113;
    private static final int ERR_CODE_INVALID = 40112;

    private final OAuthProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return PROVIDER;
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
            throw new BizException(ERR_THIRD_PARTY, "微信服务暂不可用");
        }

        if (tokenResp.has("errcode") && tokenResp.get("errcode").asInt() != 0) {
            String errMsg = tokenResp.has("errmsg") ? tokenResp.get("errmsg").asText() : "unknown";
            log.warn("WeChat token error: {} {}", tokenResp.get("errcode"), errMsg);
            throw new BizException(ERR_CODE_INVALID, "微信授权码无效");
        }

        String accessToken = tokenResp.get("access_token").asText();
        String openid = tokenResp.get("openid").asText();
        String unionid = tokenResp.has("unionid") ? tokenResp.get("unionid").asText() : null;

        try {
            String userUrl = String.format(USERINFO_URL, accessToken, openid);
            String userBody = restTemplate.getForObject(userUrl, String.class);
            JsonNode userResp = objectMapper.readTree(userBody);
            String nickname = userResp.has("nickname") ? userResp.get("nickname").asText() : null;
            String avatar = userResp.has("headimgurl") ? userResp.get("headimgurl").asText() : null;
            return new OAuthUserInfo(openid, unionid, nickname, avatar, null, false);
        } catch (Exception e) {
            log.error("WeChat userinfo fetch failed", e);
            return new OAuthUserInfo(openid, unionid, null, null, null, false);
        }
    }
}
