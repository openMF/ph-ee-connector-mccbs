package org.mifos.connector.mastercard.config;

import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.Variable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mifos.connector.mastercard.zeebe.MastercardCbsWorkers;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Multi-tenant worker registration for Mastercard CBS
 * Registers workers for greenbank, redbank, and bluebank tenants
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTenantWorkerConfig {

    private final MastercardCbsWorkers workers;

    // Greenbank tenant workers
    @JobWorker(type = "mastercard-lookup-supplemental-data-greenbank", autoComplete = true)
    public Map<String, Object> lookupSupplementalDataGreenbank(
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "transactionId") String transactionId) {
        return workers.lookupSupplementalDataFromChannelRequest(channelRequest, transactionId);
    }

    @JobWorker(type = "mastercard-merge-data-greenbank", autoComplete = true, fetchAllVariables = true)
    public Map<String, Object> mergeDataGreenbank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {
        log.debug("[DEBUG] Merge worker for greenbank - transactionId: {}, suppDataMap: {}", transactionId, suppDataMap);
        return workers.mergeData(transactionId, channelRequest, suppDataMap);
    }

    @JobWorker(type = "mastercard-initiate-payment-greenbank", autoComplete = true, fetchAllVariables = true)
    public Map<String, Object> initiatePaymentGreenbank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {
        return workers.initiatePaymentGovStack(transactionId, mergedPaymentData, suppDataMap);
    }

    @JobWorker(type = "mastercard-update-operations-greenbank", autoComplete = true)
    public Map<String, Object> updateOperationsGreenbank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "cbsPaymentId") String cbsPaymentId,
            @Variable(name = "cbsPaymentStatus") String cbsPaymentStatus,
            @Variable(name = "paymentSuccess") Boolean paymentSuccess,
            @Variable(name = "batchId") String batchId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData) {
        return workers.updateOperationsGovStack(transactionId, cbsPaymentId, cbsPaymentStatus, paymentSuccess, batchId, mergedPaymentData, "greenbank");
    }

    // Redbank tenant workers
    @JobWorker(type = "mastercard-lookup-supplemental-data-redbank", autoComplete = true)
    public Map<String, Object> lookupSupplementalDataRedbank(
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "transactionId") String transactionId) {
        return workers.lookupSupplementalDataFromChannelRequest(channelRequest, transactionId);
    }

    @JobWorker(type = "mastercard-merge-data-redbank", autoComplete = true, fetchAllVariables = true)
    public Map<String, Object> mergeDataRedbank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {
        return workers.mergeData(transactionId, channelRequest, suppDataMap);
    }

    @JobWorker(type = "mastercard-initiate-payment-redbank", autoComplete = true, fetchAllVariables = true)
    public Map<String, Object> initiatePaymentRedbank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {
        return workers.initiatePaymentGovStack(transactionId, mergedPaymentData, suppDataMap);
    }

    @JobWorker(type = "mastercard-update-operations-redbank", autoComplete = true)
    public Map<String, Object> updateOperationsRedbank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "cbsPaymentId") String cbsPaymentId,
            @Variable(name = "cbsPaymentStatus") String cbsPaymentStatus,
            @Variable(name = "paymentSuccess") Boolean paymentSuccess,
            @Variable(name = "batchId") String batchId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData) {
        return workers.updateOperationsGovStack(transactionId, cbsPaymentId, cbsPaymentStatus, paymentSuccess, batchId, mergedPaymentData, "redbank");
    }

    // Bluebank tenant workers
    @JobWorker(type = "mastercard-lookup-supplemental-data-bluebank", autoComplete = true)
    public Map<String, Object> lookupSupplementalDataBluebank(
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "transactionId") String transactionId) {
        return workers.lookupSupplementalDataFromChannelRequest(channelRequest, transactionId);
    }

    @JobWorker(type = "mastercard-merge-data-bluebank", autoComplete = true, fetchAllVariables = true)
    public Map<String, Object> mergeDataBluebank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "channelRequest") String channelRequest,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {
        return workers.mergeData(transactionId, channelRequest, suppDataMap);
    }

    @JobWorker(type = "mastercard-initiate-payment-bluebank", autoComplete = true, fetchAllVariables = true)
    public Map<String, Object> initiatePaymentBluebank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData,
            @Variable(name = "supplementaryData") Map<String, Object> suppDataMap) {
        return workers.initiatePaymentGovStack(transactionId, mergedPaymentData, suppDataMap);
    }

    @JobWorker(type = "mastercard-update-operations-bluebank", autoComplete = true)
    public Map<String, Object> updateOperationsBluebank(
            @Variable(name = "transactionId") String transactionId,
            @Variable(name = "cbsPaymentId") String cbsPaymentId,
            @Variable(name = "cbsPaymentStatus") String cbsPaymentStatus,
            @Variable(name = "paymentSuccess") Boolean paymentSuccess,
            @Variable(name = "batchId") String batchId,
            @Variable(name = "mergedPaymentData") Map<String, Object> mergedPaymentData) {
        return workers.updateOperationsGovStack(transactionId, cbsPaymentId, cbsPaymentStatus, paymentSuccess, batchId, mergedPaymentData, "bluebank");
    }
}
