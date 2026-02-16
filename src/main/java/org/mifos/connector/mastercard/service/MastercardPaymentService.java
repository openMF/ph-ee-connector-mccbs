package org.mifos.connector.mastercard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.config.MastercardConfig;
import org.mifos.connector.mastercard.model.*;
import org.mifos.connector.mastercard.model.xml.PaymentRequestXml;
import org.mifos.connector.mastercard.model.xml.PaymentResponseXml;
import org.mifos.connector.mastercard.util.EncryptionUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class MastercardPaymentService {

    private final RestTemplate restTemplate;
    private final MastercardConfig mastercardConfig;
    private final MastercardAuthService authService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public MastercardPaymentResponse initiatePayment(
            String transactionId,
            String payeeAccount,
            BigDecimal amount,
            String currency,
            SupplementaryData suppData) {

        try {
            // Use correct CBS endpoint with partner ID
            String apiUrl = mastercardConfig.getApi().getUrl() +
                    "/send/v1/partners/" +
                    mastercardConfig.getApi().getPartnerId() +
                    "/crossborder/payment";

            // Build payment request
            MastercardPaymentRequest request = buildPaymentRequest(
                    transactionId, payeeAccount, amount, currency, suppData);

            String beneficiaryName = (suppData.getRecipientFirstName() != null ? suppData.getRecipientFirstName() : "") +
                    " " + (suppData.getRecipientLastName() != null ? suppData.getRecipientLastName() : "");
            log.info("Initiating CBS payment for transaction: {}, amount: {} {}, beneficiary: {}, encryption: {}",
                    transactionId, amount, currency, beneficiaryName.trim(), mastercardConfig.getEncryption().getEnabled());

            // Build XML request (Mastercard CBS API requires XML format)
            PaymentRequestXml xmlRequest = buildPaymentRequestXml(transactionId, payeeAccount, amount, currency, suppData);

            // Debug: Log the XML request
            try {
                jakarta.xml.bind.JAXBContext jaxbContext = jakarta.xml.bind.JAXBContext.newInstance(PaymentRequestXml.class);
                jakarta.xml.bind.Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                java.io.StringWriter sw = new java.io.StringWriter();
                marshaller.marshal(xmlRequest, sw);
                log.info("XML Request being sent:\n{}", sw.toString());
            } catch (Exception e) {
                log.warn("Could not marshal XML for logging", e);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);  // Changed to XML
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_XML));

            // Check if encryption is enabled
            if (Boolean.TRUE.equals(mastercardConfig.getEncryption().getEnabled())) {
                // Encrypt the payload
                return initiatePaymentWithEncryptionXml(apiUrl, headers, xmlRequest);
            } else {
                // Send unencrypted XML (for real Mastercard sandbox)
                HttpEntity<PaymentRequestXml> httpRequest = new HttpEntity<>(xmlRequest, headers);

                ResponseEntity<PaymentResponseXml> response = restTemplate.exchange(
                        apiUrl,
                        HttpMethod.POST,
                        httpRequest,
                        PaymentResponseXml.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    PaymentResponseXml xmlResponse = response.getBody();
                    log.info("CBS payment submitted successfully (XML unencrypted). Payment ID: {}, Status: {}",
                            xmlResponse.getId(), xmlResponse.getStatus());

                    // Convert XML response to internal format
                    return convertXmlToJsonResponse(xmlResponse);
                } else {
                    throw new RuntimeException("Payment submission failed: " + response.getStatusCode());
                }
            }

        } catch (RestClientException e) {
            log.error("Error submitting payment to Mastercard CBS for transaction: {}", transactionId, e);
            throw new RuntimeException("Payment submission failed: " + e.getMessage(), e);
        }
    }

    private MastercardPaymentResponse initiatePaymentWithEncryption(String apiUrl, HttpHeaders headers,
                                                                     MastercardPaymentRequest request) {
        System.out.println("===TDEBUG=== initiatePaymentWithEncryption CALLED");
        try {
            System.out.println("===TDEBUG=== Entered try block");
            // Convert request to JSON string
            String plainPayload = objectMapper.writeValueAsString(request);
            System.out.println("=== MASTERCARD CBS ENCRYPTION DEBUG ===");
            System.out.println("API URL: " + apiUrl);
            System.out.println("Plain payload (before encryption): " + plainPayload);
            System.out.println("Encryption fingerprint: " + mastercardConfig.getEncryption().getFingerPrint());
            System.out.println("Cert file: " + mastercardConfig.getEncryption().getCertificateFile());

            // Load encryption resources
            Resource certFile = resourceLoader.getResource(mastercardConfig.getEncryption().getCertificateFile());

            // Encrypt the payload using JWE
            String encryptedData = EncryptionUtils.jweEncrypt(
                    plainPayload,
                    certFile,
                    mastercardConfig.getEncryption().getFingerPrint(),
                    MediaType.APPLICATION_JSON_VALUE,
                    mastercardConfig.getEncryption().getCertificatePassword()
            );

            System.out.println("Payload encrypted successfully. Length: " + encryptedData.length() + " chars");
            System.out.println("Encrypted JWE (first 150 chars): " + encryptedData.substring(0, Math.min(150, encryptedData.length())));

            // Wrap encrypted payload in Mastercard format (per official reference app)
            EncryptedPayload encryptedPayload = EncryptedPayload.builder()
                    .encryptedPayload(EncryptedPayload.EncryptedData.builder()
                            .data(encryptedData)
                            .build())
                    .build();

            // Log the wrapped payload structure
            String wrappedPayloadJson = objectMapper.writeValueAsString(encryptedPayload);
            System.out.println("Wrapped encrypted payload structure: " + wrappedPayloadJson);

            // Add Mastercard CBS required headers
            headers.add("x-mc-routing", "nextgen-apigw");  // Per official reference app
            headers.add("x-encrypted", "true");

            System.out.println("Request headers: " + headers);
            System.out.println("Sending encrypted request to Mastercard CBS...");

            HttpEntity<EncryptedPayload> httpRequest = new HttpEntity<>(encryptedPayload, headers);

            // Send encrypted request
            ResponseEntity<EncryptedPayload> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    httpRequest,
                    EncryptedPayload.class
            );

            log.info("Response status: {}", response.getStatusCode());
            log.info("Response headers: {}", response.getHeaders());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Decrypt the response
                String encryptedResponse = response.getBody().getEncryptedPayload().getData();
                log.debug("Received encrypted response");

                Resource decryptionKeyFile = resourceLoader.getResource(
                        mastercardConfig.getEncryption().getDecryptionKeyFile());

                String decryptedResponse = EncryptionUtils.jweDecrypt(
                        encryptedResponse,
                        decryptionKeyFile,
                        mastercardConfig.getEncryption().getDecryptionKeyAlias(),
                        mastercardConfig.getEncryption().getDecryptionKeyPassword()
                );

                log.debug("Response decrypted successfully");

                // Parse decrypted response
                MastercardPaymentResponse paymentResponse = objectMapper.readValue(
                        decryptedResponse,
                        MastercardPaymentResponse.class
                );

                log.info("CBS payment submitted successfully (encrypted). Payment ID: {}, Status: {}",
                        paymentResponse.getPaymentId(), paymentResponse.getStatus());

                return paymentResponse;
            } else {
                throw new RuntimeException("Payment submission failed: " + response.getStatusCode());
            }

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("=== MASTERCARD CBS ERROR RESPONSE ===");
            log.error("HTTP Status: {}", e.getStatusCode());
            log.error("Response Body: {}", e.getResponseBodyAsString());
            log.error("Response Headers: {}", e.getResponseHeaders());
            log.error("Error in encrypted payment submission", e);
            throw new RuntimeException("Encrypted payment submission failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error in encrypted payment submission", e);
            throw new RuntimeException("Encrypted payment submission failed: " + e.getMessage(), e);
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

    private PaymentRequestXml buildPaymentRequestXml(
            String transactionId,
            String payeeAccount,
            BigDecimal amount,
            String currency,
            SupplementaryData suppData) {

        // Build recipient name
        String firstName = suppData.getRecipientFirstName() != null ? suppData.getRecipientFirstName() : "";
        String lastName = suppData.getRecipientLastName() != null ? suppData.getRecipientLastName() : "";

        return PaymentRequestXml.builder()
                .paymentRequest(PaymentRequestXml.PaymentRequestDetail.builder()
                        .transactionReference(transactionId)
                        .recipientAccountUri(suppData.getPayeeAccountNumber() != null ?
                                suppData.getPayeeAccountNumber() : payeeAccount)
                        .paymentAmount(PaymentRequestXml.PaymentAmount.builder()
                                .amount(amount.toPlainString())
                                .currency(currency)
                                .build())
                        .paymentOriginationCountry(suppData.getSenderAddressCountry())
                        .paymentType(suppData.getPaymentType())
                        .sender(PaymentRequestXml.Sender.builder()
                                .organizationName(suppData.getSenderOrganisationName())
                                .address(PaymentRequestXml.Address.builder()
                                        .line1(suppData.getSenderAddressLine1())
                                        .city(suppData.getSenderAddressCity())
                                        .country(suppData.getSenderAddressCountry())
                                        .build())
                                .build())
                        .recipient(PaymentRequestXml.Recipient.builder()
                                .firstName(firstName)
                                .lastName(lastName)
                                .address(PaymentRequestXml.Address.builder()
                                        .line1(suppData.getRecipientAddressLine1())
                                        .country(suppData.getRecipientAddressCountry())
                                        .build())
                                .build())
                        .purposeOfPayment(suppData.getPurposeOfPayment() != null ?
                                suppData.getPurposeOfPayment() : "Government disbursement")
                        .build())
                .build();
    }

    private MastercardPaymentResponse convertXmlToJsonResponse(PaymentResponseXml xmlResponse) {
        return MastercardPaymentResponse.builder()
                .paymentId(xmlResponse.getId())
                .status(xmlResponse.getStatus())
                .transactionReference(xmlResponse.getTransactionReference())
                .build();
    }

    private MastercardPaymentResponse initiatePaymentWithEncryptionXml(
            String apiUrl,
            HttpHeaders headers,
            PaymentRequestXml xmlRequest) {

        try {
            // For future use when encryption is enabled with XML format
            // This will require converting XML to string, encrypting, and wrapping
            log.warn("XML encryption not yet implemented - falling back to unencrypted XML");

            // Fallback to unencrypted for now
            HttpEntity<PaymentRequestXml> httpRequest = new HttpEntity<>(xmlRequest, headers);

            ResponseEntity<PaymentResponseXml> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    httpRequest,
                    PaymentResponseXml.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                PaymentResponseXml xmlResponse = response.getBody();
                log.info("CBS payment submitted successfully (XML). Payment ID: {}, Status: {}",
                        xmlResponse.getId(), xmlResponse.getStatus());
                return convertXmlToJsonResponse(xmlResponse);
            } else {
                throw new RuntimeException("Payment submission failed: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("Error in XML payment submission", e);
            throw new RuntimeException("XML payment submission failed: " + e.getMessage(), e);
        }
    }
}
