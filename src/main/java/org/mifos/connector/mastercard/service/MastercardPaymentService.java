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

            log.info("Initiating CBS payment for transaction: {}, amount: {} {}, beneficiary: {}",
                    transactionId, amount, currency, suppData.getBeneficiaryFullName());

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

        return MastercardPaymentRequest.builder()
                .partnerId(mastercardConfig.getApi().getPartnerId())
                .transactionReference(transactionId)
                .paymentType("PERSON_TO_PERSON")
                .amount(MastercardPaymentRequest.AmountInfo.builder()
                        .value(amount.toPlainString())
                        .currency(currency)
                        .build())
                .sender(buildSenderInfo())
                .recipient(buildRecipientInfo(payeeAccount, suppData))
                .purposeOfPayment(suppData.getPurposeOfPayment() != null ?
                        suppData.getPurposeOfPayment() : "Government disbursement")
                .regulatoryCompliance(MastercardPaymentRequest.RegulatoryCompliance.builder()
                        .sourceOfFunds(suppData.getSourceOfFunds() != null ?
                                suppData.getSourceOfFunds() : "GOVERNMENT")
                        .purposeCode("GOVT_TRANSFER")
                        .build())
                .build();
    }

    private MastercardPaymentRequest.SenderInfo buildSenderInfo() {
        // Default sender (government program)
        return MastercardPaymentRequest.SenderInfo.builder()
                .name("GovStack Payment Program")
                .address(MastercardPaymentRequest.AddressInfo.builder()
                        .line1("Government Plaza")
                        .city("Capital City")
                        .country("XX")
                        .build())
                .account(MastercardPaymentRequest.AccountInfo.builder()
                        .number("GOV-ACCOUNT-001")
                        .type("GOVERNMENT")
                        .build())
                .build();
    }

    private MastercardPaymentRequest.RecipientInfo buildRecipientInfo(
            String payeeAccount,
            SupplementaryData suppData) {

        return MastercardPaymentRequest.RecipientInfo.builder()
                .name(suppData.getBeneficiaryFullName())
                .address(MastercardPaymentRequest.AddressInfo.builder()
                        .line1(suppData.getBeneficiaryAddressLine1())
                        .line2(suppData.getBeneficiaryAddressLine2())
                        .city(suppData.getBeneficiaryCity())
                        .state(suppData.getBeneficiaryState())
                        .postalCode(suppData.getBeneficiaryPostalCode())
                        .country(suppData.getBeneficiaryCountryCode())
                        .build())
                .account(MastercardPaymentRequest.AccountInfo.builder()
                        .number(payeeAccount)
                        .type("CHECKING")
                        .build())
                .bank(MastercardPaymentRequest.BankInfo.builder()
                        .name(suppData.getBankName())
                        .swiftBic(suppData.getBankBicSwift())
                        .routingNumber(suppData.getBankRoutingNumber())
                        .country(suppData.getBankCountryCode())
                        .branchCode(suppData.getBankBranchCode())
                        .build())
                .taxId(suppData.getBeneficiaryTaxId())
                .idType(suppData.getBeneficiaryIdType())
                .idNumber(suppData.getBeneficiaryIdNumber())
                .build();
    }
}
