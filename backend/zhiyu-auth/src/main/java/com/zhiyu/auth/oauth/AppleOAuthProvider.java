package com.zhiyu.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.config.OAuthProperties;
import com.zhiyu.ufp.auth.oauth.OAuthProvider;
import com.zhiyu.ufp.auth.oauth.OAuthRequest;
import com.zhiyu.ufp.auth.oauth.OAuthUserInfo;
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

    private static final String PROVIDER = "APPLE";
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
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
        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new BizException(ERR_CODE_INVALID, "Apple ID Token must not be empty");
        }

        try {
            String[] parts = request.idToken().split("\\.");
            if (parts.length < 2) {
                throw new BizException(ERR_CODE_INVALID, "Apple ID Token format is invalid");
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);
            String kid = header.get("kid").asText();

            PublicKey publicKey = fetchApplePublicKey(kid);
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(request.idToken())
                    .getPayload();

            String sub = claims.getSubject();
            String email = claims.get("email", String.class);
            Boolean emailVerified = claims.get("email_verified", Boolean.class);

            return new OAuthUserInfo(sub, null, null, null,
                    email, emailVerified != null && emailVerified);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple ID Token verification failed", e);
            throw new BizException(ERR_CODE_INVALID, "Apple ID Token verification failed");
        }
    }

    private PublicKey fetchApplePublicKey(final String kid) throws Exception {
        String keysBody = restTemplate.getForObject(APPLE_KEYS_URL, String.class);
        JsonNode keysResp = objectMapper.readTree(keysBody);
        JsonNode keys = keysResp.get("keys");

        for (JsonNode key : keys) {
            if (kid.equals(key.get("kid").asText())) {
                String n = key.get("n").asText();
                String e = key.get("e").asText();
                byte[] nBytes = Base64.getUrlDecoder().decode(n);
                byte[] eBytes = Base64.getUrlDecoder().decode(e);
                BigInteger modulus = new BigInteger(1, nBytes);
                BigInteger exponent = new BigInteger(1, eBytes);
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(spec);
            }
        }
        throw new BizException(ERR_THIRD_PARTY, "Failed to fetch Apple public key");
    }
}
