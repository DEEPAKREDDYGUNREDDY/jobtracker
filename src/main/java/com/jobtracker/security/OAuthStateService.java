package com.jobtracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Google's OAuth redirect hits our callback with no Authorization header, so
 * we can't rely on the JwtAuthFilter to tell us which user is connecting.
 * Instead we stuff a signed, short-lived "state" token (userId + expiry +
 * HMAC signature) into the auth URL and verify it when Google redirects back.
 */
@Service
public class OAuthStateService {

    private static final long TTL_MS = 10 * 60 * 1000; // 10 minutes

    private final SecretKeySpec key;

    public OAuthStateService(@Value("${app.jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String createState(UUID userId) {
        long expiry = System.currentTimeMillis() + TTL_MS;
        String payload = userId + ":" + expiry;
        String signature = sign(payload);
        String raw = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the userId encoded in the state, or throws IllegalArgumentException
     * if the state is missing, malformed, expired, or has a bad signature.
     */
    public UUID verifyAndExtractUserId(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth state parameter");
        }

        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Malformed OAuth state parameter", ex);
        }

        String[] parts = raw.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed OAuth state parameter");
        }

        String userIdPart = parts[0];
        String expiryPart = parts[1];
        String signaturePart = parts[2];
        String payload = userIdPart + ":" + expiryPart;

        if (!sign(payload).equals(signaturePart)) {
            throw new IllegalArgumentException("OAuth state signature mismatch");
        }

        long expiry = Long.parseLong(expiryPart);
        if (System.currentTimeMillis() > expiry) {
            throw new IllegalArgumentException("OAuth state has expired, please reconnect Gmail");
        }

        return UUID.fromString(userIdPart);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] signed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign OAuth state", e);
        }
    }
}
