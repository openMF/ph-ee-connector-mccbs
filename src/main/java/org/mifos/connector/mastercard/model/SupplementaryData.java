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

    @JsonProperty("payee_msisdn")
    private String payeeMsisdn;

    @JsonProperty("payee_account_number")
    private String payeeAccountNumber;

    // Beneficiary Details
    @JsonProperty("beneficiary_full_name")
    private String beneficiaryFullName;

    @JsonProperty("beneficiary_first_name")
    private String beneficiaryFirstName;

    @JsonProperty("beneficiary_last_name")
    private String beneficiaryLastName;

    @JsonProperty("beneficiary_address_line1")
    private String beneficiaryAddressLine1;

    @JsonProperty("beneficiary_address_line2")
    private String beneficiaryAddressLine2;

    @JsonProperty("beneficiary_city")
    private String beneficiaryCity;

    @JsonProperty("beneficiary_state")
    private String beneficiaryState;

    @JsonProperty("beneficiary_postal_code")
    private String beneficiaryPostalCode;

    @JsonProperty("beneficiary_country_code")
    private String beneficiaryCountryCode;

    // Bank Details
    @JsonProperty("bank_name")
    private String bankName;

    @JsonProperty("bank_bic_swift")
    private String bankBicSwift;

    @JsonProperty("bank_routing_number")
    private String bankRoutingNumber;

    @JsonProperty("bank_country_code")
    private String bankCountryCode;

    @JsonProperty("bank_branch_code")
    private String bankBranchCode;

    // Regulatory/Compliance
    @JsonProperty("purpose_of_payment")
    private String purposeOfPayment;

    @JsonProperty("source_of_funds")
    private String sourceOfFunds;

    @JsonProperty("beneficiary_tax_id")
    private String beneficiaryTaxId;

    @JsonProperty("beneficiary_id_type")
    private String beneficiaryIdType;

    @JsonProperty("beneficiary_id_number")
    private String beneficiaryIdNumber;

    // Metadata
    @JsonProperty("created_date")
    private LocalDateTime createdDate;

    @JsonProperty("updated_date")
    private LocalDateTime updatedDate;

    @JsonProperty("is_active")
    private Boolean isActive;
}
