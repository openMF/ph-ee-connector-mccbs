package org.mifos.connector.mastercard.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.Variable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.service.MastercardAuthService;
import org.mifos.connector.mastercard.service.MastercardPaymentService;
import org.mifos.connector.mastercard.service.OperationsService;
import org.mifos.connector.mastercard.service.SupplementaryDataService;
import org.mifos.connector.mastercard.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MastercardCbsWorkers {

    private final MastercardAuthService authService;
    private final SupplementaryDataService supplementaryDataService;
    private final MastercardPaymentService paymentService;
    private final OperationsService operationsService;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // Workers for MastercardFundTransfer-{dfspid} workflow (Simplified flow)
    // ========================================================================

    /**
     * Worker: Lookup Supplemental Data from Channel Request (parses JSON)
     */
    public Map<String, Object> lookupSupplementalDataFromChannelRequest(
            String channelRequest,
            String transactionId) {

        try {
            // Parse channelRequest JSON to extract payeeIdentifier
            Map<String, Object> requestMap = objectMapper.readValue(channelRequest, Map.class);
            Map<String, Object> payee = (Map<String, Object>) requestMap.get("payee");
            Map<String, Object> partyIdInfo = (Map<String, Object>) payee.get("partyIdInfo");
            String payeeIdentifier = (String) partyIdInfo.get("partyIdentifier");

            // Call existing method
            return lookupSupplementalData(payeeIdentifier, transactionId);

        } catch (Exception e) {
            log.error("[GovStack] Error parsing channelRequest for transaction: {}", transactionId, e);
            Map<String, Object> variables = new HashMap<>();
            variables.put("supplementalDataFound", false);
            variables.put("errorCode", "CHANNEL_REQUEST_PARSE_ERROR");
            variables.put("errorMessage", "Failed to parse channelRequest: " + e.getMessage());
            return variables;
        }
    }

    /**
     * Worker: Lookup Supplemental Data (GovStack flow)
     * Looks up supplementary data from database for MastercardFundTransfer workflow
     */
    // @JobWorker annotation removed - using MultiTenantWorkerConfig
    public Map<String, Object> lookupSupplementalData(
            @Variable(name = "payeeIdentifier") String payeeIdentifier,
            @Variable(name = "transactionId") String transactionId) {

        log.info("[GovStack] Looking up supplemental data for transaction: {}, payee: {}",
                transactionId, payeeIdentifier);

        Map<String, Object> variables = new HashMap<>();

        try {
            // Try to find by account number (from identity mapper)
            Optional<SupplementaryData> suppDataOpt = supplementaryDataService.findByAccountNumber(payeeIdentifier);

            // If not found by account number, try by MSISDN
            if (suppDataOpt.isEmpty()) {
                log.debug("Account lookup failed, trying MSISDN: {}", payeeIdentifier);
                suppDataOpt = supplementaryDataService.findByMsisdn(payeeIdentifier);
            }

            if (suppDataOpt.isPresent()) {
                SupplementaryData suppData = suppDataOpt.get();

                // Convert to Map for Zeebe
                @SuppressWarnings("unchecked")
                Map<String, Object> suppDataMap = objectMapper.convertValue(suppData, Map.class);

                variables.put("supplementalDataFound", true);
                variables.put("supplementaryData", suppDataMap);

                log.info("[GovStack] Supplemental data found for transaction: {}, beneficiary: {} {}",
                        transactionId, suppData.getRecipientFirstName(), suppData.getRecipientLastName());
                log.debug("[GovStack] Returning variables: supplementalDataFound={}, supplementaryData keys={}",
                        true, suppDataMap.keySet());

            } else {
                log.warn("[GovStack] No supplemental data found for transaction: {}, payee: {}",
                        transactionId, payeeIdentifier);

                variables.put("supplementalDataFound", false);
                variables.put("errorCode", "SUPPLEMENTAL_DATA_NOT_FOUND");
                variables.put("errorMessage", "No supplemental data found for payee: " + payeeIdentifier);
            }

        } catch (Exception e) {
            log.error("[GovStack] Error looking up supplemental data for transaction: {}", transactionId, e);
            variables.put("supplementalDataFound", false);
            variables.put("errorCode", "SUPPLEMENTAL_DATA_LOOKUP_ERROR");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Merge Data (PHEE-355)
     * Merges transfer data with supplemental data for CBS payment
     */
    // @JobWorker annotation removed - using MultiTenantWorkerConfig
    public Map<String, Object> mergeData(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {

        log.info("[GovStack] Merging transfer and supplemental data for transaction: {}", transactionId);
        log.debug("[GovStack] supplementaryData map received: {}", suppDataMap);

        Map<String, Object> variables = new HashMap<>();

        try {
            // Parse channelRequest to extract required fields
            Map<String, Object> requestMap = objectMapper.readValue(channelRequest, Map.class);

            // Extract amount and currency from channelRequest
            Map<String, Object> amountObj = (Map<String, Object>) requestMap.get("amount");
            String amountValue = (String) amountObj.get("amount");
            String currency = (String) amountObj.get("currency");

            // Extract payeeIdentifier
            Map<String, Object> payee = (Map<String, Object>) requestMap.get("payee");
            Map<String, Object> partyIdInfo = (Map<String, Object>) payee.get("partyIdInfo");
            String payeeIdentifier = (String) partyIdInfo.get("partyIdentifier");

            // Convert amount
            BigDecimal amount = new BigDecimal(amountValue);

            // Check if supplementaryData is null
            if (suppDataMap == null || suppDataMap.isEmpty()) {
                log.error("[GovStack] supplementaryData is null or empty for transaction: {}", transactionId);
                variables.put("mergeSuccess", false);
                variables.put("errorCode", "SUPPLEMENTARY_DATA_MISSING");
                variables.put("errorMessage", "supplementaryData map is null or empty");
                return variables;
            }

            // Convert supplementary data map back to object
            SupplementaryData suppData = objectMapper.convertValue(suppDataMap, SupplementaryData.class);

            if (suppData == null) {
                log.error("[GovStack] Failed to convert suppDataMap to SupplementaryData object for transaction: {}. Map contents: {}",
                        transactionId, suppDataMap);
                variables.put("mergeSuccess", false);
                variables.put("errorCode", "SUPPLEMENTARY_DATA_CONVERSION_FAILED");
                variables.put("errorMessage", "Failed to convert supplementaryData map to object");
                return variables;
            }

            // Create merged payment request (PHEE-355 logic)
            Map<String, Object> mergedPaymentData = new HashMap<>();

            // Transfer data from workflow
            mergedPaymentData.put("transactionId", transactionId);
            mergedPaymentData.put("amount", amount);
            mergedPaymentData.put("currency", currency);

            // Supplemental data for regulatory compliance
            mergedPaymentData.put("payeeAccountNumber", suppData.getPayeeAccountNumber());
            mergedPaymentData.put("recipientFirstName", suppData.getRecipientFirstName());
            mergedPaymentData.put("recipientLastName", suppData.getRecipientLastName());
            mergedPaymentData.put("recipientIdType", suppData.getRecipientIdType());
            mergedPaymentData.put("recipientIdNumber", suppData.getRecipientIdNumber());
            mergedPaymentData.put("recipientAddress", suppData.getRecipientAddressLine1());
            mergedPaymentData.put("recipientCity", suppData.getRecipientAddressCity());
            mergedPaymentData.put("recipientCountry", suppData.getRecipientAddressCountry());
            mergedPaymentData.put("recipientPostalCode", suppData.getRecipientPostalCode());

            // Sender information (from supplemental data)
            mergedPaymentData.put("senderName", suppData.getSenderOrganisationName());
            mergedPaymentData.put("senderIdNumber", suppData.getSenderIdNumber());
            mergedPaymentData.put("senderCountry", suppData.getSenderAddressCountry());

            // Bank information
            mergedPaymentData.put("bankSwiftCode", suppData.getBankSwiftCode());
            mergedPaymentData.put("bankName", suppData.getBankName());
            mergedPaymentData.put("bankAddress", suppData.getBankAddress());

            variables.put("mergedPaymentData", mergedPaymentData);
            variables.put("mergeSuccess", true);

            log.info("[GovStack] Data merge successful for transaction: {}", transactionId);

        } catch (Exception e) {
            log.error("[GovStack] Error merging data for transaction: {}", transactionId, e);
            variables.put("mergeSuccess", false);
            variables.put("errorCode", "DATA_MERGE_FAILED");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Initiate Mastercard CBS Payment (GovStack flow)
     * Submits payment to Mastercard CBS API with merged data
     */
    // @JobWorker annotation removed - using MultiTenantWorkerConfig
    public Map<String, Object> initiatePaymentGovStack(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {

        log.info("[GovStack] Initiating CBS payment for transaction: {}", transactionId);
        log.debug("[GovStack] suppDataMap in initiate: {}", suppDataMap != null ? suppDataMap.keySet() : "null");

        Map<String, Object> variables = new HashMap<>();

        try {
            // Extract required fields from merged data
            BigDecimal amount = convertToBigDecimal(mergedPaymentData.get("amount"));
            String currency = (String) mergedPaymentData.get("currency");
            String payeeAccountNumber = (String) mergedPaymentData.get("payeeAccountNumber");

            // Check if supplementaryData is null
            if (suppDataMap == null || suppDataMap.isEmpty()) {
                log.error("[GovStack] supplementaryData is null or empty in payment initiation for transaction: {}", transactionId);
                variables.put("paymentSuccess", false);
                variables.put("errorCode", "SUPPLEMENTARY_DATA_MISSING");
                variables.put("errorMessage", "supplementaryData map is null or empty");
                return variables;
            }

            // Convert supplementary data map back to object
            SupplementaryData suppData = objectMapper.convertValue(suppDataMap, SupplementaryData.class);

            if (suppData == null) {
                log.error("[GovStack] Failed to convert suppDataMap to object. Keys: {}", suppDataMap.keySet());
                variables.put("paymentSuccess", false);
                variables.put("errorCode", "CONVERSION_FAILED");
                variables.put("errorMessage", "Failed to convert supplementaryData");
                return variables;
            }

            // Submit payment to Mastercard CBS
            MastercardPaymentResponse response = paymentService.initiatePayment(
                    transactionId, payeeAccountNumber, amount, currency, suppData);

            variables.put("paymentSuccess", true);
            variables.put("cbsPaymentId", response.getPaymentId());
            variables.put("cbsPaymentStatus", response.getStatus());
            variables.put("cbsTransactionReference", response.getTransactionReference());

            // Log success with full details
            log.info("[GovStack] ════════════════════════════════════════════════════════════");
            log.info("[GovStack] ✓ CBS PAYMENT SUBMITTED SUCCESSFULLY");
            log.info("[GovStack] ════════════════════════════════════════════════════════════");
            log.info("[GovStack]   Transaction ID        : {}", transactionId);
            log.info("[GovStack]   Payment ID (CBS)      : {}", response.getPaymentId());
            log.info("[GovStack]   Status                : {}", response.getStatus());
            log.info("[GovStack]   Transaction Reference : {}", response.getTransactionReference());
            log.info("[GovStack]   Payee Account         : {}", payeeAccountNumber);
            log.info("[GovStack]   Amount                : {} {}", amount, currency);
            log.info("[GovStack]   Beneficiary           : {} {}",
                    suppData.getRecipientFirstName(), suppData.getRecipientLastName());
            log.info("[GovStack] ════════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("[GovStack] Error initiating CBS payment for transaction: {}", transactionId, e);
            variables.put("paymentSuccess", false);
            variables.put("errorCode", "CBS_PAYMENT_FAILED");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Update Operations DB (GovStack flow)
     * Updates transfer record in PaymentHub operations database
     */
    // @JobWorker annotation removed - using MultiTenantWorkerConfig
    public Map<String, Object> updateOperationsGovStack(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "cbsPaymentId") String cbsPaymentId,
            @Variable(name = "cbsPaymentStatus") String cbsPaymentStatus,
            @Variable(name = "paymentSuccess") Boolean paymentSuccess,
            @Variable(name = "batchId") String batchId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData,
            String tenantId) {

        log.info("[GovStack] Updating operations DB for transaction: {}, success: {}, status: {}, tenant: {}, batchId: {}",
                transactionId, paymentSuccess, cbsPaymentStatus, tenantId, batchId);

        // Extract amount and currency from mergedPaymentData if available
        BigDecimal amount = null;
        String currency = null;
        if (mergedPaymentData != null) {
            Object amountObj = mergedPaymentData.get("amount");
            if (amountObj != null) {
                amount = convertToBigDecimal(amountObj);
            }
            currency = (String) mergedPaymentData.get("currency");
        }

        Map<String, Object> variables = new HashMap<>();

        try {
            // Determine transfer status
            String transferStatus = paymentSuccess != null && paymentSuccess ? "COMPLETED" : "FAILED";

            // Prepare status details
            String statusDetails = cbsPaymentStatus;
            if (statusDetails == null && !paymentSuccess) {
                statusDetails = "Payment failed - CBS did not complete";
            }

            // Call operations-app API to update transfer status
            boolean updateSuccess = operationsService.updateTransferStatus(
                    transactionId,
                    transferStatus,
                    cbsPaymentId,
                    statusDetails,
                    tenantId,
                    batchId,
                    amount,
                    currency
            );

            if (updateSuccess) {
                log.info("[GovStack] Operations DB update completed. Transaction: {}, CBS Payment ID: {}, Status: {}",
                        transactionId, cbsPaymentId != null ? cbsPaymentId : "N/A", cbsPaymentStatus);

                variables.put("operationsUpdateSuccess", true);
                variables.put("transferStatus", transferStatus);
            } else {
                log.warn("[GovStack] Operations DB update returned failure for transaction: {}", transactionId);
                variables.put("operationsUpdateSuccess", false);
                variables.put("errorMessage", "Operations API returned non-success status");
            }

        } catch (Exception e) {
            log.error("[GovStack] Error updating operations DB for transaction: {}", transactionId, e);
            variables.put("operationsUpdateSuccess", false);
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Helper method to convert various number types to BigDecimal
     */
    private BigDecimal convertToBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } else if (value instanceof String) {
            return new BigDecimal((String) value);
        } else {
            throw new IllegalArgumentException("Cannot convert to BigDecimal: " + value);
        }
    }
}
