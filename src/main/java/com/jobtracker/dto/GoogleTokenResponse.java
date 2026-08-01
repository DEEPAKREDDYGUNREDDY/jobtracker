package com.jobtracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps the JSON body Google's token endpoint returns from both the
 * authorization_code and refresh_token grant flows. refreshToken is null
 * on a refresh-token exchange (Google only issues it once, on first consent).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        String access_token,
        String refresh_token,
        Integer expires_in,
        String scope,
        String token_type,
        String id_token
) {
}
