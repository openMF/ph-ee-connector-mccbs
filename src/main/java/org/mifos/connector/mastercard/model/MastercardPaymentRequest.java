package org.mifos.connector.mastercard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MastercardPaymentRequest {

    @JsonProperty("partner_id")
    private String partnerId;

    @JsonProperty("transaction_reference")
    private String transactionReference;

    @JsonProperty("payment_type")
    private String paymentType;

    @JsonProperty("amount")
    private AmountInfo amount;

    @JsonProperty("sender")
    private SenderInfo sender;

    @JsonProperty("recipient")
    private RecipientInfo recipient;

    @JsonProperty("purpose_of_payment")
    private String purposeOfPayment;

    @JsonProperty("regulatory_compliance")
    private RegulatoryCompliance regulatoryCompliance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountInfo {
        @JsonProperty("value")
        private String value;

        @JsonProperty("currency")
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SenderInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("address")
        private AddressInfo address;

        @JsonProperty("account")
        private AccountInfo account;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("address")
        private AddressInfo address;

        @JsonProperty("account")
        private AccountInfo account;

        @JsonProperty("bank")
        private BankInfo bank;

        @JsonProperty("tax_id")
        private String taxId;

        @JsonProperty("id_type")
        private String idType;

        @JsonProperty("id_number")
        private String idNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressInfo {
        @JsonProperty("line1")
        private String line1;

        @JsonProperty("line2")
        private String line2;

        @JsonProperty("city")
        private String city;

        @JsonProperty("state")
        private String state;

        @JsonProperty("postal_code")
        private String postalCode;

        @JsonProperty("country")
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountInfo {
        @JsonProperty("number")
        private String number;

        @JsonProperty("type")
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("swift_bic")
        private String swiftBic;

        @JsonProperty("routing_number")
        private String routingNumber;

        @JsonProperty("country")
        private String country;

        @JsonProperty("branch_code")
        private String branchCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryCompliance {
        @JsonProperty("source_of_funds")
        private String sourceOfFunds;

        @JsonProperty("purpose_code")
        private String purposeCode;
    }
}
