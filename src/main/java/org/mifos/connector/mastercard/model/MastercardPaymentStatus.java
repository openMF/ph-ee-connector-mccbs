package org.mifos.connector.mastercard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MastercardPaymentStatus {

    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("transaction_reference")
    private String transactionReference;

    @JsonProperty("amount")
    private MastercardPaymentRequest.AmountInfo amount;

    @JsonProperty("created_timestamp")
    private LocalDateTime createdTimestamp;

    @JsonProperty("completion_timestamp")
    private LocalDateTime completionTimestamp;

    @JsonProperty("recipient_confirmation")
    private String recipientConfirmation;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;
}
