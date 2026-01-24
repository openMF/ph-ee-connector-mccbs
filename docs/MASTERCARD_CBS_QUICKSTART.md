# Mastercard CBS Demo - Quick Start Guide

## Overview

This guide provides step-by-step instructions to implement and test the Mastercard CBS demo connector with PaymentHub EE.

## Architecture Summary

```
GovStack API → PaymentHub Operations → Identity Mapper → Bulk Processor → CBS Connector → Mock Mastercard API
                                                                                ↓
                                                                    Supplementary Data Service
```

## Prerequisites

- PaymentHub EE deployed and running
- MySQL database access (operations DB)
- Kubernetes cluster access
- Maven 3.8+
- Java 17+
- Python 3.8+ (for data loading scripts)

## Implementation Components

### Component 1: Mock Mastercard CBS API Simulator

**Purpose**: Simulates Mastercard Cross-Border Services API for demo purposes

**Location**: `repos/mastercard-cbs-simulator/`

**Key Features**:
- OAuth 2.0 token endpoint
- Payment submission endpoint
- Payment status retrieval endpoint
- In-memory payment storage
- Automatic status transitions (PENDING → PROCESSING → COMPLETED)

**Endpoints**:
- `POST /oauth/token` - Get OAuth access token
- `POST /send/v1/partners/transfer` - Submit payment
- `GET /send/v1/partners/transfer/{id}` - Get payment status

**Deploy**:
```bash
cd repos/mastercard-cbs-simulator
mvn clean package
docker build -t mastercard-cbs-simulator:1.0.0 .
kubectl apply -f k8s/deployment.yaml
```

### Component 2: Mastercard CBS Connector

**Purpose**: PaymentHub connector that integrates with Mastercard CBS

**Location**: `repos/ph-ee-connector-mastercard-cbs/`

**Key Features**:
- Zeebe workers for CBS workflow steps
- OAuth authentication with Mastercard
- Supplementary data lookup from MySQL
- Payment submission and status tracking
- Error handling and retry logic

**Zeebe Workers**:
1. `mastercard-cbs-authenticate` - Get OAuth token
2. `mastercard-cbs-match-regulatory-data` - Lookup supplementary data
3. `mastercard-cbs-initiate-payment` - Submit payment to CBS
4. `mastercard-cbs-check-status` - Retrieve payment status
5. `mastercard-cbs-update-operations` - Update Operations DB

**Deploy**:
```bash
cd repos/ph-ee-connector-mastercard-cbs
mvn clean package
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .
kubectl apply -f k8s/deployment.yaml
```

### Component 3: Supplementary Data Database

**Purpose**: Store regulatory/compliance data for CBS payments

**Table**: `mastercard_cbs_supplementary_data` in operations DB

**Schema**:
```sql
CREATE TABLE mastercard_cbs_supplementary_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payee_msisdn VARCHAR(20) UNIQUE NOT NULL,
    payee_account_number VARCHAR(50) NOT NULL,

    -- Beneficiary Details
    beneficiary_full_name VARCHAR(255) NOT NULL,
    beneficiary_first_name VARCHAR(100),
    beneficiary_last_name VARCHAR(100),
    beneficiary_address_line1 VARCHAR(255),
    beneficiary_address_line2 VARCHAR(255),
    beneficiary_city VARCHAR(100),
    beneficiary_state VARCHAR(100),
    beneficiary_postal_code VARCHAR(20),
    beneficiary_country_code CHAR(2) NOT NULL,

    -- Bank Details
    bank_name VARCHAR(255) NOT NULL,
    bank_bic_swift VARCHAR(11),
    bank_routing_number VARCHAR(50),
    bank_country_code CHAR(2) NOT NULL,
    bank_branch_code VARCHAR(50),

    -- Regulatory/Compliance
    purpose_of_payment VARCHAR(255) DEFAULT 'Government disbursement',
    source_of_funds VARCHAR(100) DEFAULT 'GOVERNMENT',
    beneficiary_tax_id VARCHAR(50),
    beneficiary_id_type VARCHAR(50),
    beneficiary_id_number VARCHAR(100),

    -- Metadata
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    INDEX idx_msisdn (payee_msisdn),
    INDEX idx_account (payee_account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Load Data**:
```bash
cd src/utils/data-loading
python3 load-mastercard-cbs-supplementary-data.py --config ~/tomconfig.ini
```

### Component 4: BPMN Workflow

**File**: `orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn`

**Workflow**: `bulk_connector_mastercard_cbs-{tenant}`

**Key Tasks**:
1. Start → Validate Input
2. Authenticate with CBS
3. Match Supplementary Data
4. Initiate CBS Payment
5. Wait for Completion (async)
6. Retrieve Payment Status
7. Update Operations DB
8. Complete

**Deploy Workflow**:
```bash
zbctl deploy orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn \
  --address ph-ee-zeebe-gateway:26500
