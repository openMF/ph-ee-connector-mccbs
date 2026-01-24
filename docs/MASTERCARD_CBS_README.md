# Mastercard CBS Demo Connector for PaymentHub EE

## Overview

This implementation adds Mastercard Cross-Border Services (CBS) capabilities to Mifos Payment Hub EE, enabling GovStack-compliant cross-border disbursements.

**Demo Scope**:
- Receive GovStack PayBB-compliant disbursement instructions
- Resolve payee identities via Account Mapper
- Match regulatory data from pre-populated tables
- Submit payments to Mastercard CBS (sandbox/simulator)
- Track payment status via PaymentHub APIs
- Optional: Retrieve payment status from Mastercard

## Project Structure

```
mifos-gazelle/
├── docs/
│   ├── MASTERCARD_CBS_IMPLEMENTATION_PLAN.md   # Detailed implementation plan
│   ├── MASTERCARD_CBS_QUICKSTART.md            # Step-by-step setup guide
│   ├── MASTERCARD_CBS_README.md                # This file
│   └── GOVSTACK.md                             # GovStack architecture notes
│
├── repos/
│   ├── ph-ee-connector-mastercard-cbs/         # Main CBS connector service
│   │   ├── src/main/java/org/mifos/connector/mastercard/
│   │   │   ├── config/                         # Spring Boot configuration
│   │   │   ├── service/                        # Business logic services
│   │   │   ├── model/                          # Data models
│   │   │   ├── zeebe/                          # Zeebe workers
│   │   │   └── api/                            # REST controllers (if any)
│   │   ├── pom.xml
│   │   └── README.md
│   │
│   ├── mastercard-cbs-simulator/               # Mock Mastercard API
│   │   ├── src/main/java/org/mifos/simulator/mastercard/
│   │   │   ├── controller/                     # API endpoints
│   │   │   ├── model/                          # Request/response models
│   │   │   └── service/                        # Simulation logic
│   │   ├── pom.xml
│   │   └── README.md
│   │
│   └── ph_template/helm/ph-ee-engine/
│       └── connector-mastercard-cbs/           # Helm chart for deployment
│           ├── Chart.yaml
│           ├── values.yaml
│           ├── README.md
│           └── templates/
│               ├── deployment.yaml
│               ├── service.yaml
│               ├── secret.yaml
│               └── ...
│
├── orchestration/feel/
│   └── bulk_connector_mastercard_cbs-DFSPID.bpmn   # BPMN workflow
│
├── src/utils/data-loading/
│   ├── mastercard-cbs-schema.sql               # Database schema
│   ├── mastercard-cbs-demo-payees.csv          # 10 demo payees data
│   ├── load-mastercard-cbs-supplementary-data.py   # Data loader
│   ├── load-identity-mapper-cbs-demo.py        # Identity mapper setup
│   ├── bulk-cbs-demo-10.csv                    # Test batch CSV
│   └── test-cbs-flow.sh                        # End-to-end test script
│
└── config/
    └── ph_values.yaml                          # Updated with CBS connector config
```

## Components

### 1. Mastercard CBS Connector (`ph-ee-connector-mastercard-cbs`)

**Technology**: Java 17, Spring Boot 3.2, Zeebe Spring Client

**Responsibilities**:
- Authenticate with Mastercard CBS API (OAuth 2.0)
- Query supplementary regulatory data from MySQL
- Format and submit payment requests to CBS API
- Handle responses and update workflow variables
- Retrieve payment status (optional)
- Update PaymentHub Operations database

**Zeebe Workers**:
| Worker Type | Purpose | Input | Output |
|-------------|---------|-------|--------|
| `mastercard-cbs-authenticate` | Get OAuth token | Client credentials | Access token |
| `mastercard-cbs-match-regulatory-data` | Lookup supplementary data | MSISDN | Beneficiary details |
| `mastercard-cbs-initiate-payment` | Submit payment | Payment details + supp data | CBS payment ID |
| `mastercard-cbs-check-status` | Query payment status | CBS payment ID | Current status |
| `mastercard-cbs-update-operations` | Update Operations DB | Payment result | Transfer record updated |

