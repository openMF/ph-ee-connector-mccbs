package org.mifos.connector.mastercard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.config.OperationsConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final DataSource dataSource;
    private final OperationsConfig operationsConfig;

    /**
     * Update transfer status in operations database
     *
     * @param transactionId   Transfer transaction ID
     * @param status          Transfer status (COMPLETED, FAILED, etc.)
     * @param externalId      External system reference (e.g., Mastercard payment ID)
     * @param statusDetails   Additional status information
     * @param tenantId        Tenant identifier (database name)
     * @return true if update successful, false otherwise
     */
    public boolean updateTransferStatus(String transactionId, String status,
                                        String externalId, String statusDetails,
                                        String tenantId, String batchId,
                                        BigDecimal amount, String currency,
                                        String payeePartyId, String payeePartyIdType,
                                        String payerPartyId, String payerPartyIdType,
                                        String payeeDfspId, String payerDfspId) {

        if (!operationsConfig.getApi().getEnabled()) {
            log.info("[OperationsDB] Integration disabled - skipping update for transaction: {} (status={}, externalId={})",
                    transactionId, status, externalId);
            return true;
        }

        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("[OperationsDB] ⚠ Tenant ID is null/empty for transaction: {}, skipping database update", transactionId);
            return false;
        }

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            // Build STATUS_DETAIL field: combine statusDetails with Mastercard payment ID
            String statusDetailValue = buildStatusDetail(statusDetails, externalId);

            // Update transfers table in tenant-specific database
            // Use COALESCE so we don't overwrite party fields already set by bulk-processor
            String sql = String.format(
                "UPDATE %s.transfers SET STATUS = ?, STATUS_DETAIL = ?, COMPLETED_AT = NOW(), " +
                "PAYEE_PARTY_ID = COALESCE(PAYEE_PARTY_ID, ?), " +
                "PAYEE_PARTY_ID_TYPE = COALESCE(PAYEE_PARTY_ID_TYPE, ?), " +
                "PAYER_PARTY_ID = COALESCE(PAYER_PARTY_ID, ?), " +
                "PAYER_PARTY_ID_TYPE = COALESCE(PAYER_PARTY_ID_TYPE, ?), " +
                "PAYEE_DFSP_ID = COALESCE(PAYEE_DFSP_ID, ?), " +
                "PAYER_DFSP_ID = COALESCE(PAYER_DFSP_ID, ?) " +
                "WHERE TRANSACTION_ID = ?",
                tenantId
            );

            log.info("[OperationsDB] Updating transfer in database - tenant: {}, transaction: {}, status: {}, externalId: {}, batchId: {}, payee: {}, payer: {}",
                    tenantId, transactionId, status, externalId, batchId, payeePartyId, payerPartyId);

            int rowsUpdated = jdbcTemplate.update(sql, status, statusDetailValue,
                    payeePartyId, payeePartyIdType, payerPartyId, payerPartyIdType,
                    payeeDfspId, payerDfspId, transactionId);

            if (rowsUpdated > 0) {
                log.info("[OperationsDB] ✓ Successfully updated {} row(s) in {}.transfers for transaction: {}",
                        rowsUpdated, tenantId, transactionId);
                return true;
            } else {
                log.warn("[OperationsDB] ⚠ No rows updated for transaction: {} in {}.transfers (transfer may not exist yet)",
                        transactionId, tenantId);
                log.info("[OperationsDB] Attempting to INSERT new transfer record for transaction: {}", transactionId);

                // INSERT new transfer record if UPDATE didn't find existing record
                return insertTransferRecord(jdbcTemplate, transactionId, status, statusDetailValue,
                                           externalId, tenantId, batchId, amount, currency,
                                           payeePartyId, payeePartyIdType, payerPartyId, payerPartyIdType,
                                           payeeDfspId, payerDfspId);
            }

        } catch (org.springframework.jdbc.CannotGetJdbcConnectionException e) {
            // Database connection error
            log.warn("[OperationsDB] ⚠ Cannot connect to operations database for transaction: {} (Connection error: {}). " +
                    "Payment completed successfully, but status not recorded in operations DB.",
                    transactionId, e.getMessage());
            log.info("[OperationsDB] Payment details - status: {}, externalId: {}, statusDetails: {}",
                    status, externalId, statusDetails);
            return true; // Return true to not block the workflow
        } catch (org.springframework.dao.DataAccessException e) {
            // Database/SQL error
            log.error("[OperationsDB] ✗ Database error updating transaction: {} in tenant: {}, error: {}",
                    transactionId, tenantId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[OperationsDB] ✗ Unexpected error updating transaction: {} in tenant: {}",
                    transactionId, tenantId, e);
            return false;
        }
    }

    /**
     * Insert a new transfer record if it doesn't exist
     * This handles cases where Mastercard connector is called directly without prior transfer record creation
     */
    private boolean insertTransferRecord(JdbcTemplate jdbcTemplate, String transactionId,
                                        String status, String statusDetailValue,
                                        String externalId, String tenantId,
                                        String batchId, BigDecimal amount, String currency,
                                        String payeePartyId, String payeePartyIdType,
                                        String payerPartyId, String payerPartyIdType,
                                        String payeeDfspId, String payerDfspId) {
        try {
            String insertSql = String.format(
                "INSERT INTO %s.transfers (TRANSACTION_ID, STATUS, STATUS_DETAIL, " +
                "BATCH_ID, AMOUNT, CURRENCY, STARTED_AT, COMPLETED_AT, DIRECTION, " +
                "PAYEE_PARTY_ID, PAYEE_PARTY_ID_TYPE, PAYER_PARTY_ID, PAYER_PARTY_ID_TYPE, " +
                "PAYEE_DFSP_ID, PAYER_DFSP_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 'OUTBOUND', ?, ?, ?, ?, ?, ?)",
                tenantId
            );

            Long amountLong = (amount != null) ? amount.longValue() : null;

            log.debug("[OperationsDB] INSERT SQL: {} with params: [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}]",
                    insertSql, transactionId, status, statusDetailValue, batchId, amountLong, currency,
                    payeePartyId, payeePartyIdType, payerPartyId, payerPartyIdType, payeeDfspId, payerDfspId);

            int rowsInserted = jdbcTemplate.update(insertSql, transactionId, status,
                                                  statusDetailValue, batchId, amountLong, currency,
                                                  payeePartyId, payeePartyIdType, payerPartyId, payerPartyIdType,
                                                  payeeDfspId, payerDfspId);

            if (rowsInserted > 0) {
                log.info("[OperationsDB] ✓ Successfully inserted new transfer record in {}.transfers for transaction: {}, externalId: {}",
                        tenantId, transactionId, externalId);
                return true;
            } else {
                log.error("[OperationsDB] ✗ Failed to insert transfer record for transaction: {} (0 rows inserted)",
                        transactionId);
                return false;
            }

        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Race condition - another process created the record between UPDATE and INSERT
            log.info("[OperationsDB] Transfer record already exists for transaction: {} (concurrent insert detected), retrying UPDATE",
                    transactionId);

            // Retry UPDATE since record now exists
            String updateSql = String.format(
                "UPDATE %s.transfers SET STATUS = ?, STATUS_DETAIL = ?, COMPLETED_AT = NOW(), " +
                "BATCH_ID = COALESCE(BATCH_ID, ?), AMOUNT = COALESCE(AMOUNT, ?), CURRENCY = COALESCE(CURRENCY, ?), " +
                "PAYEE_PARTY_ID = COALESCE(PAYEE_PARTY_ID, ?), PAYEE_PARTY_ID_TYPE = COALESCE(PAYEE_PARTY_ID_TYPE, ?), " +
                "PAYER_PARTY_ID = COALESCE(PAYER_PARTY_ID, ?), PAYER_PARTY_ID_TYPE = COALESCE(PAYER_PARTY_ID_TYPE, ?), " +
                "PAYEE_DFSP_ID = COALESCE(PAYEE_DFSP_ID, ?), PAYER_DFSP_ID = COALESCE(PAYER_DFSP_ID, ?) " +
                "WHERE TRANSACTION_ID = ?",
                tenantId
            );

            Long amountLong = (amount != null) ? amount.longValue() : null;
            int rowsUpdated = jdbcTemplate.update(updateSql, status, statusDetailValue,
                    batchId, amountLong, currency,
                    payeePartyId, payeePartyIdType, payerPartyId, payerPartyIdType,
                    payeeDfspId, payerDfspId, transactionId);

            if (rowsUpdated > 0) {
                log.info("[OperationsDB] ✓ Successfully updated transfer on retry for transaction: {}", transactionId);
                return true;
            } else {
                log.warn("[OperationsDB] ⚠ Retry UPDATE failed for transaction: {}", transactionId);
                return true; // Return true to not block workflow
            }

        } catch (Exception e) {
            log.error("[OperationsDB] ✗ Error inserting transfer record for transaction: {} in tenant: {}, error: {}",
                    transactionId, tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * Build STATUS_DETAIL field combining status details and Mastercard payment ID
     */
    private String buildStatusDetail(String statusDetails, String externalId) {
        if (statusDetails != null && externalId != null) {
            return String.format("%s | Mastercard Payment ID: %s", statusDetails, externalId);
        } else if (externalId != null) {
            return String.format("Mastercard Payment ID: %s", externalId);
        } else if (statusDetails != null) {
            return statusDetails;
        } else {
            return "Payment processed via Mastercard CBS";
        }
    }
}