```

### Component 5: Identity Account Mapper Pre-population

**Purpose**: Map MSISDNs to account numbers for the 10 demo payees

**Load Data**:
```bash
cd src/utils/data-loading
python3 load-identity-mapper-cbs-demo.py --config ~/tomconfig.ini
```

**Verification**:
```sql
USE identity_account_mapper;
SELECT
    id.payee_identity,
    pm.destination_account,
    pm.institution_code
FROM identity_details id
JOIN payment_modality_details pm ON id.master_id = pm.master_id
WHERE pm.institution_code = 'MASTERCARD_CBS';
```

## Step-by-Step Setup

### Step 1: Create Supplementary Data Table

```bash
mysql -h <mysql-host> -u root -p operations < src/utils/data-loading/mastercard-cbs-schema.sql
```

### Step 2: Load Demo Data (10 Payees)

```bash
cd src/utils/data-loading
python3 load-mastercard-cbs-supplementary-data.py \
  --config ~/tomconfig.ini \
  --demo-data ./mastercard-cbs-demo-payees.csv
```

**Sample Demo Payees** (`mastercard-cbs-demo-payees.csv`):
```csv
msisdn,account,first_name,last_name,address,city,state,postal_code,country,bank_name,swift,purpose
0495822412,1001234567,John,Doe,123 Main St,New York,NY,10001,US,First National Bank,FNBAUS33,Government pension
0424942603,2002345678,Jane,Smith,45 High Street,London,,SW1A 1AA,GB,Barclays Bank,BARCGB22,Social welfare payment
0413356886,3003456789,Carlos,Rodriguez,Calle Mayor 10,Madrid,,28013,ES,Banco Santander,BSCHESMM,Education grant
0487123456,4004567890,Maria,Garcia,Via Roma 25,Rome,,00100,IT,UniCredit Bank,UNCRITMM,Healthcare subsidy
0456789012,5005678901,Pierre,Dubois,Rue de la Paix 5,Paris,,75002,FR,BNP Paribas,BNPAFRPP,Agricultural support
0498765432,6006789012,Hans,Mueller,Hauptstrasse 15,Berlin,,10115,DE,Deutsche Bank,DEUTDEFF,Family allowance
0423456789,7007890123,Yuki,Tanaka,1-1-1 Shibuya,Tokyo,,150-0002,JP,Mitsubishi UFJ,BOTKJPJT,Disaster relief
0465432109,8008901234,Li,Wei,888 Nanjing Rd,Shanghai,,200001,CN,Bank of China,BKCHCNBJ,Rural development
0487654321,9009012345,Ahmed,Hassan,King Fahd Rd,Riyadh,,11564,SA,Al Rajhi Bank,RJHISARI,Housing assistance
0491234567,1000123456,Priya,Sharma,MG Road 42,Mumbai,,400001,IN,HDFC Bank,HDFCINBB,Women empowerment
```

### Step 3: Load Identity Account Mapper

```bash
python3 load-identity-mapper-cbs-demo.py --config ~/tomconfig.ini
```

**Verification**:
```bash
curl -X POST http://identity-mapper:8080/api/v1/identity-account-mapper/batch-account-lookup \
  -H "Content-Type: application/json" \
  -d '{
    "requestID": "test-123",
    "registeringInstitutionId": "govstack",
    "beneficiaries": [
      {"payeeIdentity": "0495822412", "paymentModality": "MSISDN"}
    ]
  }'
```

### Step 4: Deploy Mock Mastercard API

```bash
cd repos/mastercard-cbs-simulator
mvn clean package
docker build -t mastercard-cbs-simulator:1.0.0 .

