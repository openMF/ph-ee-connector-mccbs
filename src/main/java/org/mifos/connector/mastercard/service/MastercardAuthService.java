package org.mifos.connector.mastercard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.config.MastercardConfig;
import org.mifos.connector.mastercard.model.OAuthToken;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MastercardAuthService {

    private final RestTemplate restTemplate;
    private final MastercardConfig mastercardConfig;

    private OAuthToken cachedToken;

    public String getAccessToken() {
        if (cachedToken != null && !cachedToken.isExpired()) {
            log.debug("Using cached OAuth token");
            return cachedToken.getAccessToken();
        }

        log.info("Requesting new OAuth token from Mastercard");
        cachedToken = requestNewToken();
        return cachedToken.getAccessToken();
    }

    private OAuthToken requestNewToken() {
        try {
            String authUrl = mastercardConfig.getApi().getAuthUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", mastercardConfig.getOauth().getGrantType());
            body.add("client_id", mastercardConfig.getOauth().getClientId());
            body.add("client_secret", mastercardConfig.getOauth().getClientSecret());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.debug("POST {} with grant_type={}", authUrl, body.get("grant_type"));

            ResponseEntity<OAuthToken> response = restTemplate.exchange(
                    authUrl,
                    HttpMethod.POST,
                    request,
                    OAuthToken.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                OAuthToken token = response.getBody();
                token.calculateExpiryTime();

                log.info("Successfully obtained OAuth token, expires in {} seconds", token.getExpiresIn());
                return token;
            } else {
                throw new RuntimeException("Failed to obtain OAuth token: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("Error requesting OAuth token from Mastercard", e);
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    public void clearToken() {
        log.info("Clearing cached OAuth token");
        cachedToken = null;
    }
}