**Configuration** (`application.yaml`):
```yaml
mastercard:
  api:
    url: ${MASTERCARD_API_URL}
    auth-url: ${MASTERCARD_AUTH_URL}
    partner-id: ${MASTERCARD_PARTNER_ID:MIFOS_GOVSTACK}
  oauth:
    client-id: ${MASTERCARD_CLIENT_ID}
    client-secret: ${MASTERCARD_CLIENT_SECRET}
  payment:
    timeout-seconds: 30
    max-retries: 3
```

### 2. Mastercard CBS Simulator (`mastercard-cbs-simulator`)

**Technology**: Java 17, Spring Boot 3.2, Spring Security

**Purpose**: Mock Mastercard CBS API for demo/testing

**Endpoints**:
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/oauth/token` | OAuth token generation |
| POST | `/send/v1/partners/transfer` | Submit payment |
| GET | `/send/v1/partners/transfer/{id}` | Get payment status |

**Features**:
- In-memory payment storage
- Configurable success rate
- Automatic status transitions (PENDING → PROCESSING → COMPLETED)
- Simulated processing delays
- Comprehensive logging

**Configuration**:
```yaml
simulator:
  oauth:
    token-expiry-seconds: 3600
  payment:
    processing-delay-ms: 2000
    auto-complete: true
    success-rate: 100  # Percentage
```

### 3. Supplementary Data Service

**Storage**: MySQL table in PaymentHub Operations database

**Purpose**: Store regulatory/KYC data required by Mastercard CBS

**Schema**: [See MASTERCARD_CBS_QUICKSTART.md](MASTERCARD_CBS_QUICKSTART.md#component-3-supplementary-data-database)

**Demo Data**: 10 pre-populated payees covering multiple countries/banks

**Access Pattern**:
- Primary lookup: By MSISDN
- Secondary lookup: By account number
- Connector queries during payment processing

### 4. BPMN Workflow

**File**: `orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn`

**Flow**:
```
Start
  ↓
Validate Input
  ↓
Authenticate → [Get OAuth Token]
  ↓
Match Regulatory Data → [Query Supplementary Data Table]
  ↓
Initiate Payment → [POST to Mastercard CBS]
  ↓
Wait for Completion (Async)
  ↓
Retrieve Status (Optional) → [GET from Mastercard CBS]
  ↓
Update Operations DB → [Record final status]
  ↓
Complete
```

**Error Handling**:
- Authentication failures: Retry with backoff
- Missing supplementary data: Fail immediately with clear error
- Payment submission failures: Retry up to 3 times
- Status retrieval failures: Log warning but don't fail transaction

### 5. Data Loading Scripts

**Scripts**:
1. `mastercard-cbs-schema.sql` - Create supplementary data table
2. `load-mastercard-cbs-supplementary-data.py` - Load 10 demo payees
3. `load-identity-mapper-cbs-demo.py` - Populate identity account mapper

**Usage**:
```bash
# Create table
mysql -h <host> -u root -p operations < mastercard-cbs-schema.sql

# Load supplementary data
python3 load-mastercard-cbs-supplementary-data.py --config ~/tomconfig.ini

# Load identity mapper
python3 load-identity-mapper-cbs-demo.py --config ~/tomconfig.ini
```

## Quick Start

See [MASTERCARD_CBS_QUICKSTART.md](MASTERCARD_CBS_QUICKSTART.md) for detailed setup instructions.

**Summary**:
1. Deploy Mock Mastercard API simulator
2. Create supplementary data table in Operations DB
3. Load 10 demo payees
4. Pre-populate Identity Account Mapper
5. Deploy CBS connector service
6. Deploy BPMN workflow to Zeebe
7. Submit test batch via Operations API
8. Monitor progress and check status

## Integration with Existing PaymentHub

### Bulk Processor Integration

**Update**: `repos/ph_template/helm/ph-ee-engine/bulk-processor/values.yaml`

Add CBS to supported payment modes:
```yaml
payment:
  modes:
    - CLOSEDLOOP
    - MOJALOOP
    - SLCB
    - MASTERCARD_CBS  # NEW