# Deploy to Kubernetes
kubectl create namespace mastercard-simulator
kubectl apply -f k8s/ -n mastercard-simulator

# Verify deployment
kubectl get pods -n mastercard-simulator
kubectl logs -f deployment/mastercard-cbs-simulator -n mastercard-simulator
```

### Step 5: Deploy CBS Connector

```bash
cd repos/ph-ee-connector-mastercard-cbs
mvn clean package
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .

# Update Helm values
cd repos/ph_template/helm/ph-ee-engine

# Add to values.yaml:
# connector-mastercard-cbs:
#   enabled: true
#   image:
#     repository: ph-ee-connector-mastercard-cbs
#     tag: 1.0.0

helm upgrade ph-ee . -n paymenthub

# Verify connector started
kubectl get pods -n paymenthub | grep mastercard-cbs
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub
```

### Step 6: Deploy BPMN Workflow

```bash
# Deploy workflow to Zeebe
zbctl deploy orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn \
  --address <zeebe-gateway>:26500

# Verify deployment
zbctl list workflows --address <zeebe-gateway>:26500 | grep mastercard_cbs
```

### Step 7: Test End-to-End Flow

#### 7.1 Prepare Test CSV

**File**: `src/utils/data-loading/bulk-cbs-demo-10.csv`

```csv
id,request_id,payment_mode,payee_identifier_type,payee_identifier,amount,currency,note
0,cbs-001,MASTERCARD_CBS,MSISDN,0495822412,100.00,USD,Pension payment
1,cbs-002,MASTERCARD_CBS,MSISDN,0424942603,150.00,GBP,Welfare payment
2,cbs-003,MASTERCARD_CBS,MSISDN,0413356886,200.00,EUR,Education grant
3,cbs-004,MASTERCARD_CBS,MSISDN,0487123456,75.00,EUR,Healthcare subsidy
4,cbs-005,MASTERCARD_CBS,MSISDN,0456789012,125.00,EUR,Agricultural support
5,cbs-006,MASTERCARD_CBS,MSISDN,0498765432,180.00,EUR,Family allowance
6,cbs-007,MASTERCARD_CBS,MSISDN,0423456789,90.00,JPY,Disaster relief
7,cbs-008,MASTERCARD_CBS,MSISDN,0465432109,110.00,CNY,Rural development
8,cbs-009,MASTERCARD_CBS,MSISDN,0487654321,95.00,SAR,Housing assistance
9,cbs-010,MASTERCARD_CBS,MSISDN,0491234567,135.00,INR,Women empowerment
```

#### 7.2 Submit Batch via API

```bash
cd src/utils/data-loading
python3 submit-batch.py \
  --config ~/tomconfig.ini \
  --file bulk-cbs-demo-10.csv \
  --tenant govstack \
  --callback-url http://callback-service/batch-complete \
  --purpose "GovStack CBS Demo Batch" \
  --payment-mode MASTERCARD_CBS
```

#### 7.3 Monitor Progress

**Terminal 1 - Bulk Processor Logs**:
```bash
kubectl logs -f deployment/ph-ee-bulk-processor -n paymenthub | grep -E "CBS|mastercard"
```

**Terminal 2 - CBS Connector Logs**:
```bash
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub
```

**Terminal 3 - Mock Mastercard API Logs**:
```bash
kubectl logs -f deployment/mastercard-cbs-simulator -n mastercard-simulator
```

**Terminal 4 - Zeebe Workflow Monitor**:
```bash
zbctl list instances --address <zeebe-gateway>:26500 | grep mastercard_cbs
```

#### 7.4 Check Batch Status

```bash
# Get batch ID from submit-batch.py output
BATCH_ID="<your-batch-id>"

# Batch Summary
curl -X GET "http://<ops-host>/api/v1/batch/summary?batchId=${BATCH_ID}" \
  -H "Platform-TenantId: govstack"

# Batch Details
curl -X GET "http://<ops-host>/api/v1/batch/detail?batchId=${BATCH_ID}" \
  -H "Platform-TenantId: govstack"

# Individual Transactions
curl -X GET "http://<ops-host>/api/v1/batch/transactions?batchId=${BATCH_ID}" \
  -H "Platform-TenantId: govstack"
