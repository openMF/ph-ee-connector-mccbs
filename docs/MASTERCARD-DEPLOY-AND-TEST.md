# Mastercard CBS GovStack - Deployment and Testing Guide

## Quick Reference: Components to Rebuild

| Component | Configuration Changed | Rebuild Required | Restart Pod Required |
|-----------|----------------------|------------------|---------------------|
| **ph-ee-bulk-processor** | ✅ Yes (payment-mode + tenant) | ✅ Yes | ✅ Yes |
| **ph-ee-connector-channel** | ✅ Yes (tenant) | ✅ Yes | ✅ Yes |
| **ph-ee-connector-mccbs** | ✅ Yes (new workers) | ✅ Yes | ✅ Yes |
| **Zeebe** | N/A | ❌ No | ❌ No (just deploy workflow) |

---

## Step-by-Step Deployment Process

### Step 1: Deploy BPMN Workflow to Zeebe

```bash
cd ~/mifos-gazelle

# Deploy the MastercardFundTransfer workflow
./src/utils/deployBpmn-gazelle.sh orchestration/feel/MastercardFundTransfer-DFSPID.bpmn

# Verify deployment
kubectl logs -n paymenthub -l app=zeebe --tail=50 | grep -i "mastercardFund"
```

**Expected output:**
```
Deployed workflow: MastercardFundTransfer-DFSPID (process definition key: ...)
```

---

### Step 2: Rebuild Component JARs

**Prerequisites:** You must be using hostpath mounts for local development. If using image-based deployment, skip to container rebuild section.

#### 2.1 Rebuild Bulk Processor

```bash
cd ~/ph-ee-bulk-processor

# Clean and rebuild (skip tests for speed)
./gradlew clean build -x test

# Verify build success
ls -lh build/libs/ph-ee-bulk-processor-*.jar
```

