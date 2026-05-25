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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component("googleOAuthProvider")
@RequiredArgsConstructor
public class GoogleOAuthProvider implements OAuthProvider {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final OAuthProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return OAuthField.PROVIDER_GOOGLE;
    }

    @Override
    public OAuthUserInfo authorize(final OAuthRequest request) throws BizException {
        OAuthProperties.Google cfg = properties.getGoogle();

        HttpHeaders headers = new HttpHeaders(); // NOPMD LooseCoupling
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", request.code());
        body.add("client_id", cfg.getClientId());
        body.add("client_secret", cfg.getClientSecret());
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", "postmessage");
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        JsonNode tokenResp;
        try {
            tokenResp = restTemplate.postForObject(TOKEN_URL, entity, JsonNode.class);
        } catch (Exception e) {
            log.error("Google token exchange failed", e);
            throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR, e);
        }

        if (tokenResp == null || tokenResp.has(OAuthField.ERROR)) {
            String errDesc = tokenResp != null && tokenResp.has(OAuthField.ERROR_DESCRIPTION)
                    ? tokenResp.get(OAuthField.ERROR_DESCRIPTION).asText() : "unknown";
            log.warn("Google token error: {}", errDesc);
            throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
        }

        String accessToken = tokenResp.get(OAuthField.ACCESS_TOKEN).asText();

        try {
            HttpHeaders userHeaders = new HttpHeaders(); // NOPMD LooseCoupling
            userHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);
            JsonNode userResp = restTemplate.postForObject(USERINFO_URL, userEntity, JsonNode.class);

            if (userResp == null) {
                throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR);
            }
            String sub = userResp.get(OAuthField.SUB).asText();
            String email = userResp.has(OAuthField.EMAIL) ? userResp.get(OAuthField.EMAIL).asText() : null;
            boolean emailVerified = userResp.has(OAuthField.EMAIL_VERIFIED)
                    && userResp.get(OAuthField.EMAIL_VERIFIED).asBoolean();
            String name = userResp.has(OAuthField.NAME) ? userResp.get(OAuthField.NAME).asText() : null;
            String picture = userResp.has(OAuthField.PICTURE) ? userResp.get(OAuthField.PICTURE).asText() : null;

            return new OAuthUserInfo(sub, null, name, picture, email, emailVerified);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google userinfo fetch failed", e);
            throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR, e);
        }
    }
}