```

**Routing Logic**:
When bulk-processor encounters `payment_mode: MASTERCARD_CBS` in CSV, it routes to:
- Workflow: `bulk_connector_mastercard_cbs-{tenant}`
- This workflow invokes the CBS connector Zeebe workers

### CSV Format

**Standard GovStack Format** (for CBS payments):
```csv
id,request_id,payment_mode,payee_identifier_type,payee_identifier,amount,currency,note
0,uuid1,MASTERCARD_CBS,MSISDN,0495822412,100.00,USD,Government disbursement
```

**Required Headers When Submitting**:
```
X-Signature: <signature>
X-CorrelationID: <batch-id>
Platform-TenantId: govstack
type: csv
filename: bulk-cbs-demo-10.csv
X-Registering-Institution-ID: govstack  # Triggers GovStack mode
Purpose: GovStack CBS Demo
```

### Status Tracking

Uses existing PaymentHub APIs:

**Batch Summary**:
```
GET /api/v1/batch/summary?batchId={batchId}
```

**Batch Details**:
```
GET /api/v1/batch/detail?batchId={batchId}
```

**Individual Transactions**:
```
GET /api/v1/batch/transactions?batchId={batchId}
```

CBS connector updates `transfers` table in Operations DB, which feeds these APIs automatically.

## Architecture Flow

### End-to-End Payment Flow

```
1. Source System (GovStack)
   ↓ POST /batchtransactions (CSV with MSISDNs)

2. Operations App
   ↓ Store batch, start workflow

3. Bulk Processor (GovStack Mode)
   ↓ Detect X-Registering-Institution-ID header

4. Identity Account Mapper
   ↓ Resolve MSISDNs → Account Numbers + Institution Codes

5. Bulk Processor (Splitting)
   ↓ Group by Institution Code
   ↓ For institution_code = "MASTERCARD_CBS"

6. Start Workflow: bulk_connector_mastercard_cbs-govstack
   ↓

7. CBS Connector Workers:
   a. mastercard-cbs-authenticate
      ↓ POST /oauth/token → Access Token

   b. mastercard-cbs-match-regulatory-data
      ↓ SELECT FROM mastercard_cbs_supplementary_data WHERE payee_msisdn = ?
      ↓ Returns: Beneficiary name, address, bank details, etc.

   c. mastercard-cbs-initiate-payment
      ↓ Build CBS payment request with:
      │   - Amount, currency
      │   - Beneficiary details (from supplementary data)
      │   - Bank routing info
      │   - Regulatory fields (purpose, source of funds)
      ↓ POST /send/v1/partners/transfer
      ↓ Returns: CBS Payment ID

   d. mastercard-cbs-check-status (optional)
      ↓ GET /send/v1/partners/transfer/{id}
      ↓ Returns: Status (PENDING, PROCESSING, COMPLETED, FAILED)

   e. mastercard-cbs-update-operations
      ↓ UPDATE transfers SET status = ?, external_id = ? WHERE transaction_id = ?

8. Bulk Processor (Merge Results)
   ↓ Aggregate all sub-batch results

9. Operations App (Batch Aggregate)
   ↓ Calculate totals from transfers table

10. Source System Queries Status
    ↓ GET /api/v1/batch/summary?batchId=...
    ↓ Returns: Total: 10, Completed: 10, Failed: 0
```

### Data Flow Diagram

```
┌─────────────────────┐
│  identity_details   │  (Identity Account Mapper DB)
│  payee_identity     │
│  → 0495822412       │
└──────────┬──────────┘
           │ Maps to
           ↓
┌────────────────────────────┐
│ payment_modality_details   │
│ destination_account        │
│ → 1001234567              │
│ institution_code           │
│ → MASTERCARD_CBS          │
└──────────┬─────────────────┘
           │
           │ Connector looks up
           ↓
┌────────────────────────────────────┐
│ mastercard_cbs_supplementary_data  │  (Operations DB)
│ payee_msisdn: 0495822412          │
│ payee_account_number: 1001234567  │
│ beneficiary_full_name: John Doe   │
│ bank_name: First National Bank    │
│ bank_bic_swift: FNBAUS33          │
│ beneficiary_country_code: US      │
│ purpose_of_payment: Gov't pension │
└──────────┬─────────────────────────┘
           │
           │ Used to build CBS request
           ↓
