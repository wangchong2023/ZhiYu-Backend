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

    private static final String PROVIDER = "GOOGLE";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final int ERR_THIRD_PARTY = 41501;
    private static final int ERR_CODE_INVALID = 41502;

    private final OAuthProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public OAuthUserInfo authorize(final OAuthRequest request) throws BizException {
        OAuthProperties.Google cfg = properties.getGoogle();

        HttpHeaders headers = new HttpHeaders();
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
            throw new BizException(ERR_THIRD_PARTY, "Google service is temporarily unavailable");
        }

        if (tokenResp == null || tokenResp.has("error")) {
            String errDesc = tokenResp != null && tokenResp.has("error_description")
                    ? tokenResp.get("error_description").asText() : "unknown";
            log.warn("Google token error: {}", errDesc);
            throw new BizException(ERR_CODE_INVALID, "Google authorization code is invalid");
        }

        String accessToken = tokenResp.get("access_token").asText();

        try {
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);
            JsonNode userResp = restTemplate.postForObject(USERINFO_URL, userEntity, JsonNode.class);

            if (userResp == null) {
                throw new BizException(ERR_THIRD_PARTY, "Failed to fetch Google user info");
            }
            String sub = userResp.get("sub").asText();
            String email = userResp.has("email") ? userResp.get("email").asText() : null;
            boolean emailVerified = userResp.has("email_verified")
                    && userResp.get("email_verified").asBoolean();
            String name = userResp.has("name") ? userResp.get("name").asText() : null;
            String picture = userResp.has("picture") ? userResp.get("picture").asText() : null;

            return new OAuthUserInfo(sub, null, name, picture, email, emailVerified);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google userinfo fetch failed", e);
            throw new BizException(ERR_THIRD_PARTY, "Failed to fetch Google user info");
        }
    }
}
