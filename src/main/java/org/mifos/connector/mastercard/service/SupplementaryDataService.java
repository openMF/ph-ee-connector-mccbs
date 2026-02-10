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
            "SELECT * FROM mastercard_cbs_supplementary_data WHERE payee_msisdn = ?";

    private static final String FIND_BY_ACCOUNT_SQL =
            "SELECT * FROM mastercard_cbs_supplementary_data WHERE payee_account_number = ?";

    public Optional<SupplementaryData> findByMsisdn(String msisdn) {
        try {
            log.debug("Looking up supplementary data for MSISDN: {}", msisdn);

            SupplementaryData data = jdbcTemplate.queryForObject(
                    FIND_BY_MSISDN_SQL,
                    new SupplementaryDataRowMapper(),
                    msisdn
            );

            log.info("Found supplementary data for MSISDN: {}, recipient: {} {}",
                    msisdn, data != null ? data.getRecipientFirstName() : "null",
                    data != null ? data.getRecipientLastName() : "null");

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

            log.info("Found supplementary data for account: {}, recipient: {} {}",
                    accountNumber, data != null ? data.getRecipientFirstName() : "null",
                    data != null ? data.getRecipientLastName() : "null");

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
                    // Static sender information (PHEE-353)
                    .senderOrganisationName(rs.getString("sender_organisation_name"))
                    .senderAddressLine1(rs.getString("sender_address_line1"))
                    .senderAddressCity(rs.getString("sender_address_city"))
                    .senderAddressCountry(rs.getString("sender_address_country"))
                    .paymentOriginationCountry(rs.getString("payment_origination_country"))
                    .destinationCountryIso3(rs.getString("destination_country_iso3"))
                    .beneficiaryCurrency(rs.getString("beneficiary_currency"))
                    .beneficiaryCurrencyDecimalPrecision(rs.getInt("beneficiary_currency_decimal_precision"))
                    .destinationServiceTag(rs.getString("destination_service_tag"))
                    .paymentType(rs.getString("payment_type"))
                    // Variable recipient details (PHEE-353)
                    .recipientFirstName(rs.getString("recipient_first_name"))
                    .recipientLastName(rs.getString("recipient_last_name"))
                    .recipientAddressLine1(rs.getString("recipient_address_line1"))
                    .recipientAddressCountry(rs.getString("recipient_address_country"))
                    .recipientPhone(rs.getString("recipient_phone"))
                    .recipientEmail(rs.getString("recipient_email"))
                    // Bank details (PHEE-353)
                    .bankName(rs.getString("bank_name"))
                    .bankSwiftCode(rs.getString("bank_swift_code"))
                    .bankBranchName(rs.getString("bank_branch_name"))
                    .bankCountryCode(rs.getString("bank_country_code"))
                    // Regulatory
                    .purposeOfPayment(rs.getString("purpose_of_payment"))
                    // Metadata
                    .createdBy(rs.getString("created_by"))
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .build();
        }
    }
}
