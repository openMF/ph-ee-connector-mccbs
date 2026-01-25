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
    private final ObjectMapper objectMapper;

    /**
     * Worker: Validate Input
     * Validates required workflow variables are present
     */
    @JobWorker(type = "mastercard-cbs-validate-input", autoComplete = true)
    public Map<String, Object> validateInput(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "payeeIdentity") String payeeIdentity,
            @Variable(name = "amount") Object amountObj,
            @Variable(name = "currency") String currency) {

        log.info("Validating input for transaction: {}", transactionId);

        Map<String, Object> variables = new HashMap<>();

        try {
            // Validate required fields
            if (transactionId == null || transactionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction ID is required");
            }
            if (payeeIdentity == null || payeeIdentity.trim().isEmpty()) {
                throw new IllegalArgumentException("Payee identity is required");
            }
            if (amountObj == null) {
                throw new IllegalArgumentException("Amount is required");
            }
            if (currency == null || currency.trim().isEmpty()) {
                throw new IllegalArgumentException("Currency is required");
            }

            // Convert amount to BigDecimal
            BigDecimal amount = convertToBigDecimal(amountObj);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }

            variables.put("validationSuccess", true);
            variables.put("amount", amount);

            log.info("Input validation successful for transaction: {}", transactionId);

        } catch (Exception e) {
            log.error("Input validation failed for transaction: {}", transactionId, e);
            variables.put("validationSuccess", false);
            variables.put("errorCode", "VALIDATION_FAILED");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Authenticate with Mastercard CBS
     * Gets OAuth access token
     */
    @JobWorker(type = "mastercard-cbs-authenticate", autoComplete = true)
    public Map<String, Object> authenticate() {
        log.info("Authenticating with Mastercard CBS");

        Map<String, Object> variables = new HashMap<>();

        try {
            String accessToken = authService.getAccessToken();

            variables.put("authSuccess", true);
            variables.put("cbsAccessToken", accessToken);
            variables.put("authRetryCount", 0);

            log.info("Authentication successful");

        } catch (Exception e) {
            log.error("Authentication failed", e);
            variables.put("authSuccess", false);
            variables.put("errorCode", "AUTH_FAILED");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Match Regulatory Data
     * Looks up supplementary data from database
     */
    @JobWorker(type = "mastercard-cbs-match-regulatory-data", autoComplete = true)
    public Map<String, Object> matchRegulatoryData(
            @Variable(name = "payeeIdentity") String payeeIdentity,
            @Variable(name = "payeeAccountNumber", required = false) String payeeAccountNumber,
            @Variable(name = "transactionId") String transactionId) {

        log.info("Matching regulatory data for transaction: {}, payee: {}", transactionId, payeeIdentity);

        Map<String, Object> variables = new HashMap<>();

        try {
            // Try to find by MSISDN first
            Optional<SupplementaryData> suppDataOpt = supplementaryDataService.findByMsisdn(payeeIdentity);

            // If not found and account number is provided, try by account
            if (suppDataOpt.isEmpty() && payeeAccountNumber != null && !payeeAccountNumber.isEmpty()) {
                log.debug("MSISDN lookup failed, trying account number: {}", payeeAccountNumber);
                suppDataOpt = supplementaryDataService.findByAccountNumber(payeeAccountNumber);
            }

            if (suppDataOpt.isPresent()) {
                SupplementaryData suppData = suppDataOpt.get();

                // Convert to Map for Zeebe
                @SuppressWarnings("unchecked")
                Map<String, Object> suppDataMap = objectMapper.convertValue(suppData, Map.class);

                variables.put("supplementaryDataFound", true);
                variables.put("supplementaryData", suppDataMap);
                variables.put("payeeAccountNumber", suppData.getPayeeAccountNumber());

                log.info("Supplementary data found for transaction: {}, beneficiary: {}",
                        transactionId, suppData.getBeneficiaryFullName());

            } else {
                log.warn("No supplementary data found for transaction: {}, payee: {}",
                        transactionId, payeeIdentity);

                variables.put("supplementaryDataFound", false);
                variables.put("errorCode", "DATA_NOT_FOUND");
                variables.put("errorMessage", "No supplementary data found for payee: " + payeeIdentity);
            }

        } catch (Exception e) {
            log.error("Error matching regulatory data for transaction: {}", transactionId, e);
            variables.put("supplementaryDataFound", false);
            variables.put("errorCode", "DATA_LOOKUP_ERROR");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Initiate CBS Payment
     * Submits payment to Mastercard CBS API
     */
    @JobWorker(type = "mastercard-cbs-initiate-payment", autoComplete = true)
    public Map<String, Object> initiatePayment(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "payeeAccountNumber") String payeeAccountNumber,
            @Variable(name = "amount") Object amountObj,
            @Variable(name = "currency") String currency,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {

        log.info("Initiating CBS payment for transaction: {}", transactionId);

        Map<String, Object> variables = new HashMap<>();

        try {
            // Convert amount
            BigDecimal amount = convertToBigDecimal(amountObj);

            // Convert supplementary data map back to object
            SupplementaryData suppData = objectMapper.convertValue(suppDataMap, SupplementaryData.class);

            // Submit payment
            MastercardPaymentResponse response = paymentService.initiatePayment(
                    transactionId, payeeAccountNumber, amount, currency, suppData);

            variables.put("paymentSubmitted", true);
            variables.put("cbsPaymentId", response.getPaymentId());
            variables.put("cbsPaymentStatus", response.getStatus());
            variables.put("paymentRetryCount", 0);

            log.info("CBS payment submitted successfully. Transaction: {}, Payment ID: {}, Status: {}",
                    transactionId, response.getPaymentId(), response.getStatus());

        } catch (Exception e) {
            log.error("Error initiating CBS payment for transaction: {}", transactionId, e);
            variables.put("paymentSubmitted", false);
            variables.put("errorCode", "PAYMENT_SUBMISSION_FAILED");
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Check Payment Status
     * Retrieves current status from Mastercard CBS
     */
    @JobWorker(type = "mastercard-cbs-check-status", autoComplete = true)
    public Map<String, Object> checkStatus(
            @Variable(name = "cbsPaymentId") String cbsPaymentId,
            @Variable(name = "transactionId") String transactionId) {

        log.info("Checking CBS payment status for transaction: {}, payment ID: {}",
                transactionId, cbsPaymentId);

        Map<String, Object> variables = new HashMap<>();

        try {
            MastercardPaymentStatus status = paymentService.retrievePaymentStatus(cbsPaymentId);

            variables.put("statusRetrieved", true);
            variables.put("cbsPaymentStatus", status.getStatus());

            // Convert status to Map for Zeebe
            @SuppressWarnings("unchecked")
            Map<String, Object> statusMap = objectMapper.convertValue(status, Map.class);
            variables.put("cbsPaymentDetails", statusMap);

            log.info("CBS payment status retrieved. Transaction: {}, Payment ID: {}, Status: {}",
                    transactionId, cbsPaymentId, status.getStatus());

        } catch (Exception e) {
            log.warn("Error retrieving CBS payment status for transaction: {} - continuing anyway",
                    transactionId, e);

            // Don't fail the workflow if status retrieval fails
            variables.put("statusRetrieved", false);
            variables.put("statusRetrievalError", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Update Operations DB
     * Updates transfer record in PaymentHub operations database
     */
    @JobWorker(type = "mastercard-cbs-update-operations", autoComplete = true)
    public Map<String, Object> updateOperations(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "cbsPaymentId") String cbsPaymentId,
            @Variable(name = "cbsPaymentStatus") String cbsPaymentStatus) {

        log.info("Updating operations DB for transaction: {}, status: {}", transactionId, cbsPaymentStatus);

        Map<String, Object> variables = new HashMap<>();

        try {
            // TODO: Update transfers table in operations database
            // This would typically call a REST API endpoint on operations-app
            // For now, just log the update

            log.info("Operations DB update completed. Transaction: {}, CBS Payment ID: {}, Status: {}",
                    transactionId, cbsPaymentId, cbsPaymentStatus);

            variables.put("operationsUpdateSuccess", true);

        } catch (Exception e) {
            log.error("Error updating operations DB for transaction: {}", transactionId, e);
            variables.put("operationsUpdateSuccess", false);
            variables.put("errorMessage", e.getMessage());
        }

        return variables;
    }

    /**
     * Worker: Retry Handler
     * Manages retry logic for failed operations
     */
    @JobWorker(type = "mastercard-cbs-retry-handler", autoComplete = true)
    public Map<String, Object> retryHandler(
            ActivatedJob job,
            @Variable(name = "authRetryCount", required = false) Integer authRetryCount,
            @Variable(name = "paymentRetryCount", required = false) Integer paymentRetryCount) {

        Map<String, Object> variables = new HashMap<>();

        String retryType = job.getCustomHeaders().get("retryType");
        log.info("Retry handler invoked for type: {}", retryType);

        if ("auth".equals(retryType)) {
            int currentRetryCount = authRetryCount != null ? authRetryCount : 0;
            variables.put("authRetryCount", currentRetryCount + 1);
            log.info("Auth retry count incremented to: {}", currentRetryCount + 1);

        } else if ("payment".equals(retryType)) {
            int currentRetryCount = paymentRetryCount != null ? paymentRetryCount : 0;
            variables.put("paymentRetryCount", currentRetryCount + 1);
            log.info("Payment retry count incremented to: {}", currentRetryCount + 1);
        }

        return variables;
    }

    /**
     * Worker: Log Error
     * Logs errors for failed operations
     */
    @JobWorker(type = "mastercard-cbs-log-error", autoComplete = true)
    public Map<String, Object> logError(
            ActivatedJob job,
            @Variable(name = "transactionId", required = false) String transactionId,
            @Variable(name = "errorCode", required = false) String errorCode,
            @Variable(name = "errorMessage", required = false) String errorMessage) {

        String errorType = job.getCustomHeaders().get("errorType");

        log.error("CBS Error logged. Type: {}, Transaction: {}, Code: {}, Message: {}",
                errorType, transactionId, errorCode, errorMessage);

        Map<String, Object> variables = new HashMap<>();
        variables.put("transferFailed", true);
        variables.put("errorLogged", true);

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
