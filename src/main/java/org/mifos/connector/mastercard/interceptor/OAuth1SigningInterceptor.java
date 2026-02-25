package org.mifos.connector.mastercard.interceptor;

import com.mastercard.developer.oauth.OAuth;
import com.mastercard.developer.utils.AuthenticationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * RestTemplate interceptor that adds OAuth 1.0a signature to all Mastercard CBS API requests
 */
@Slf4j
public class OAuth1SigningInterceptor implements ClientHttpRequestInterceptor {

    private final String consumerKey;
    private final PrivateKey signingKey;

    public OAuth1SigningInterceptor(String consumerKey, Resource signingKeyResource, String keyAlias, String keyPassword) {
        this.consumerKey = consumerKey;
        try (InputStream keyStream = signingKeyResource.getInputStream()) {
            this.signingKey = AuthenticationUtils.loadSigningKey(
                    keyStream,
                    keyAlias,
                    keyPassword
            );
            log.info("OAuth1 signing key loaded successfully from: {}", signingKeyResource.getDescription());
        } catch (Exception e) {
            log.error("Failed to load OAuth1 signing key from: {}", signingKeyResource.getDescription(), e);
            throw new RuntimeException("Failed to load OAuth1 signing key", e);
        }
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        try {
            URI uri = request.getURI();
            String method = request.getMethod().name();

            // Convert body bytes to string for signing
            String payload = body.length > 0 ? new String(body, StandardCharsets.UTF_8) : null;

            // Generate OAuth 1.0a signature
            String authHeader = OAuth.getAuthorizationHeader(
                    uri,
                    method,
                    payload,
                    StandardCharsets.UTF_8,
                    consumerKey,
                    signingKey
            );

            // Add Authorization header
            request.getHeaders().add("Authorization", authHeader);

            // Add Mastercard routing header
            request.getHeaders().add("x-mc-routing", "nextgen-apigw");

            log.debug("OAuth1 signature added to request: {} {}", method, uri);

        } catch (Exception e) {
            log.error("Error generating OAuth1 signature", e);
            throw new IOException("OAuth1 signing failed", e);
        }

        return execution.execute(request, body);
    }
}
