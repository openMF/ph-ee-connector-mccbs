package org.mifos.connector.mastercard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.config.MastercardConfig;
import org.mifos.connector.mastercard.model.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class MastercardPaymentService {

    private final RestTemplate restTemplate;
    private final MastercardConfig mastercardConfig;
    private final MastercardAuthService authService;

    public MastercardPaymentResponse initiatePayment(
            String transactionId,
            String payeeAccount,
            BigDecimal amount,
            String currency,
            SupplementaryData suppData) {

        try {
            String token = authService.getAccessToken();
            String apiUrl = mastercardConfig.getApi().getUrl() + "/send/v1/partners/transfer";

            // Build payment request
            MastercardPaymentRequest request = buildPaymentRequest(
                    transactionId, payeeAccount, amount, currency, suppData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<MastercardPaymentRequest> httpRequest = new HttpEntity<>(request, headers);

            String beneficiaryName = (suppData.getRecipientFirstName() != null ? suppData.getRecipientFirstName() : "") +
                    " " + (suppData.getRecipientLastName() != null ? suppData.getRecipientLastName() : "");
            log.info("Initiating CBS payment for transaction: {}, amount: {} {}, beneficiary: {}",
                    transactionId, amount, currency, beneficiaryName.trim());

            ResponseEntity<MastercardPaymentResponse> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    httpRequest,
                    MastercardPaymentResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                MastercardPaymentResponse paymentResponse = response.getBody();
                log.info("CBS payment submitted successfully. Payment ID: {}, Status: {}",
                        paymentResponse.getPaymentId(), paymentResponse.getStatus());
                return paymentResponse;
            } else {
                throw new RuntimeException("Payment submission failed: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("Error submitting payment to Mastercard CBS for transaction: {}", transactionId, e);
            throw new RuntimeException("Payment submission failed: " + e.getMessage(), e);
        }
    }

    public MastercardPaymentStatus retrievePaymentStatus(String paymentId) {
        try {
            String token = authService.getAccessToken();
            String apiUrl = mastercardConfig.getApi().getUrl() + "/send/v1/partners/transfer/" + paymentId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            log.debug("Retrieving CBS payment status for payment ID: {}", paymentId);

            ResponseEntity<MastercardPaymentStatus> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    request,
                    MastercardPaymentStatus.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                MastercardPaymentStatus status = response.getBody();
                log.info("CBS payment status retrieved. Payment ID: {}, Status: {}",
                        paymentId, status.getStatus());
                return status;
            } else {
                throw new RuntimeException("Status retrieval failed: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("Error retrieving payment status from Mastercard CBS for payment ID: {}", paymentId, e);
            throw new RuntimeException("Status retrieval failed: " + e.getMessage(), e);
        }
    }

    private MastercardPaymentRequest buildPaymentRequest(
            String transactionId,
            String payeeAccount,
            BigDecimal amount,
            String currency,
            SupplementaryData suppData) {

        // PHEE-355: Merge data from supplementary data (static fields) and request (dynamic fields)
        return MastercardPaymentRequest.builder()
                .partnerId(mastercardConfig.getApi().getPartnerId())
                .transactionReference(transactionId)
                .paymentType(suppData.getPaymentType()) // PHEE-355: Use payment_type from supplementary data (e.g., "B2P")
                .amount(MastercardPaymentRequest.AmountInfo.builder()
                        .value(amount.toPlainString())
                        .currency(currency)
                        .build())
                .sender(buildSenderInfo(suppData)) // PHEE-355: Sender org info from supplementary data
                .recipient(buildRecipientInfo(payeeAccount, suppData)) // PHEE-355: Recipient details from supplementary data
                .purposeOfPayment(suppData.getPurposeOfPayment() != null ?
                        suppData.getPurposeOfPayment() : "Government disbursement")
                .regulatoryCompliance(MastercardPaymentRequest.RegulatoryCompliance.builder()
                        .sourceOfFunds("GOVERNMENT") // PHEE-355: Static value for G2P payments
                        .purposeCode("GOVT_TRANSFER")
                        .build())
                .build();
    }

    private MastercardPaymentRequest.SenderInfo buildSenderInfo(SupplementaryData suppData) {
        // PHEE-355: Use sender information from supplementary data table
        return MastercardPaymentRequest.SenderInfo.builder()
                .name(suppData.getSenderOrganisationName())
                .address(MastercardPaymentRequest.AddressInfo.builder()
                        .line1(suppData.getSenderAddressLine1())
                        .city(suppData.getSenderAddressCity())
                        .country(suppData.getSenderAddressCountry())
                        .build())
                .account(MastercardPaymentRequest.AccountInfo.builder()
                        .number("GOV-ACCOUNT-001") // TODO: Add to supplementary data if needed
                        .type("GOVERNMENT")
                        .build())
                .build();
    }

    private MastercardPaymentRequest.RecipientInfo buildRecipientInfo(
            String payeeAccount,
            SupplementaryData suppData) {

        // Build full name from first and last name
        String fullName = (suppData.getRecipientFirstName() != null ? suppData.getRecipientFirstName() : "") +
                " " + (suppData.getRecipientLastName() != null ? suppData.getRecipientLastName() : "");

        return MastercardPaymentRequest.RecipientInfo.builder()
                .name(fullName.trim())
                .address(MastercardPaymentRequest.AddressInfo.builder()
                        .line1(suppData.getRecipientAddressLine1())
                        .line2(null) // Not in database schema
                        .city(null) // Not in database schema
                        .state(null) // Not in database schema
                        .postalCode(null) // Not in database schema
                        .country(suppData.getRecipientAddressCountry())
                        .build())
                .account(MastercardPaymentRequest.AccountInfo.builder()
                        .number(payeeAccount)
                        .type("CHECKING")
                        .build())
                .bank(MastercardPaymentRequest.BankInfo.builder()
                        .name(suppData.getBankName())
                        .swiftBic(suppData.getBankSwiftCode())
                        .routingNumber(null) // Not in database schema
                        .country(suppData.getBankCountryCode())
                        .branchCode(suppData.getBankBranchName())
                        .build())
                .taxId(null) // Not in database schema
                .idType(null) // Not in database schema
                .idNumber(null) // Not in database schema
                .build();
    }
}
