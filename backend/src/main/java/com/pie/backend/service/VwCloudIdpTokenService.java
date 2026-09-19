package com.pie.backend.service;

import com.pie.backend.config.VwLlmaasProperties;
import com.pie.backend.exception.LlmaasAuthException;
import com.pie.shared.dto.llmaas.IdpTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Equivalent of the Python {@code get_token()} helper: obtains an OAuth2
 * access token from VW Cloud IDP using the client-credentials grant, caches
 * it in memory, and transparently refreshes it shortly before it expires.
 * <p>
 * The access token (and the client secret) are never logged.
 */
@Service
public class VwCloudIdpTokenService {

    private static final Logger log = LoggerFactory.getLogger(VwCloudIdpTokenService.class);

    /** Refresh this many seconds before the token's actual expiry to avoid races. */
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private final WebClient idpWebClient;
    private final VwLlmaasProperties properties;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

    public VwCloudIdpTokenService(WebClient vwIdpWebClient, VwLlmaasProperties properties) {
        this.idpWebClient = vwIdpWebClient;
        this.properties = properties;
    }

    /**
     * Returns a valid bearer access token, fetching or refreshing it from
     * Cloud IDP as needed.
     */
    public String getToken() {
        String token = cachedAccessToken;
        if (token != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return token;
        }

        refreshLock.lock();
        try {
            // Re-check after acquiring the lock in case another thread already refreshed.
            token = cachedAccessToken;
            if (token != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
                return token;
            }
            return fetchNewToken();
        } finally {
            refreshLock.unlock();
        }
    }

    private String fetchNewToken() {
        VwLlmaasProperties.Idp idp = properties.getIdp();
        if (!StringUtils.hasText(idp.getClientId()) || !StringUtils.hasText(idp.getClientSecret())) {
            throw new LlmaasAuthException(
                    "VW LLMaaS Cloud IDP credentials are not configured. Set VW_LLM_CLIENT_ID and VW_LLM_CLIENT_SECRET.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", idp.getClientId());
        form.add("client_secret", idp.getClientSecret());
        form.add("grant_type", "client_credentials");

        log.debug("Requesting new VW Cloud IDP access token");

        try {
            IdpTokenResponse response = idpWebClient.post()
                    .uri(idp.getTokenUri())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(IdpTokenResponse.class)
                    .block();

            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new LlmaasAuthException("VW Cloud IDP returned an empty token response");
            }

            cachedAccessToken = response.accessToken();
            long ttlSeconds = Math.max(response.expiresInSeconds() - EXPIRY_SAFETY_MARGIN_SECONDS, 0);
            cachedTokenExpiresAt = Instant.now().plusSeconds(ttlSeconds);

            log.info("Obtained new VW Cloud IDP access token, valid for {}s", ttlSeconds);
            return cachedAccessToken;
        } catch (WebClientResponseException ex) {
            log.warn("VW Cloud IDP token request failed with status {}", ex.getStatusCode().value());
            throw new LlmaasAuthException(
                    "VW Cloud IDP token request failed with status " + ex.getStatusCode().value(), ex);
        } catch (WebClientRequestException ex) {
            log.warn("VW Cloud IDP token request could not be sent: {}", ex.getMessage());
            throw new LlmaasAuthException("Unable to reach VW Cloud IDP token endpoint", ex);
        }
    }
}
