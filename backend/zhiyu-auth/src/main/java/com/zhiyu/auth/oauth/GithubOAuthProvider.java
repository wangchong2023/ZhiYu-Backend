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

/**
 * GitHub OAuth 2.0 Provider 实现。
 *
 * <p>流程说明：</p>
 * <ol>
 *   <li>客户端使用 GitHub OAuth App 获取 authorization code</li>
 *   <li>将 code 发送到本接口，换取 access_token</li>
 *   <li>使用 access_token 调用 GitHub API 获取用户信息（id/login/name/avatar_url/email）</li>
 *   <li>以 GitHub 用户 id（字符串形式）作为 openId，完成账号绑定或自动注册</li>
 * </ol>
 *
 * <p>注意：GitHub 用户可能未公开邮箱，此时 email 字段为 null，系统使用受限 scope 注册。</p>
 *
 * @see OAuthProperties.Github
 * @see OAuthProviderFactory
 */
@Slf4j
@Component("githubOAuthProvider")
@RequiredArgsConstructor
public class GithubOAuthProvider implements OAuthProvider {

    /** GitHub OAuth token 换取端点 */
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    /** GitHub REST API 用户信息端点 */
    private static final String USERINFO_URL = "https://api.github.com/user";
    /** GitHub REST API 用户邮箱端点（公开邮箱不一定存在于 /user，需单独调用） */
    private static final String USER_EMAILS_URL = "https://api.github.com/user/emails";

    private final OAuthProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 返回平台唯一标识。
     *
     * @return "GITHUB"
     */
    @Override
    public String getProviderName() {
        return OAuthField.PROVIDER_GITHUB;
    }

    /**
     * 通过 GitHub authorization code 换取用户信息。
     *
     * @param request 包含 code 的 OAuth 请求对象
     * @return 标准化的 GitHub 用户信息
     * @throws BizException 授权码无效或平台接口异常时抛出
     */
    @Override
    public OAuthUserInfo authorize(final OAuthRequest request) throws BizException {
        if (request.code() == null || request.code().isBlank()) {
            throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
        }
        OAuthProperties.Github cfg = properties.getGithub();

        // 步骤一：code 换取 access_token
        String accessToken = exchangeAccessToken(request.code(), cfg);

        // 步骤二：调用 /user 接口获取基本用户信息
        JsonNode userNode = fetchUserInfo(accessToken);

        String openId = String.valueOf(userNode.get("id").asLong());
        String login = userNode.has("login") ? userNode.get("login").asText() : null;
        String name = userNode.has(OAuthField.NAME) && !userNode.get(OAuthField.NAME).isNull()
                ? userNode.get(OAuthField.NAME).asText() : login;
        String avatarUrl = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;
        // GitHub 用户资料中的 email 字段：已公开才存在
        String email = userNode.has(OAuthField.EMAIL) && !userNode.get(OAuthField.EMAIL).isNull()
                ? userNode.get(OAuthField.EMAIL).asText() : null;

        // 步骤三：若 /user 无邮箱，则尝试查询 /user/emails（取首选且已验证的邮箱）
        if (email == null) {
            email = fetchPrimaryEmail(accessToken);
        }

        return new OAuthUserInfo(openId, null, name, avatarUrl, email, email != null);
    }

    /**
     * 用 authorization code 向 GitHub 换取 access_token。
     *
     * @param code GitHub 授权码
     * @param cfg  OAuthProperties 中的 GitHub 配置
     * @return access_token 字符串
     */
    private String exchangeAccessToken(final String code, final OAuthProperties.Github cfg) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // GitHub token 接口默认返回 application/x-www-form-urlencoded，需声明 Accept: application/json
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", cfg.getClientId());
        body.add("client_secret", cfg.getClientSecret());
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        JsonNode tokenResp;
        try {
            tokenResp = restTemplate.postForObject(TOKEN_URL, entity, JsonNode.class);
        } catch (Exception e) {
            log.error("GitHub token exchange failed", e);
            throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR, e);
        }

        if (tokenResp == null || tokenResp.has(OAuthField.ERROR)) {
            String errDesc = tokenResp != null && tokenResp.has(OAuthField.ERROR_DESCRIPTION)
                    ? tokenResp.get(OAuthField.ERROR_DESCRIPTION).asText() : "unknown";
            log.warn("GitHub token error: {}", errDesc);
            throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
        }

        return tokenResp.get(OAuthField.ACCESS_TOKEN).asText();
    }

    /**
     * 调用 GitHub /user 接口获取用户基础信息。
     *
     * @param accessToken GitHub access_token
     * @return 用户信息 JsonNode
     */
    private JsonNode fetchUserInfo(final String accessToken) {
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        // GitHub API 要求声明版本
        userHeaders.set("Accept", "application/vnd.github+json");
        userHeaders.set("X-GitHub-Api-Version", "2022-11-28");
        HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);

        JsonNode userResp;
        try {
            userResp = restTemplate.postForObject(USERINFO_URL, userEntity, JsonNode.class);
        } catch (Exception e) {
            log.error("GitHub userinfo fetch failed", e);
            throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR, e);
        }
        if (userResp == null) {
            throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR);
        }
        return userResp;
    }

    /**
     * 调用 GitHub /user/emails 接口，获取用户主邮箱（首选且已验证）。
     *
     * <p>当 /user 接口返回的邮箱为 null（未公开）时，通过此接口尝试获取验证邮箱。</p>
     *
     * @param accessToken GitHub access_token
     * @return 主邮箱地址，不存在则返回 null
     */
    private String fetchPrimaryEmail(final String accessToken) {
        try {
            HttpHeaders emailHeaders = new HttpHeaders();
            emailHeaders.setBearerAuth(accessToken);
            emailHeaders.set("Accept", "application/vnd.github+json");
            emailHeaders.set("X-GitHub-Api-Version", "2022-11-28");
            HttpEntity<Void> emailEntity = new HttpEntity<>(emailHeaders);

            String emailBody = restTemplate.getForObject(USER_EMAILS_URL, String.class,
                    new org.springframework.http.RequestEntity<>(
                            emailHeaders, org.springframework.http.HttpMethod.GET,
                            java.net.URI.create(USER_EMAILS_URL)));
            if (emailBody == null) {
                return null;
            }
            JsonNode emailArray = objectMapper.readTree(emailBody);
            // 遍历邮箱列表，优先取 primary=true 且 verified=true 的邮箱
            for (JsonNode entry : emailArray) {
                boolean primary = entry.has("primary") && entry.get("primary").asBoolean();
                boolean verified = entry.has("verified") && entry.get("verified").asBoolean();
                if (primary && verified && entry.has(OAuthField.EMAIL)) {
                    return entry.get(OAuthField.EMAIL).asText();
                }
            }
        } catch (Exception e) {
            // 邮箱查询失败不影响登录主流程，降级返回 null
            log.warn("GitHub user emails fetch failed, proceeding without email", e);
        }
        return null;
    }
}
