# Mastercard CBS - Integration Quickstart

## Setup for Mifos-Gazelle Integration

This guide shows how to integrate the Mastercard CBS connector with your existing mifos-gazelle deployment.

---

## Step 1: Create Mastercard Branch in Mifos-Gazelle

```bash
cd ~/mifos-gazelle
git checkout -b mastercard
```

---

## Step 2: Link CBS Connector

```bash
# Option 1: Symlink (if keeping separate repos)
cd ~/mifos-gazelle/repos
ln -s ~/ph-ee-connector-mccbs ph-ee-connector-mccbs

# Option 2: Copy scripts to mifos-gazelle
cp ~/ph-ee-connector-mccbs/src/utils/data-loading/*.py \
   ~/mifos-gazelle/src/utils/data-loading/
```

---

## Step 3: Load Database Schema

```bash
# Connect to your operations database
mysql -h operationsmysql.paymenthub.svc.cluster.local \
  -u root -p mysql \
  operations < ~/ph-ee-connector-mccbs/src/utils/data-loading/mastercard-cbs-schema-v2.sql

# Verify table created
mysql -h operationsmysql.paymenthub.svc.cluster.local \
  -u root -p mysql \
  -e "SHOW TABLES FROM operations LIKE 'mastercard%';"
```

**Expected output:**
```
+----------------------------------------------+
| Tables_in_operations (mastercard%)           |
+----------------------------------------------+
| mastercard_cbs_supplementary_data            |
+----------------------------------------------+
```

---

## Step 4: Populate Identity Account Mapper (If Not Done)

```bash
cd ~/mifos-gazelle/src/utils/data-loading

# This populates identity_account_mapper with MSISDNs from MifosX
./generate-mifos-vnext-data.py --regenerate -c ~/tomconfig.ini
```

**Verify:**
```bash
mysql -h operationsmysql.paymenthub.svc.cluster.local \
  -u root -p mysql \
  -e "SELECT COUNT(*) FROM identity_account_mapper.identity_details WHERE payee_identity_type='MSISDN';"
```

---

## Step 5: Load Mastercard Supplementary Data

```bash
cd ~/mifos-gazelle/src/utils/data-loading

# Or if you copied scripts:
cd ~/mifos-gazelle/src/utils/data-loading

./load-mastercard-supplementary-data.py -c ~/tomconfig.ini
```

**Expected output:**
```
======================================================================
Mastercard CBS Supplementary Data Loader
======================================================================

Loading config from: /home/tdaly/tomconfig.ini
Connecting to databases...

Querying identity_account_mapper...
Found 10 beneficiaries in identity_account_mapper

Generating supplementary data...
  ✓ 0495822412 → John Doe (US - JPMorgan Chase Bank)
  ✓ 0424942603 → Emma Wilson (GB - Barclays Bank)
  ✓ 0413356886 → Carlos Rodriguez (ES - Banco Santander)
  ...

======================================================================
✅ Successfully loaded 10 supplementary data records
======================================================================
```

**Verify:**
```bash
mysql -h operationsmysql.paymenthub.svc.cluster.local \
  -u root -p mysql \
  -e "SELECT
        payee_msisdn,
        recipient_first_name,
        recipient_last_name,
        recipient_address_country,
        bank_name
      FROM operations.mastercard_cbs_supplementary_data
      LIMIT 5;"
```

---

## Step 6: Deploy CBS Connector

### Option A: Using Existing PaymentHub Deployment

Add to `config/ph_values.yaml`:

```yaml
# Add CBS Connector
ph-ee-connector-mastercard-cbs:
  enabled: true
  image:
    repository: ph-ee-connector-mastercard-cbs
    tag: 1.0.0
  env:
    ZEEBE_BROKER_CONTACTPOINT: zeebe-gateway:26500
    MASTERCARD_API_URL: http://mastercard-simulator.mastercard-simulator.svc.cluster.local:8080
    DATASOURCE_URL: jdbc:mysql://operationsmysql:3306/operations
    DATASOURCE_USERNAME: root
    DATASOURCE_PASSWORD: mysql
```

### Option B: Manual Kubernetes Deployment

```bash
# Build connector image
cd ~/ph-ee-connector-mccbs
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .

# Deploy to PaymentHub namespace
kubectl create deployment ph-ee-connector-mastercard-cbs \
  --image=ph-ee-connector-mastercard-cbs:1.0.0 \
  -n paymenthub

# Set environment variables
kubectl set env deployment/ph-ee-connector-mastercard-cbs \
  ZEEBE_BROKER_CONTACTPOINT=zeebe-gateway:26500 \
  MASTERCARD_API_URL=http://mastercard-simulator:8080 \
  DATASOURCE_URL=jdbc:mysql://operationsmysql:3306/operations \
  DATASOURCE_USERNAME=root \
  DATASOURCE_PASSWORD=mysql \
  -n paymenthub

# Verify deployment
kubectl get pods -n paymenthub | grep mastercard-cbs
kubectl logs -n paymenthub -l app=ph-ee-connector-mastercard-cbs --tail=20
```

