package org.mifos.connector.mastercard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.config.OperationsConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final RestTemplate restTemplate;
    private final OperationsConfig operationsConfig;

    /**
     * Update transfer status in operations database
     *
     * @param transactionId   Transfer transaction ID
     * @param status          Transfer status (COMPLETED, FAILED, etc.)
     * @param externalId      External system reference (e.g., CBS payment ID)
     * @param statusDetails   Additional status information
     * @param tenantId        Tenant identifier
     * @return true if update successful, false otherwise
     */
    public boolean updateTransferStatus(String transactionId, String status,
                                        String externalId, String statusDetails,
                                        String tenantId) {

        if (!operationsConfig.getApi().getEnabled()) {
            log.info("[OperationsDB] Integration disabled - skipping update for transaction: {} (status={}, externalId={})",
                    transactionId, status, externalId);
            return true;
        }

        try {
            String url = operationsConfig.getApi().getBaseUrl() +
                    "/api/v1/transfers/" + transactionId + "/status";

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("status", status);
            if (externalId != null) {
                requestBody.put("externalId", externalId);
            }
            if (statusDetails != null) {
                requestBody.put("statusDetails", statusDetails);
            }

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (tenantId != null) {
                headers.set("Platform-TenantId", tenantId);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("[OperationsDB] Updating transfer status - transaction: {}, status: {}, externalId: {}, tenant: {}",
                    transactionId, status, externalId, tenantId);
            log.debug("[OperationsDB] API request: POST {} with body: {}", url, requestBody);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[OperationsDB] ✓ Successfully updated operations DB for transaction: {}, HTTP status: {}",
                        transactionId, response.getStatusCode());
                return true;
            } else {
                log.warn("[OperationsDB] ✗ Update returned non-success status for transaction: {}, HTTP status: {}",
                        transactionId, response.getStatusCode());
                return false;
            }

        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Connection refused or network error - operations-app may not be deployed
            log.warn("[OperationsDB] ⚠ Operations-app API unreachable for transaction: {} (Connection refused: {}). " +
                    "Payment completed successfully, but status not recorded in operations DB. " +
                    "This is normal if operations-app is not deployed.",
                    transactionId, e.getMessage());
            log.info("[OperationsDB] Payment details - status: {}, externalId: {}, statusDetails: {}",
                    status, externalId, statusDetails);
            return true; // Return true to not block the workflow
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 4xx error - client error (transfer not found, bad request, etc.)
            log.error("[OperationsDB] ✗ Client error updating transaction: {}, HTTP status: {}, response body: {}",
                    transactionId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // 5xx error - server error
            log.error("[OperationsDB] ✗ Server error updating transaction: {}, HTTP status: {}, response body: {}",
                    transactionId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("[OperationsDB] ✗ Unexpected error updating transaction: {}", transactionId, e);
            return false;
        }
    }
}