**What changed:**
- [application.yaml:189-191](/home/tdaly/ph-ee-bulk-processor/src/main/resources/application.yaml#L189-L191) - Payment mode changed to `type: PAYMENT`
- [application.yaml:236-239](/home/tdaly/ph-ee-bulk-processor/src/main/resources/application.yaml#L236-L239) - Added `greenbank-mastercard` tenant

#### 2.2 Rebuild Connector Channel

```bash
cd ~/ph-ee-connector-channel

# Clean and rebuild
./gradlew clean build -x test

# Verify build success
ls -lh build/libs/ph-ee-connector-channel-*.jar
```

**What changed:**
- [application.yml:122-125](/home/tdaly/ph-ee-connector-channel/src/main/resources/application.yml#L122-L125) - Added `greenbank-mastercard` tenant

#### 2.3 Rebuild Mastercard Connector

```bash
cd ~/ph-ee-connector-mccbs

# Clean and rebuild
./gradlew clean build -x test

# Verify build success
ls -lh build/libs/ph-ee-connector-mastercard-cbs-*.jar
```

**What changed:**
- [MastercardCbsWorkers.java:338-544](/home/tdaly/ph-ee-connector-mccbs/src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java#L338-L544) - Added 4 new workers

---

### Step 3: Restart Pods

```bash
# Restart bulk processor
kubectl delete pod -n paymenthub -l app=ph-ee-bulk-processor

# Restart connector channel
kubectl delete pod -n paymenthub -l app=ph-ee-connector-channel

# Restart mastercard connector
kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs

# Wait for all pods to be ready (this may take 2-3 minutes)
echo "Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -n paymenthub -l app=ph-ee-bulk-processor --timeout=180s
kubectl wait --for=condition=ready pod -n paymenthub -l app=ph-ee-connector-channel --timeout=180s
kubectl wait --for=condition=ready pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs --timeout=180s

echo "✓ All pods restarted successfully"
```

---

### Step 4: Verify Worker Registration

```bash
# Check that new workers are registered
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs --tail=100 | grep "Registered worker"
```

**Expected output (should include 4 new workers):**
```
Registered worker: mastercard-lookup-supplemental-data-DFSPID
Registered worker: mastercard-merge-data-DFSPID
Registered worker: mastercard-initiate-payment-DFSPID
Registered worker: mastercard-update-operations-DFSPID
```

**Also verify existing workers are still there:**
```
Registered worker: mastercard-cbs-validate-input
Registered worker: mastercard-cbs-authenticate
Registered worker: mastercard-cbs-match-regulatory-data
Registered worker: mastercard-cbs-initiate-payment
Registered worker: mastercard-cbs-check-status
Registered worker: mastercard-cbs-update-operations
Registered worker: mastercard-cbs-retry-handler
Registered worker: mastercard-cbs-log-error
```

**Total workers:** Should see 12 workers registered (8 original + 4 new)

---

### Step 5: Verify Configuration Loading

#### Check Bulk Processor Configuration

```bash
kubectl logs -n paymenthub -l app=ph-ee-bulk-processor --tail=100 | grep -i "greenbank-mastercard"
```

**Expected:** Should see tenant loading messages

#### Check Connector Channel Configuration

```bash
kubectl logs -n paymenthub -l app=ph-ee-connector-channel --tail=100 | grep -i "greenbank-mastercard"
```

**Expected:** Should see tenant loading messages

---

## Testing the Implementation

### Step 1: Generate Test CSV Files

```bash
cd ~/mifos-gazelle/src/utils/data-loading

# Generate all CSV files including new mastercard file
./generate-example-csv-files.py -c ~/tomconfig.ini

# Or generate mastercard only
./generate-example-csv-files.py -c ~/tomconfig.ini --mode mastercard
```

**Expected output:**
```
=== CSV File Generation ===

Output directory: /home/tdaly/mifos-gazelle/src/utils/data-loading

Querying Mifos for redbank clients...
✓ Found 1 clients in redbank
Querying Mifos for greenbank clients...
✓ Found 1 clients in greenbank
Querying Mifos for bluebank clients...
✓ Found 4 clients in bluebank

✓ Queried Mifos successfully
✓ Generated bulk-gazelle-closedloop-4.csv (4 rows, payer: 0424942603, tenant: redbank)
✓ Generated bulk-gazelle-mojaloop-4.csv (4 rows, payer: 0413509790, tenant: greenbank)
✓ Generated bulk-gazelle-mastercard-4.csv (4 rows, payer: 0413509790, tenant: greenbank-mastercard)
✓ Generated bulk-gazelle-govstack-4.csv (4 rows, GovStack mode)

✓ Generated 4 CSV files

============================================================
CSV files created with 8 transactions:
  CLOSEDLOOP payer: 0424942603 (redbank)
  MOJALOOP payer: 0413509790 (greenbank)
  MASTERCARD_CBS payer: 0413509790 (greenbank-mastercard)
  Payee: Jane Doe (0495822412) - account 000000001
  Payee: John Smith (0410134086) - account 000000002
  ... and 2 more payees
============================================================
```

### Step 2: Verify CSV Contents

```bash
# Check the mastercard CSV
cat bulk-gazelle-mastercard-4.csv
```

**Expected format:**
```csv
id,request_id,payment_mode,payer_identifier_type,payer_identifier,payee_identifier_type,payee_identifier,amount,currency,note
0,uuid-1,MASTERCARD_CBS,MSISDN,0413509790,MSISDN,0495822412,10,USD,Payment to Jane Doe
1,uuid-2,MASTERCARD_CBS,MSISDN,0413509790,MSISDN,0495822412,16,USD,Payment to Jane Doe
2,uuid-3,MASTERCARD_CBS,MSISDN,0413509790,MSISDN,0410134086,10,USD,Payment to John Smith
3,uuid-4,MASTERCARD_CBS,MSISDN,0413509790,MSISDN,0410134086,16,USD,Payment to John Smith
```

---

### Step 3: Submit Test Batch (Non-GovStack Mode)

**Purpose:** Test basic Mastercard CBS flow without identity validation

```bash
cd ~/mifos-gazelle/src/utils/data-loading

# Submit batch to greenbank-mastercard tenant
./submit-batch.py \
  -c ~/tomconfig.ini \
  -f bulk-gazelle-mastercard-4.csv \
  --tenant greenbank-mastercard
```

**Expected output:**
```
Submitting batch to Payment Hub...
✓ Batch submitted successfully
Batch ID: batch-12345
Total transactions: 4
```

**Monitor execution:**
```bash
# Watch connector channel logs
kubectl logs -n paymenthub -l app=ph-ee-connector-channel -f | grep -i "mastercard"

# Watch mastercard connector logs
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -f | grep -i "govstack\|transaction"
```

**Expected log sequence:**
```
[connector-channel] Starting workflow: MastercardFundTransfer-greenbank-mastercard
[mastercard-cbs] [GovStack] Looking up supplemental data for transaction: TXN-001
[mastercard-cbs] [GovStack] Supplemental data found for transaction: TXN-001, beneficiary: Jane Doe
[mastercard-cbs] [GovStack] Merging transfer and supplemental data for transaction: TXN-001
[mastercard-cbs] [GovStack] Data merge successful for transaction: TXN-001
[mastercard-cbs] [GovStack] Initiating CBS payment for transaction: TXN-001
[mastercard-cbs] [GovStack] CBS payment submitted successfully. Transaction: TXN-001, Payment ID: CBS-12345
[mastercard-cbs] [GovStack] Updating operations DB for transaction: TXN-001, success: true
```

---

### Step 4: Submit Test Batch (GovStack Mode)

**Purpose:** Test full GovStack-compliant flow with identity validation and batch de-bulking

#### 4.1 Ensure Supplemental Data is Loaded

```bash
cd ~/mifos-gazelle/src/utils/data-loading

# Load supplemental data if not already loaded
./load-mastercard-supplementary-data.py -c ~/tomconfig.ini

# Verify data exists
kubectl exec -n paymenthub operationsmysql-0 -- mysql -uroot -pmysql operations -e \
  "SELECT COUNT(*) as count FROM mastercard_cbs_supplementary_data"
```

**Expected:** `count > 0`

#### 4.2 Submit GovStack Batch

```bash
./submit-batch.py \
  -c ~/tomconfig.ini \
  -f bulk-gazelle-mastercard-4.csv \
  --tenant greenbank-mastercard \
  --govstack \
  --registering-institution greenbank-mastercard
```

**Monitor execution:**
```bash
# Watch bulk processor (identity validation + de-bulking)
kubectl logs -n paymenthub -l app=ph-ee-bulk-processor -f | grep -i "govstack\|splitting"

# Watch mastercard connector
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -f
```

**Expected log sequence:**
```
[bulk-processor] [GovStack] Starting batch account lookup for batch: batch-12345
[bulk-processor] [GovStack] Calling identity mapper for 4 beneficiaries
[bulk-processor] [GovStack] Identity mapper returned 4 results
[bulk-processor] [GovStack] Splitting batch by payee FSP
[bulk-processor] [GovStack] Created sub-batch for FSP: bluebank (4 transactions)
[connector-channel] Starting workflow: MastercardFundTransfer-greenbank-mastercard
[mastercard-cbs] [GovStack] Looking up supplemental data...
[mastercard-cbs] [GovStack] Supplemental data found...
[mastercard-cbs] [GovStack] Merging transfer and supplemental data...
[mastercard-cbs] [GovStack] CBS payment submitted successfully...
```

---

### Step 5: Verify Results in Database

```bash
# Check batch status
kubectl exec -n paymenthub operationsmysql-0 -- mysql -uroot -pmysql operations_app -e \
  "SELECT batch_id, total, successful, failed, ongoing, note
   FROM batch
   ORDER BY id DESC LIMIT 1"

# Check transfer details
kubectl exec -n paymenthub operationsmysql-0 -- mysql -uroot -pmysql operations_app -e \
  "SELECT id, transaction_id, payee_identifier, payee_dfsp_id, status, status_detail
   FROM transfers
   ORDER BY id DESC LIMIT 5"
```

**Expected results:**
- **Batch:** `total=4, successful=4, failed=0, ongoing=0`
- **Transfers:** `status=COMPLETED, payee_dfsp_id=bluebank` (if GovStack mode with de-bulking)

---

## Troubleshooting

### Problem: Workflow Not Found (412 Error)

**Error:**
```
Process definition not found: MastercardFundTransfer-greenbank-mastercard
```

**Solution:**
```bash
# Redeploy workflow
cd ~/mifos-gazelle
./src/utils/deployBpmn-gazelle.sh orchestration/feel/MastercardFundTransfer-DFSPID.bpmn

# Verify in Zeebe logs
kubectl logs -n paymenthub -l app=zeebe --tail=50 | grep "MastercardFund"
```

---

### Problem: Workers Not Registered

**Error in logs:**
```
No job worker registered for type: mastercard-lookup-supplemental-data-DFSPID
```

**Solution:**
```bash
# Check if connector is running
kubectl get pods -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs

# Check logs for errors
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs --tail=100

# If needed, rebuild and restart
cd ~/ph-ee-connector-mccbs
./gradlew clean build -x test
kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs
```

---

### Problem: Supplemental Data Not Found

**Error in logs:**
```
[GovStack] No supplemental data found for transaction: TXN-001, payee: 0495822412
```

**Solution:**
```bash
# Check if supplemental data table exists
kubectl exec -n paymenthub operationsmysql-0 -- mysql -uroot -pmysql operations -e \
  "SHOW TABLES LIKE 'mastercard_cbs_supplementary_data'"

# Load supplemental data
cd ~/mifos-gazelle/src/utils/data-loading
./load-mastercard-supplementary-data.py -c ~/tomconfig.ini

# Verify data
kubectl exec -n paymenthub operationsmysql-0 -- mysql -uroot -pmysql operations -e \
  "SELECT payee_msisdn, payee_account_number, recipient_first_name
   FROM mastercard_cbs_supplementary_data
   LIMIT 5"
```

---

### Problem: Wrong Workflow Triggered

**Symptom:** Seeing `PayerFundTransfer` instead of `MastercardFundTransfer`

**Cause:** Using wrong tenant

**Solution:**
```bash
# Make sure you use greenbank-mastercard tenant
./submit-batch.py \
  -c ~/tomconfig.ini \
  -f bulk-gazelle-mastercard-4.csv \
  --tenant greenbank-mastercard  # NOT greenbank!
```

---

### Problem: Configuration Not Loading

**Symptom:** New tenant not recognized after rebuild

**Solution:**
```bash
# Verify JAR was rebuilt
ls -lh ~/ph-ee-bulk-processor/build/libs/*.jar
ls -lh ~/ph-ee-connector-channel/build/libs/*.jar

# Make sure you deleted pods (restart not enough with hostpath)
kubectl delete pod -n paymenthub -l app=ph-ee-bulk-processor
kubectl delete pod -n paymenthub -l app=ph-ee-connector-channel

# Wait for pods to come back up
kubectl wait --for=condition=ready pod -n paymenthub -l app=ph-ee-bulk-processor --timeout=180s
kubectl wait --for=condition=ready pod -n paymenthub -l app=ph-ee-connector-channel --timeout=180s
```

---

## Quick Test Summary

**Minimal test (non-GovStack):**
```bash
cd ~/mifos-gazelle

# 1. Deploy workflow
./src/utils/deployBpmn-gazelle.sh orchestration/feel/MastercardFundTransfer-DFSPID.bpmn

# 2. Generate CSV
cd src/utils/data-loading
./generate-example-csv-files.py -c ~/tomconfig.ini --mode mastercard

# 3. Submit batch
./submit-batch.py -c ~/tomconfig.ini -f bulk-gazelle-mastercard-4.csv --tenant greenbank-mastercard

# 4. Check results
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs --tail=50 | grep "GovStack"
```

**Full test (with GovStack):**
```bash
# After minimal test works, add --govstack flag
./submit-batch.py \
  -c ~/tomconfig.ini \
  -f bulk-gazelle-mastercard-4.csv \
  --tenant greenbank-mastercard \
  --govstack \
  --registering-institution greenbank-mastercard
```

---

## Files Modified Summary

| File | Purpose | Status |
|------|---------|--------|
| [MastercardFundTransfer-DFSPID.bpmn](/home/tdaly/mifos-gazelle/orchestration/feel/MastercardFundTransfer-DFSPID.bpmn) | New workflow | ✅ Created |
| [MastercardCbsWorkers.java](/home/tdaly/ph-ee-connector-mccbs/src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java) | New workers | ✅ Modified |
| [bulk-processor/application.yaml](/home/tdaly/ph-ee-bulk-processor/src/main/resources/application.yaml) | Payment mode + tenant | ✅ Modified |
| [connector-channel/application.yml](/home/tdaly/ph-ee-connector-channel/src/main/resources/application.yml) | Tenant config | ✅ Modified |
| [generate-example-csv-files.py](/home/tdaly/mifos-gazelle/src/utils/data-loading/generate-example-csv-files.py) | CSV generation | ✅ Modified |

---

**Last Updated:** January 2026
**Status:** Ready for testing