┌──────────────────────────┐
│ Mastercard CBS API       │
│ POST /send/.../transfer  │
│ {                        │
│   amount: 100.00,        │
│   currency: USD,         │
│   recipient: {           │
│     name: John Doe,      │
│     account: 1001234567, │
│     bank: {...}          │
│   }                      │
│ }                        │
└──────────┬───────────────┘
           │ Returns payment ID
           ↓
┌──────────────────────┐
│ Operations DB        │
│ transfers table      │
│ external_id:         │
│ → CBS-12345-67890   │
│ status: COMPLETED    │
└──────────────────────┘
```

## Configuration

### Environment Variables (Connector)

```bash
# Zeebe
ZEEBE_BROKER_CONTACTPOINT=ph-ee-zeebe-gateway:26500
ZEEBE_CLIENT_WORKER_THREADS=5

# Mastercard API
MASTERCARD_API_URL=http://mastercard-simulator:8080
MASTERCARD_AUTH_URL=http://mastercard-simulator:8080/oauth/token
MASTERCARD_CLIENT_ID=demo
MASTERCARD_CLIENT_SECRET=demo
MASTERCARD_PARTNER_ID=MIFOS_GOVSTACK

# Database
DATASOURCE_URL=jdbc:mysql://operationsdb-mysql:3306/operations
DATASOURCE_USERNAME=root
DATASOURCE_PASSWORD=<from-secret>

# Logging
LOGGING_LEVEL_ORG_MIFOS=DEBUG
LOGGING_LEVEL_IO_CAMUNDA=INFO
```

### Helm Values

```yaml
connector-mastercard-cbs:
  enabled: true
  replicas: 1

  image:
    repository: openmf/ph-ee-connector-mastercard-cbs
    tag: 1.0.0
    pullPolicy: IfNotPresent

  resources:
    limits:
      cpu: 500m
      memory: 512Mi
    requests:
      cpu: 250m
      memory: 256Mi

  env:
    SPRING_PROFILES_ACTIVE: production
    MASTERCARD_API_URL: http://mastercard-simulator:8080
    ZEEBE_BROKER_CONTACTPOINT: zeebe-zeebe-gateway:26500

  secrets:
    mastercard:
      clientId: mastercard-cbs-credentials
      clientSecret: mastercard-cbs-credentials
