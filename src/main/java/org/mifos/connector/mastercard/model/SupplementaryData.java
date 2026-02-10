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
public class SupplementaryData {

    private Long id;

    // Lookup Keys (from identity_account_mapper)
    @JsonProperty("payee_msisdn")
    private String payeeMsisdn;

    @JsonProperty("payee_account_number")
    private String payeeAccountNumber;

    // Static Sender Information (PHEE-353 - table defaults)
    @JsonProperty("sender_organisation_name")
    private String senderOrganisationName;

    @JsonProperty("sender_address_line1")
    private String senderAddressLine1;

    @JsonProperty("sender_address_city")
    private String senderAddressCity;

    @JsonProperty("sender_address_country")
    private String senderAddressCountry;

    @JsonProperty("sender_id_number")
    private String senderIdNumber;

    @JsonProperty("payment_origination_country")
    private String paymentOriginationCountry;

    @JsonProperty("destination_country_iso3")
    private String destinationCountryIso3;

    @JsonProperty("beneficiary_currency")
    private String beneficiaryCurrency;

    @JsonProperty("beneficiary_currency_decimal_precision")
    private Integer beneficiaryCurrencyDecimalPrecision;

    @JsonProperty("destination_service_tag")
    private String destinationServiceTag;

    @JsonProperty("payment_type")
    private String paymentType;

    // Variable Recipient Details (PHEE-353 - per beneficiary)
    @JsonProperty("recipient_first_name")
    private String recipientFirstName;

    @JsonProperty("recipient_last_name")
    private String recipientLastName;

    @JsonProperty("recipient_id_type")
    private String recipientIdType;

    @JsonProperty("recipient_id_number")
    private String recipientIdNumber;

    @JsonProperty("recipient_address_line1")
    private String recipientAddressLine1;

    @JsonProperty("recipient_address_city")
    private String recipientAddressCity;

    @JsonProperty("recipient_address_country")
    private String recipientAddressCountry;

    @JsonProperty("recipient_postal_code")
    private String recipientPostalCode;

    @JsonProperty("recipient_phone")
    private String recipientPhone;

    @JsonProperty("recipient_email")
    private String recipientEmail;

    // Bank Details (PHEE-353)
    @JsonProperty("bank_name")
    private String bankName;

    @JsonProperty("bank_swift_code")
    private String bankSwiftCode;

    @JsonProperty("bank_branch_name")
    private String bankBranchName;

    @JsonProperty("bank_address")
    private String bankAddress;

    @JsonProperty("bank_country_code")
    private String bankCountryCode;

    // Regulatory/Compliance
    @JsonProperty("purpose_of_payment")
    private String purposeOfPayment;

    // Metadata
    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