**Expected log output:**
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

---

## Step 7: Deploy BPMN Workflow

```bash
# Install zbctl if not already installed
# brew install zeebe-io/tap/zbctl  (macOS)
# or download from https://github.com/camunda-cloud/zeebe/releases

# Deploy workflow
zbctl deploy ~/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn \
  --address zeebe-gateway.paymenthub.svc.cluster.local:26500

# Or port-forward and deploy locally
kubectl port-forward -n paymenthub svc/zeebe-gateway 26500:26500 &
zbctl deploy ~/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn
```

**Verify:**
```bash
zbctl list workflows | grep mastercard
```

---

## Step 8: Configure Payment Mode in Bulk Processor

### Option 1: Using HostPath Mounts (Local Dev)

Edit `~/ph-ee-bulk-processor/src/main/resources/application.yaml`:

```yaml
payment-modes:
  # Existing modes...
  - id: "MOJALOOP"
    type: "BULK"
    endpoint: "bulk_connector_mojaloop-{dfspid}"
  - id: "CLOSEDLOOP"
    type: "BULK"
    endpoint: "bulk_connector_closedloop-{dfspid}"

  # ADD THIS:
  - id: "MASTERCARD_CBS"
    type: "BULK"
    endpoint: "bulk_connector_mastercard_cbs-{dfspid}"
```

Rebuild and restart:
```bash
cd ~/ph-ee-bulk-processor
./gradlew clean build -x test
kubectl delete pod -n paymenthub -l app=ph-ee-bulk-processor
```

### Option 2: Using ConfigMap

```bash
kubectl edit configmap ph-ee-bulk-processor-config -n paymenthub

# Add to payment-modes section:
# - id: "MASTERCARD_CBS"
#   type: "BULK"
#   endpoint: "bulk_connector_mastercard_cbs-{dfspid}"

# Restart pod
kubectl delete pod -n paymenthub -l app=ph-ee-bulk-processor
```

---

## Step 9: Generate Test Batch

```bash
cd ~/mifos-gazelle/src/utils/data-loading

./generate-mastercard-batch.py -c ~/tomconfig.ini --count 10
```

**Expected output:**
```
======================================================================
Mastercard CBS Batch CSV Generator
======================================================================

Loading config from: /home/tdaly/tomconfig.ini
Connecting to operations database...
Querying 10 payees from supplementary data...
Generating CSV: bulk-mastercard-cbs.csv

======================================================================
✅ Generated batch CSV with 10 payments
   File: bulk-mastercard-cbs.csv
======================================================================

Payees included:
  • 0495822412 - John Doe (US) - JPMorgan Chase Bank
  • 0424942603 - Emma Wilson (GB) - Barclays Bank
  • 0413356886 - Carlos Rodriguez (ES) - Banco Santander
  ...
```

---

## Step 10: Submit Test Batch

```bash
cd ~/mifos-gazelle/src/utils/data-loading

./submit-batch.py \
  -c ~/tomconfig.ini \
  -f bulk-mastercard-cbs.csv \
  --tenant greenbank \
  --payment-mode MASTERCARD_CBS
```

**Expected output:**
```
Submitting batch to PaymentHub...
✅ Batch submitted successfully
   Batch ID: batch-20260124-001
```

---

## Step 11: Monitor Batch Progress

### Check Bulk Processor Logs
```bash
kubectl logs -n paymenthub -l app=ph-ee-bulk-processor --tail=50 -f
```

Look for:
- `Processing batch: batch-20260124-001`
- `Payment mode: MASTERCARD_CBS`
- `Starting workflow: bulk_connector_mastercard_cbs-greenbank`

### Check CBS Connector Logs
```bash
kubectl logs -n paymenthub -l app=ph-ee-connector-mastercard-cbs --tail=100 -f
```

Look for each worker executing:
1. `Validating input for transaction...`
2. `Authenticating with Mastercard CBS...`
3. `Matching regulatory data for MSISDN...`
4. `Initiating CBS payment...`
5. `Checking payment status...`
6. `Updating operations database...`

### Check Database Status
```bash
# Get batch ID from logs or submit-batch output
BATCH_ID="batch-20260124-001"

# Check batch summary
kubectl exec -n paymenthub operationsmysql-0 -- \
  mysql -uroot -pmysql operations_app -e \
  "SELECT batch_id, total, successful, failed, ongoing
   FROM batch WHERE batch_id = '${BATCH_ID}';"
```