```

**Expected Response** (Batch Summary):
```json
{
  "batchId": "cbs-demo-batch-123",
  "total": 10,
  "ongoing": 0,
  "failed": 0,
  "completed": 10,
  "completionRate": 100.0,
  "totalAmount": 1360.00
}
```

#### 7.5 Query Payment Status from Mock API

```bash
# Get payment ID from transaction details
PAYMENT_ID="<cbs-payment-id>"

# Query Mastercard simulator
curl -X GET "http://mastercard-simulator/send/v1/partners/transfer/${PAYMENT_ID}" \
  -H "Authorization: Bearer <token>"
```

## Testing Scenarios

### Scenario 1: Successful Batch (All 10 Payees)
- All MSISDNs have supplementary data
- All payments should complete successfully
- Expected: 10/10 completed

### Scenario 2: Partial Failure (Missing Supplementary Data)
- Remove 2 payees from supplementary data table
- Submit batch with all 10
- Expected: 8/10 completed, 2/10 failed with "No supplementary data"

### Scenario 3: CBS API Failure Simulation
- Configure mock API to return errors for specific amounts
- Example: amounts > $200 fail with "LIMIT_EXCEEDED"
- Expected: Transfers retry then fail, proper error tracking

### Scenario 4: Status Retrieval
- Submit batch
- Wait for completion
- Verify status updated in Operations DB from CBS retrieve API
- Expected: Payment status matches CBS simulator status

## Troubleshooting

### Issue: Connector Not Receiving Jobs

**Check**:
```bash
# Verify connector is registered with Zeebe
kubectl logs deployment/ph-ee-connector-mastercard-cbs -n paymenthub | grep "Registered worker"

# Check Zeebe gateway connection
kubectl logs deployment/ph-ee-connector-mastercard-cbs -n paymenthub | grep "zeebe"
```

**Fix**: Verify `ZEEBE_BROKER_CONTACTPOINT` environment variable is correct

### Issue: Supplementary Data Not Found

**Check**:
```bash
mysql -h <mysql-host> -u root -p operations -e \
  "SELECT COUNT(*) FROM mastercard_cbs_supplementary_data WHERE payee_msisdn = '0495822412';"
```

**Fix**: Re-run data loading script, verify MSISDN format matches

### Issue: Authentication Failed with Mock API

**Check**:
```bash
# Test OAuth endpoint directly
curl -X POST http://mastercard-simulator/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=demo&client_secret=demo"
```

**Fix**: Verify connector credentials match simulator configuration

### Issue: Batch Shows 0 Transactions

**Check**:
```sql
-- Verify transfers linked to batch
SELECT BATCH_ID, COUNT(*)
FROM transfers
WHERE BATCH_ID = '<your-batch-id>'
GROUP BY BATCH_ID;
```

**Fix**: Ensure connector is setting `X-BatchID` header (see [GOVSTACK.md](GOVSTACK.md) Issue #1)

### Issue: Payments Stuck in PENDING

**Check**:
```bash
# Check Zeebe workflow instances
zbctl list instances --address <zeebe-gateway>:26500 | grep mastercard_cbs

# Check for incidents
zbctl list incidents --address <zeebe-gateway>:26500
```

**Fix**: Review Zeebe incidents, check worker error logs

## Configuration Reference

### Connector Environment Variables

```bash
# Zeebe Configuration
ZEEBE_BROKER_CONTACTPOINT=ph-ee-zeebe-gateway:26500
ZEEBE_CLIENT_WORKER_THREADS=5

# Mastercard API Configuration
MASTERCARD_API_URL=http://mastercard-simulator:8080
MASTERCARD_AUTH_URL=http://mastercard-simulator:8080/oauth/token
MASTERCARD_CLIENT_ID=demo
MASTERCARD_CLIENT_SECRET=demo
MASTERCARD_PARTNER_ID=MIFOS_GOVSTACK

# Database Configuration
DATASOURCE_URL=jdbc:mysql://operationsdb-mysql:3306/operations
DATASOURCE_USERNAME=root
DATASOURCE_PASSWORD=<password>

# Application Configuration
SPRING_PROFILES_ACTIVE=default
LOGGING_LEVEL_ORG_MIFOS=DEBUG
```

### Mock API Configuration

```bash
# Server Configuration
SERVER_PORT=8080

