package com.jobtracker.service;

import com.jobtracker.dto.GoogleTokenResponse;
import com.jobtracker.dto.GoogleUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to Google's OAuth2 + Gmail userinfo endpoints directly over
 * RestTemplate. We hand-roll this instead of using Spring Security's
 * oauth2-client because we need per-user tokens persisted in our own
 * table (gmail_accounts), not a login-session flow.
 */
@Service
public class GoogleOAuthService {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthService(
            RestTemplate restTemplate,
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.redirect-uri:http://localhost:8080/api/gmail/callback}") String redirectUri
    ) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String buildConsentUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", GMAIL_READONLY_SCOPE + " email")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public GoogleTokenResponse exchangeCodeForTokens(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", redirectUri);

        return postForToken(body);
    }

    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        return postForToken(body);
    }

    public String fetchGoogleEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        GoogleUserInfoResponse response = restTemplate.exchange(
                USERINFO_ENDPOINT, HttpMethod.GET, entity, GoogleUserInfoResponse.class
        ).getBody();

        return response != null ? response.email() : null;
    }

    private GoogleTokenResponse postForToken(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(TOKEN_ENDPOINT, entity, GoogleTokenResponse.class);
    }
}
