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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component("appleOAuthProvider")
@RequiredArgsConstructor
public class AppleOAuthProvider implements OAuthProvider {

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";

    private final OAuthProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return OAuthField.PROVIDER_APPLE;
    }

    @Override
    public OAuthUserInfo authorize(final OAuthRequest request) throws BizException {
        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
        }

        try {
            String[] parts = request.idToken().split("\\.");
            if (parts.length < 2) {
                throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);
            String kid = header.get(OAuthField.KID).asText();

            PublicKey publicKey = fetchApplePublicKey(kid);
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(request.idToken())
                    .getPayload();

            String sub = claims.getSubject();
            String email = claims.get(OAuthField.EMAIL, String.class);
            Boolean emailVerified = claims.get(OAuthField.EMAIL_VERIFIED, Boolean.class);

            return new OAuthUserInfo(sub, null, null, null,
                    email, emailVerified != null && emailVerified);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple ID Token verification failed", e);
            throw new BizException(BizErrorCode.OAUTH_CODE_INVALID);
        }
    }

    private PublicKey fetchApplePublicKey(final String kid) throws Exception {
        String keysBody = restTemplate.getForObject(APPLE_KEYS_URL, String.class);
        JsonNode keysResp = objectMapper.readTree(keysBody);
        JsonNode keys = keysResp.get(OAuthField.KEYS);

        for (JsonNode key : keys) {
            if (kid.equals(key.get(OAuthField.KID).asText())) {
                String n = key.get(OAuthField.N).asText();
                String e = key.get(OAuthField.E).asText();
                byte[] nBytes = Base64.getUrlDecoder().decode(n);
                byte[] eBytes = Base64.getUrlDecoder().decode(e);
                BigInteger modulus = new BigInteger(1, nBytes);
                BigInteger exponent = new BigInteger(1, eBytes);
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(spec);
            }
        }
        throw new BizException(BizErrorCode.OAUTH_THIRD_PARTY_ERROR);
    }
}