```

## Testing

### Unit Tests
- Service layer tests (auth, supplementary data, payment)
- Model serialization/deserialization
- Zeebe worker logic

### Integration Tests
- End-to-end workflow execution
- Database interactions
- Mock API calls

### UAT Scenarios

1. **Happy Path**: All 10 payees have supplementary data, all payments succeed
2. **Partial Failure**: 2 payees missing supplementary data, 8/10 succeed
3. **CBS API Errors**: Simulate CBS failures, verify retry logic
4. **Status Tracking**: Verify status updates from retrieve API
5. **Batch Queries**: Test all batch status APIs

**Test Script**:
```bash
cd src/utils/data-loading
./test-cbs-flow.sh --config ~/tomconfig.ini --scenario all
```

## Monitoring

### Logs to Monitor

1. **CBS Connector**:
```bash
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub
```
Look for: Worker registrations, payment submissions, status updates

2. **Mock CBS API**:
```bash
kubectl logs -f deployment/mastercard-cbs-simulator -n mastercard-simulator
```
Look for: Token requests, payment submissions, status queries

3. **Bulk Processor**:
```bash
kubectl logs -f deployment/ph-ee-bulk-processor -n paymenthub | grep CBS
```
Look for: Workflow starts, account lookups, batch splitting

4. **Zeebe**:
```bash
zbctl list instances --address zeebe-gateway:26500 | grep mastercard_cbs
```
Look for: Active workflows, completed workflows, incidents

### Metrics

**Key Performance Indicators**:
- Payment success rate (target: >95%)
- Average processing time per payment (target: <5s)
- Token refresh rate (should be hourly, not per-payment)
- Supplementary data cache hit rate (target: 100% for demo)

**Database Queries**:
```sql
-- Success rate for CBS payments
SELECT
    COUNT(*) as total,
    SUM(CASE WHEN STATUS = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
    SUM(CASE WHEN STATUS = 'FAILED' THEN 1 ELSE 0 END) as failed,
    (SUM(CASE WHEN STATUS = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) as success_rate
FROM transfers
WHERE CREATED_AT >= DATE_SUB(NOW(), INTERVAL 1 DAY)
  AND EXTERNAL_ID LIKE 'CBS-%';

-- Average processing time
SELECT
    AVG(TIMESTAMPDIFF(SECOND, STARTED_AT, COMPLETED_AT)) as avg_seconds
FROM transfers
WHERE STATUS = 'COMPLETED'
  AND EXTERNAL_ID LIKE 'CBS-%'
  AND CREATED_AT >= DATE_SUB(NOW(), INTERVAL 1 DAY);
```

## Security Considerations

1. **Credentials Management**:
   - Store Mastercard credentials in Kubernetes secrets
   - Never log credentials or tokens
   - Rotate credentials regularly

2. **Data Protection**:
   - Encrypt supplementary data at rest (MySQL encryption)
   - Use TLS for all API calls to Mastercard
   - Mask PII in logs

3. **Access Control**:
   - RBAC for connector service account
   - Restrict database access to connector only
   - Audit all CBS payment submissions

4. **Compliance**:
   - Validate beneficiary data against sanctions lists (future)
   - Log all regulatory data access
   - Retain payment records per compliance requirements

## Limitations (Demo Scope)

1. **10 Payees Only**: Supplementary data limited to demo dataset
2. **Mock API**: Using simulator, not real Mastercard Sandbox
3. **No Real Money**: All payments are simulated
4. **Simplified Compliance**: No KYC/AML screening
5. **Manual Data Loading**: No automated sync of supplementary data
6. **Single Currency Flows**: No real-time FX
7. **No Warranty**: Post-UAT support limited to bug fixes

## Future Enhancements (Production)

### Phase 1: Real API Integration
- Replace simulator with Mastercard Sandbox
- Production OAuth credentials
- SSL certificate validation
- Request signing (if required by Mastercard)

### Phase 2: Full Supplementary Data
- API to collect beneficiary details
- KYC document upload/verification
- Automated data sync from source systems
- Data validation rules

### Phase 3: Compliance & Risk
- Sanctions screening integration (OFAC, UN, EU lists)
- AML monitoring and suspicious activity reporting
- Transaction limits and velocity checks
- Fraud detection

### Phase 4: Operational Excellence
- Comprehensive reconciliation (daily, monthly)
- Failed payment retry dashboard
- Real-time monitoring alerts
- SLA tracking and reporting

### Phase 5: Feature Expansion
- Multi-currency support with FX
- Webhook support for async status updates
- Bulk status retrieval optimization
- Payment reversals/refunds

## Troubleshooting Guide

See [MASTERCARD_CBS_QUICKSTART.md - Troubleshooting](MASTERCARD_CBS_QUICKSTART.md#troubleshooting) for detailed troubleshooting steps.

**Common Issues**:
- Connector not receiving Zeebe jobs → Check gateway connection
- Supplementary data not found → Verify MSISDNs match exactly
- Authentication failures → Check credentials in secrets
- Batch shows 0 transactions → Ensure `X-BatchID` header set
- Payments stuck in PENDING → Check Zeebe incidents

## Support & Contact

**Documentation**:
- [Implementation Plan](MASTERCARD_CBS_IMPLEMENTATION_PLAN.md) - Detailed technical design
- [Quick Start Guide](MASTERCARD_CBS_QUICKSTART.md) - Step-by-step setup
- [GovStack Notes](GOVSTACK.md) - Architecture and known issues

**Issue Tracking**:
- File bugs/questions in mifos-gazelle GitHub repository
- Tag with `mastercard-cbs` label
- Include logs and configuration when reporting issues

**Community**:
- Mifos Community Forum: https://mifos.org/community
- Payment Hub EE Slack: #payment-hub-ee

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

Copyright (c) 2025 Mifos Initiative

---

**Version**: 1.0.0
**Last Updated**: 2025-12-21
**Status**: Ready for Implementation
**Maintainer**: Mifos Payment Hub Team