**Expected:**
```
+---------------------+-------+------------+--------+---------+
| batch_id            | total | successful | failed | ongoing |
+---------------------+-------+------------+--------+---------+
| batch-20260124-001  |    10 |         10 |      0 |       0 |
+---------------------+-------+------------+--------+---------+
```

### Check Individual Transfers
```bash
kubectl exec -n paymenthub operationsmysql-0 -- \
  mysql -uroot -pmysql operations_app -e \
  "SELECT
     transaction_id,
     payee_party_id,
     amount,
     currency,
     status,
     error_information
   FROM transfers
   WHERE batch_id = '${BATCH_ID}'
   ORDER BY transaction_id
   LIMIT 5;"
```

---

## Troubleshooting

### Issue: No workers registered

**Check:**
```bash
kubectl logs -n paymenthub -l app=ph-ee-connector-mastercard-cbs | grep "Registered worker"
```

**Fix:** Verify Zeebe connection
```bash
kubectl logs -n paymenthub -l app=ph-ee-connector-mastercard-cbs | grep zeebe
```

### Issue: Supplementary data not found

**Check:**
```bash
mysql -h operationsmysql.paymenthub.svc.cluster.local -u root -p mysql -e \
  "SELECT COUNT(*) FROM operations.mastercard_cbs_supplementary_data WHERE is_active=TRUE;"
```

**Fix:** Re-run data loading script
```bash
./load-mastercard-supplementary-data.py -c ~/tomconfig.ini --regenerate
```

### Issue: Batch shows 0 transactions

**Check:** Payment mode configured
```bash
kubectl logs -n paymenthub -l app=ph-ee-bulk-processor | grep "Payment mode.*MASTERCARD"
```

**Fix:** Verify payment-modes configuration in bulk-processor

### Issue: Authentication failed

**Check:** CBS connector can reach API
```bash
kubectl exec -n paymenthub deployment/ph-ee-connector-mastercard-cbs -- \
  curl -v http://mastercard-simulator:8080/oauth/token
```

**Fix:** Verify API endpoint configuration

---

## Comparison with Other Payment Modes

| Feature | Mojaloop | Closedloop | Mastercard CBS |
|---------|----------|------------|----------------|
| **Switch** | Mojaloop vNext | None (direct) | Mastercard CBS |
| **Payer Tenant** | greenbank | redbank | greenbank |
| **Payment Mode** | MOJALOOP | CLOSEDLOOP | MASTERCARD_CBS |
| **Destination** | FSP IDs | Internal accounts | International banks |
| **Currency** | Multi-currency | Multi-currency | ZAR (fixed) |
| **Workflow** | PayerFundTransfer-{dfspid} | minimal_mock-{dfspid} | bulk_connector_mastercard_cbs-{dfspid} |
| **Data Needed** | Oracle registration | None extra | Supplementary data |

---

## Next Steps

### For Development
1. **Enable HostPath Mounts** for faster iteration
2. **Add Mock Mastercard API** for testing without sandbox
3. **Implement XML Support** (see JIRA_REQUIREMENTS_ANALYSIS.md)

### For Production
1. **Get Mastercard Credentials** (partner ID, OAuth credentials)
2. **Update to XML API Format** (add JAXB support)
3. **Connect to Real Sandbox** (https://sandbox.api.mastercard.com)
4. **Add Monitoring** (Prometheus metrics, alerting)
5. **Implement Status Polling** (retrieve payment status API)

---

## Quick Reference Commands

```bash
# Full setup from scratch
cd ~/mifos-gazelle
git checkout -b mastercard
./run.sh

cd ~/mifos-gazelle/src/utils/data-loading
./generate-mifos-vnext-data.py --regenerate -c ~/tomconfig.ini
./load-mastercard-supplementary-data.py -c ~/tomconfig.ini
./generate-mastercard-batch.py -c ~/tomconfig.ini
./submit-batch.py -c ~/tomconfig.ini -f bulk-mastercard-cbs.csv --tenant greenbank --payment-mode MASTERCARD_CBS

# Check status
kubectl get pods -n paymenthub | grep mastercard
kubectl logs -n paymenthub -l app=ph-ee-connector-mastercard-cbs --tail=50

# Query database
kubectl exec -n paymenthub operationsmysql-0 -- mysql -uroot -pmysql operations_app -e \
  "SELECT batch_id, total, successful, failed FROM batch ORDER BY id DESC LIMIT 3;"
```

---

**Document Created**: January 24, 2026
**Status**: Ready for integration testing
**Next**: Deploy to test environment and run end-to-end tests