# OAuth Configuration
OAUTH_ISSUER=mastercard-simulator
OAUTH_TOKEN_EXPIRY_SECONDS=3600

# Payment Simulation
PAYMENT_PROCESSING_DELAY_MS=2000  # Simulate processing time
PAYMENT_AUTO_COMPLETE=true        # Auto-transition to COMPLETED
PAYMENT_SUCCESS_RATE=100          # Percentage of successful payments
```

## Database Queries for Monitoring

### Check Batch Progress
```sql
SELECT
    b.BATCH_ID,
    b.TOTAL_TRANSACTIONS,
    b.ONGOING,
    b.FAILED,
    b.COMPLETED,
    b.STATUS,
    b.START_TIME,
    b.END_TIME
FROM batches b
WHERE b.BATCH_ID = '<batch-id>';
```

### Check Individual Transfers
```sql
SELECT
    t.TRANSACTION_ID,
    t.PAYEE_PARTY_ID,
    t.AMOUNT,
    t.CURRENCY,
    t.STATUS,
    t.ERROR_INFORMATION,
    t.STARTED_AT,
    t.COMPLETED_AT
FROM transfers t
WHERE t.BATCH_ID = '<batch-id>'
ORDER BY t.TRANSACTION_ID;
```

### Check CBS Payment References
```sql
SELECT
    t.TRANSACTION_ID,
    t.PAYEE_PARTY_ID,
    t.EXTERNAL_ID,  -- CBS payment ID
    t.STATUS
FROM transfers t
WHERE t.BATCH_ID = '<batch-id>'
  AND t.EXTERNAL_ID LIKE 'CBS-%';
```

### Check Supplementary Data Coverage
```sql
-- MSISDNs in batch vs supplementary data
SELECT
    'In Batch' as source,
    COUNT(*) as count
FROM transfers
WHERE BATCH_ID = '<batch-id>'
UNION ALL
SELECT
    'Has Supp Data',
    COUNT(*)
FROM transfers t
JOIN mastercard_cbs_supplementary_data s ON t.PAYEE_PARTY_ID = s.payee_msisdn
WHERE t.BATCH_ID = '<batch-id>';
```

## Performance Metrics

### Expected Processing Times
- **Batch Upload**: < 5 seconds
- **Identity Account Lookup**: ~500ms per 10 identities
- **CBS Payment Submission**: ~2-3 seconds per payment
- **Status Retrieval**: ~500ms per payment
- **Total Batch (10 payments)**: ~1-2 minutes

### Resource Usage
- **CBS Connector Pod**: 256Mi memory, 0.25 CPU
- **Mock API Pod**: 128Mi memory, 0.1 CPU
- **Supplementary Data Table**: ~10KB for 10 records

## Next Steps

### For Production Deployment
1. **Replace Mock API**: Integrate with real Mastercard CBS Sandbox
2. **Production Credentials**: Use actual Mastercard OAuth credentials
3. **Full Supplementary Data**: Build data collection/sync mechanism
4. **Compliance**: Add KYC/AML screening workflows
5. **Monitoring**: Set up Prometheus/Grafana dashboards
6. **Error Handling**: Enhanced retry and reconciliation logic
7. **Security**: Encrypt sensitive data, secure credential storage

### For Extended Demo
1. **Add More Payees**: Scale supplementary data beyond 10
2. **Multi-Currency**: Test with different currency combinations
3. **Failure Scenarios**: Simulate various error conditions
4. **Reconciliation**: Build daily reconciliation reports
5. **Webhook Support**: Add async status callbacks from Mastercard

## Support

### Documentation
- [Full Implementation Plan](MASTERCARD_CBS_IMPLEMENTATION_PLAN.md)
- [GovStack Architecture](GOVSTACK.md)
- [PaymentHub EE Docs](https://mifos.gitbook.io/docs/payment-hub-ee)

### Troubleshooting
- Check connector logs for Zeebe worker errors
- Verify supplementary data loaded correctly
- Ensure BPMN workflow deployed to Zeebe
- Test mock API endpoints independently

### Contact
For issues or questions, file a GitHub issue in the mifos-gazelle repository.

---

**Last Updated**: 2025-12-21
**Version**: 1.0
**Status**: Ready for Implementation
