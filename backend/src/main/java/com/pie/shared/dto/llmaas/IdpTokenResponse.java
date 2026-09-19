package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned by VW Cloud IDP's OAuth2 client-credentials token
 * endpoint. Mirrors the Python {@code get_token()} response shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdpTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresInSeconds,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope") String scope
) {
}
