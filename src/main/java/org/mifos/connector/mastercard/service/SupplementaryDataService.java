package org.mifos.connector.mastercard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.model.SupplementaryData;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplementaryDataService {

    private final JdbcTemplate jdbcTemplate;

    private static final String FIND_BY_MSISDN_SQL =
            "SELECT * FROM mastercard_cbs_supplementary_data WHERE payee_msisdn = ? AND is_active = true";

    private static final String FIND_BY_ACCOUNT_SQL =
            "SELECT * FROM mastercard_cbs_supplementary_data WHERE payee_account_number = ? AND is_active = true";

    public Optional<SupplementaryData> findByMsisdn(String msisdn) {
        try {
            log.debug("Looking up supplementary data for MSISDN: {}", msisdn);

            SupplementaryData data = jdbcTemplate.queryForObject(
                    FIND_BY_MSISDN_SQL,
                    new SupplementaryDataRowMapper(),
                    msisdn
            );

            log.info("Found supplementary data for MSISDN: {}, beneficiary: {}",
                    msisdn, data != null ? data.getBeneficiaryFullName() : "null");

            return Optional.ofNullable(data);

        } catch (EmptyResultDataAccessException e) {
            log.warn("No supplementary data found for MSISDN: {}", msisdn);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error querying supplementary data for MSISDN: {}", msisdn, e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public Optional<SupplementaryData> findByAccountNumber(String accountNumber) {
        try {
            log.debug("Looking up supplementary data for account: {}", accountNumber);

            SupplementaryData data = jdbcTemplate.queryForObject(
                    FIND_BY_ACCOUNT_SQL,
                    new SupplementaryDataRowMapper(),
                    accountNumber
            );

            log.info("Found supplementary data for account: {}, beneficiary: {}",
                    accountNumber, data != null ? data.getBeneficiaryFullName() : "null");

            return Optional.ofNullable(data);

        } catch (EmptyResultDataAccessException e) {
            log.warn("No supplementary data found for account: {}", accountNumber);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error querying supplementary data for account: {}", accountNumber, e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    private static class SupplementaryDataRowMapper implements RowMapper<SupplementaryData> {
        @Override
        public SupplementaryData mapRow(ResultSet rs, int rowNum) throws SQLException {
            return SupplementaryData.builder()
                    .id(rs.getLong("id"))
                    .payeeMsisdn(rs.getString("payee_msisdn"))
                    .payeeAccountNumber(rs.getString("payee_account_number"))
                    // Beneficiary details
                    .beneficiaryFullName(rs.getString("beneficiary_full_name"))
                    .beneficiaryFirstName(rs.getString("beneficiary_first_name"))
                    .beneficiaryLastName(rs.getString("beneficiary_last_name"))
                    .beneficiaryAddressLine1(rs.getString("beneficiary_address_line1"))
                    .beneficiaryAddressLine2(rs.getString("beneficiary_address_line2"))
                    .beneficiaryCity(rs.getString("beneficiary_city"))
                    .beneficiaryState(rs.getString("beneficiary_state"))
                    .beneficiaryPostalCode(rs.getString("beneficiary_postal_code"))
                    .beneficiaryCountryCode(rs.getString("beneficiary_country_code"))
                    // Bank details
                    .bankName(rs.getString("bank_name"))
                    .bankBicSwift(rs.getString("bank_bic_swift"))
                    .bankRoutingNumber(rs.getString("bank_routing_number"))
                    .bankCountryCode(rs.getString("bank_country_code"))
                    .bankBranchCode(rs.getString("bank_branch_code"))
                    // Regulatory
                    .purposeOfPayment(rs.getString("purpose_of_payment"))
                    .sourceOfFunds(rs.getString("source_of_funds"))
                    .beneficiaryTaxId(rs.getString("beneficiary_tax_id"))
                    .beneficiaryIdType(rs.getString("beneficiary_id_type"))
                    .beneficiaryIdNumber(rs.getString("beneficiary_id_number"))
                    // Metadata
                    .createdDate(rs.getTimestamp("created_date") != null ?
                            rs.getTimestamp("created_date").toLocalDateTime() : null)
                    .updatedDate(rs.getTimestamp("updated_date") != null ?
                            rs.getTimestamp("updated_date").toLocalDateTime() : null)
                    .isActive(rs.getBoolean("is_active"))
                    .build();
        }
    }
}
