# Mastercard CBS Demo Connector - Implementation Plan

## Executive Summary

This document outlines the implementation plan for integrating Mifos Payment Hub EE with Mastercard Cross-Border Services (CBS) to enable GovStack-compliant cross-border disbursements.

**Scope**: Demo connector for proof-of-concept cross-border payments from GovStack → PaymentHub EE → Mastercard CBS Sandbox

**Key Components**:
1. New Mastercard CBS Demo Connector service
2. BPMN workflow for CBS payment processing
3. Supplementary regulatory data table (10 payees)
4. Integration with existing GovStack flows
5. Payment status tracking and retrieval

---

## Table of Contents
- [Architecture Overview](#architecture-overview)
- [Component Design](#component-design)
- [Implementation Steps](#implementation-steps)
- [Data Model](#data-model)
- [API Specifications](#api-specifications)
- [Testing Strategy](#testing-strategy)
- [Deployment Guide](#deployment-guide)

---

## Architecture Overview

### High-Level Flow

```
┌─────────────────────┐
│  Source System      │
│  (GovStack PayBB)   │
└──────────┬──────────┘
           │ 1. Disbursement Instruction
           │    (GovStack API format)
           ▼
┌─────────────────────────────┐
│  Mifos Payment Hub EE       │
│  - Operations App (API)     │
│  - Bulk Processor           │
└──────────┬──────────────────┘
           │ 2. Account Lookup
           ▼
┌─────────────────────────────┐
│  Identity Account Mapper    │
│  (Pre-populated MSISDNs)    │
└──────────┬──────────────────┘
           │ 3. Resolved Accounts
           ▼
┌─────────────────────────────┐
│  Bulk Processor             │
│  (G2P Workflow)             │
└──────────┬──────────────────┘
           │ 4. Initiate CBS Payment
           ▼
┌─────────────────────────────┐
│  Mastercard CBS Connector   │ ◄──NEW COMPONENT
│  - Match Regulatory Data    │
│  - Format CBS API Request   │
└──────────┬──────────────────┘
           │ 5. Send Payment Request
           ▼
┌─────────────────────────────┐
│  Mastercard CBS Sandbox     │
│  sandbox.api.mastercard.com │
└──────────┬──────────────────┘
           │ 6. Payment Response
           ▼
┌─────────────────────────────┐
│  Payment Hub EE             │
│  - Update Status            │
│  - Track in Operations DB   │
└──────────┬──────────────────┘
           │ 7. Status Query (API)
           ▼
┌─────────────────────────────┐
│  Source System              │
│  - Batch Summary            │
│  - Batch Details            │
│  - Payment Status           │
└─────────────────────────────┘
```

### Integration Points

1. **Input**: Operations App `/batchtransactions` API (existing)
2. **Account Resolution**: Identity Account Mapper (existing, pre-populated)
3. **Workflow Routing**: Bulk Processor G2P workflow (existing)
4. **CBS Connector**: NEW - Mastercard CBS Demo Connector
5. **Status API**: Operations App batch APIs (existing)
6. **Optional Retrieval**: NEW - Mastercard Retrieve Payment API integration

---

## Component Design

### 1. Mastercard CBS Demo Connector

**Technology Stack**:
- Java 17 / Spring Boot 3.x
- Zeebe Spring Client
- Spring WebFlux (for async HTTP calls)
- Mastercard API Client SDK (or RestTemplate)

**Key Responsibilities**:
1. Authenticate with Mastercard OAuth endpoint
2. Match payee identity to supplementary regulatory data
3. Format payment instruction per Mastercard CBS API spec
4. Submit payment to Mastercard Sandbox
5. Handle response and update workflow variables
6. (Optional) Retrieve payment status from Mastercard

**Zeebe Workers**:
- `mastercard-cbs-authenticate` - OAuth token acquisition
- `mastercard-cbs-match-regulatory-data` - Lookup supplementary data
- `mastercard-cbs-initiate-payment` - Submit payment to CBS
- `mastercard-cbs-check-status` - (Optional) Retrieve payment status

### 2. Supplementary Data Service

**Purpose**: Store regulatory/KYC data required by Mastercard CBS that isn't in PaymentHub

**Storage**: MySQL database table in Operations App DB or separate service DB

**Schema**:
```sql
CREATE TABLE mastercard_cbs_supplementary_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payee_msisdn VARCHAR(20) UNIQUE NOT NULL,
    payee_account_number VARCHAR(50) NOT NULL,

    -- Beneficiary Details
    beneficiary_full_name VARCHAR(255) NOT NULL,
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

    -- Regulatory/Compliance
    purpose_of_payment VARCHAR(255),
    source_of_funds VARCHAR(100),
    beneficiary_tax_id VARCHAR(50),

    -- Metadata
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_msisdn (payee_msisdn),
    INDEX idx_account (payee_account_number)
);
```

**10 Demo Payees**: Pre-populated with realistic test data

### 3. BPMN Workflow

**File**: `orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn`

**Workflow Name**: `bulk_connector_mastercard_cbs-{tenant}`

**Flow Diagram**:
```
Start
  │
  ├─► Authenticate
  │     │
  │     ├─► Success? ──No──► Error: Auth Failed
  │     │
  │     └─► Yes
  │           │
  ├─► Match Regulatory Data
  │     │
  │     ├─► Found? ──No──► Error: No Supplementary Data
  │     │
  │     └─► Yes
  │           │
  ├─► Initiate CBS Payment
  │     │
  │     ├─► Success? ──No──► Retry Logic
  │     │                      │
  │     │                      └─► Max Retries? ──Yes──► Error: Payment Failed
  │     │                                           │
  │     │                                           └─► No ──► Retry
  │     └─► Yes
  │           │
  ├─► (Optional) Retrieve Payment Status
  │     │
  │     ├─► Success?
  │     │
  │     └─► Update Status
  │           │
  └─► Update Operations DB
        │
        └─► Complete
```

**Key Workflow Variables**:
- `batchId` - Batch identifier
- `transactionId` - Individual transaction ID
- `payeeIdentity` - MSISDN
- `payeeAccountNumber` - Resolved account
- `amount` - Payment amount
- `currency` - Currency code
- `cbsAccessToken` - Mastercard OAuth token
- `cbsPaymentId` - Mastercard payment reference
- `cbsPaymentStatus` - Payment status from Mastercard
- `supplementaryData` - Regulatory data object
- `transferFailed` - Boolean error flag
- `errorCode` - Error code if failed
- `errorMessage` - Error description

---

## Implementation Steps

### Phase 1: Foundation (Week 1)

#### 1.1 Create Connector Service Structure
```bash
repos/
└── ph-ee-connector-mastercard-cbs/
    ├── src/
    │   ├── main/
    │   │   ├── java/org/mifos/connector/mastercard/
    │   │   │   ├── config/
    │   │   │   │   ├── MastercardConfig.java
    │   │   │   │   └── ZeebeConfig.java
    │   │   │   ├── service/
    │   │   │   │   ├── MastercardAuthService.java
    │   │   │   │   ├── MastercardPaymentService.java
    │   │   │   │   └── SupplementaryDataService.java
    │   │   │   ├── model/
    │   │   │   │   ├── SupplementaryData.java
    │   │   │   │   ├── MastercardPaymentRequest.java
    │   │   │   │   └── MastercardPaymentResponse.java
    │   │   │   └── zeebe/
    │   │   │       ├── AuthenticateWorker.java
    │   │   │       ├── MatchRegulatoryDataWorker.java
    │   │   │       ├── InitiatePaymentWorker.java
    │   │   │       └── CheckStatusWorker.java
    │   │   └── resources/
    │   │       └── application.yaml
    │   └── test/
    ├── pom.xml
    └── README.md
```

#### 1.2 Create Helm Chart
```bash
repos/ph_template/helm/ph-ee-engine/
└── connector-mastercard-cbs/
    ├── Chart.yaml
    ├── values.yaml
    ├── README.md
    └── templates/
        ├── deployment.yaml
        ├── service.yaml
        ├── serviceaccount.yaml
        ├── clusterrole.yaml
        ├── clusterrolebinding.yaml
        └── secret.yaml
```

#### 1.3 Database Setup
- Create supplementary data table
- Create data loading script
- Populate 10 demo payees

### Phase 2: Core Implementation (Week 2-3)

#### 2.1 Authentication Service
```java
@Service
public class MastercardAuthService {

    @Value("${mastercard.auth.url}")
    private String authUrl;

    @Value("${mastercard.client.id}")
    private String clientId;

    @Value("${mastercard.client.secret}")
    private String clientSecret;

    private String accessToken;
    private Instant tokenExpiry;

    public String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            refreshToken();
        }
        return accessToken;
    }

    private void refreshToken() {
        // OAuth2 client credentials flow
        // POST to auth endpoint
        // Store token and expiry
    }
}
```

#### 2.2 Supplementary Data Service
```java
@Service
public class SupplementaryDataService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public SupplementaryData findByMsisdn(String msisdn) {
        String sql = "SELECT * FROM mastercard_cbs_supplementary_data WHERE payee_msisdn = ?";
        return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(SupplementaryData.class), msisdn);
    }
}
```

#### 2.3 Payment Service
```java
@Service
public class MastercardPaymentService {

    @Value("${mastercard.api.url}")
    private String apiUrl;

    @Autowired
    private MastercardAuthService authService;

    public MastercardPaymentResponse initiatePayment(
            String payeeAccount,
            BigDecimal amount,
            String currency,
            SupplementaryData suppData) {

        String token = authService.getAccessToken();

        // Build payment request per Mastercard API spec
        MastercardPaymentRequest request = MastercardPaymentRequest.builder()
            .amount(amount)
            .currency(currency)
            .beneficiary(mapToBeneficiary(suppData))
            .build();

        // POST to Mastercard CBS API
        // Return response
    }

    public MastercardPaymentStatus retrievePaymentStatus(String paymentId) {
        // GET from Mastercard Retrieve Payment API
        // Return status
    }
}
```

#### 2.4 Zeebe Workers
```java
@Component
public class MastercardCbsWorkers {

    @Autowired
    private MastercardAuthService authService;

    @Autowired
    private SupplementaryDataService suppDataService;

    @Autowired
    private MastercardPaymentService paymentService;

    @ZeebeWorker(type = "mastercard-cbs-authenticate")
    public void authenticate(ActivatedJob job, ZeebeClient client) {
        try {
            String token = authService.getAccessToken();
            client.newCompleteCommand(job.getKey())
                .variable("cbsAccessToken", token)
                .send();
        } catch (Exception e) {
            client.newFailCommand(job.getKey())
                .retries(job.getRetries() - 1)
                .errorMessage(e.getMessage())
                .send();
        }
    }

    @ZeebeWorker(type = "mastercard-cbs-match-regulatory-data")
    public void matchRegulatoryData(ActivatedJob job, ZeebeClient client) {
        try {
            String msisdn = (String) job.getVariablesAsMap().get("payeeIdentity");
            SupplementaryData suppData = suppDataService.findByMsisdn(msisdn);

            client.newCompleteCommand(job.getKey())
                .variable("supplementaryData", suppData)
                .send();
        } catch (EmptyResultDataAccessException e) {
            client.newFailCommand(job.getKey())
                .retries(0)
                .errorMessage("No supplementary data found for MSISDN: " + msisdn)
                .send();
        }
    }

    @ZeebeWorker(type = "mastercard-cbs-initiate-payment")
    public void initiatePayment(ActivatedJob job, ZeebeClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String account = (String) vars.get("payeeAccountNumber");
            BigDecimal amount = new BigDecimal(vars.get("amount").toString());
            String currency = (String) vars.get("currency");
            SupplementaryData suppData = objectMapper.convertValue(
                vars.get("supplementaryData"), SupplementaryData.class);

            MastercardPaymentResponse response = paymentService.initiatePayment(
                account, amount, currency, suppData);

            client.newCompleteCommand(job.getKey())
                .variable("cbsPaymentId", response.getPaymentId())
                .variable("cbsPaymentStatus", response.getStatus())
                .variable("transferFailed", false)
                .send();
        } catch (Exception e) {
            client.newFailCommand(job.getKey())
                .retries(job.getRetries() - 1)
                .errorMessage(e.getMessage())
                .send();
        }
    }

    @ZeebeWorker(type = "mastercard-cbs-check-status")
    public void checkStatus(ActivatedJob job, ZeebeClient client) {
        try {
            String paymentId = (String) job.getVariablesAsMap().get("cbsPaymentId");
            MastercardPaymentStatus status = paymentService.retrievePaymentStatus(paymentId);

            client.newCompleteCommand(job.getKey())
                .variable("cbsPaymentStatus", status.getStatus())
                .variable("cbsPaymentDetails", status)
                .send();
        } catch (Exception e) {
            client.newFailCommand(job.getKey())
                .retries(job.getRetries() - 1)
                .errorMessage(e.getMessage())
                .send();
        }
    }
}
```

### Phase 3: BPMN Workflow (Week 3)

#### 3.1 Create Workflow File
File: `orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn`

Key service tasks:
1. **Authenticate**: `mastercard-cbs-authenticate`
2. **Match Data**: `mastercard-cbs-match-regulatory-data`
3. **Initiate Payment**: `mastercard-cbs-initiate-payment`
4. **Check Status** (optional): `mastercard-cbs-check-status`
5. **Update Operations DB**: Reuse existing workers

#### 3.2 Error Handling
- Retry logic with exponential backoff
- Dead letter handling for unrecoverable errors
- Status tracking in Operations DB

### Phase 4: Integration (Week 4)

#### 4.1 Update Bulk Processor
Add Mastercard CBS as payment mode:

File: `repos/ph_template/helm/ph-ee-engine/bulk-processor/values.yaml`
```yaml
payment:
  modes:
    - CLOSEDLOOP
    - MOJALOOP
    - MASTERCARD_CBS
```

#### 4.2 Update CSV Format
Support new payment mode:
```csv
id,request_id,payment_mode,payee_identifier_type,payee_identifier,amount,currency,note
0,uuid1,MASTERCARD_CBS,MSISDN,0495822412,100.00,USD,Cross-border payment
```

#### 4.3 Workflow Routing
Update bulk processor to route `MASTERCARD_CBS` mode to `bulk_connector_mastercard_cbs-{tenant}`

### Phase 5: Data Loading & Testing (Week 4-5)

#### 5.1 Create Data Loading Scripts

**Script**: `src/utils/data-loading/load-mastercard-cbs-data.py`
```python
#!/usr/bin/env python3
"""
Load supplementary data for Mastercard CBS demo
"""

import pymysql
import csv

# 10 demo payees with realistic data
demo_payees = [
    {
        'msisdn': '0495822412',
        'account': '1234567890',
        'name': 'John Doe',
        'country': 'US',
        'bank_name': 'First National Bank',
        'swift': 'FNBAUS33',
        # ... additional fields
    },
    # ... 9 more payees
]

def load_data():
    # Connect to MySQL
    # INSERT INTO mastercard_cbs_supplementary_data
    pass
```

**Script**: `src/utils/data-loading/load-identity-mapper-cbs.py`
```python
#!/usr/bin/env python3
"""
Pre-populate Identity Account Mapper for CBS demo
"""

# Map 10 MSISDNs to accounts
# POST to identity-account-mapper API
```

#### 5.2 Create Test CSV Files
```bash
src/utils/data-loading/
├── bulk-gazelle-cbs-demo-10.csv
└── bulk-govstack-cbs-demo-10.csv
```

#### 5.3 Testing Script
**Script**: `src/utils/data-loading/test-cbs-flow.sh`
```bash
#!/bin/bash
# End-to-end test of CBS flow

# 1. Verify supplementary data loaded
# 2. Verify identity mapper populated
# 3. Submit batch via API
# 4. Monitor workflow progress
# 5. Check batch status
# 6. Verify payment status
```

---

## Data Model

### 1. Identity Account Mapper (Pre-populated)

**Table**: `identity_account_mapper.identity_details`
```
payee_identity: "0495822412"
registering_institution_id: "govstack"
```

**Table**: `identity_account_mapper.payment_modality_details`
```
destination_account: "1234567890"
institution_code: "MASTERCARD_CBS"
modality: "MSISDN"
```

### 2. Supplementary Data (New)

**Table**: `operations.mastercard_cbs_supplementary_data`

10 demo records with complete regulatory data.

### 3. Operations Database (Existing)

**Table**: `operations.transfers`
- Tracks all CBS payments
- Links to batch via `batch_id`
- Stores CBS payment reference in `external_id`

---

## API Specifications

### 1. Mastercard CBS Payment API

**Endpoint**: `POST https://sandbox.api.mastercard.com/send/v1/partners/transfer`

**Authentication**: OAuth 2.0 Bearer Token

**Request Payload**:
```json
{
  "partner_id": "string",
  "transaction_reference": "string",
  "payment_type": "PERSON_TO_PERSON",
  "amount": {
    "value": "100.00",
    "currency": "USD"
  },
  "sender": {
    "name": "Source Government Program",
    "address": {...},
    "account": {...}
  },
  "recipient": {
    "name": "John Doe",
    "address": {
      "line1": "123 Main St",
      "city": "New York",
      "state": "NY",
      "postal_code": "10001",
      "country": "US"
    },
    "account": {
      "number": "1234567890",
      "type": "CHECKING"
    },
    "bank": {
      "name": "First National Bank",
      "swift_bic": "FNBAUS33"
    }
  },
  "purpose_of_payment": "Government disbursement",
  "regulatory_compliance": {
    "source_of_funds": "GOVERNMENT",
    "purpose_code": "GOVT_TRANSFER"
  }
}
```

**Response**:
```json
{
  "payment_id": "CBS-12345-67890",
  "status": "PENDING",
  "transaction_reference": "string",
  "created_timestamp": "2025-01-15T10:30:00Z"
}
```

### 2. Mastercard Retrieve Payment API (Optional)

**Endpoint**: `GET https://sandbox.api.mastercard.com/send/v1/partners/transfer/{payment_id}`

**Response**:
```json
{
  "payment_id": "CBS-12345-67890",
  "status": "COMPLETED",
  "transaction_reference": "string",
  "amount": {...},
  "completion_timestamp": "2025-01-15T10:35:00Z",
  "recipient_confirmation": "string"
}
```

### 3. PaymentHub Batch Status APIs (Existing)

**Batch Summary**:
```
GET /api/v1/batch/summary?batchId={batchId}
```

**Batch Details**:
```
GET /api/v1/batch/detail?batchId={batchId}
```

**Batch Transactions**:
```
GET /api/v1/batch/transactions?batchId={batchId}
```

---

## Testing Strategy

### Unit Tests
- Zeebe worker logic
- Supplementary data service
- Mastercard API client
- Request/response mapping

### Integration Tests
- End-to-end workflow execution
- Database interactions
- Mastercard Sandbox API calls

### User Acceptance Testing (UAT)
1. **Scenario 1**: Single payment via API
2. **Scenario 2**: Batch of 10 payments (all pre-populated)
3. **Scenario 3**: Batch with missing supplementary data (error handling)
4. **Scenario 4**: Status retrieval from Mastercard
5. **Scenario 5**: Query batch status via Operations API

### Test Data
- 10 pre-populated payees in supplementary data table
- 10 corresponding entries in identity account mapper
- Test CSV files for batch submission

---

## Deployment Guide

### Prerequisites
1. Mastercard Sandbox account credentials
2. OAuth client ID and secret
3. Kubernetes cluster with PaymentHub deployed
4. MySQL database access

### Deployment Steps

#### 1. Build Docker Image
```bash
cd repos/ph-ee-connector-mastercard-cbs
mvn clean package
docker build -t openmf/ph-ee-connector-mastercard-cbs:v1.0.0 .
docker push openmf/ph-ee-connector-mastercard-cbs:v1.0.0
```

#### 2. Create Secrets
```bash
kubectl create secret generic mastercard-cbs-credentials \
  --from-literal=client-id='YOUR_CLIENT_ID' \
  --from-literal=client-secret='YOUR_CLIENT_SECRET' \
  -n paymenthub
```

#### 3. Deploy Connector
```bash
cd repos/ph_template/helm/ph-ee-engine
helm upgrade --install ph-ee . \
  --set connector-mastercard-cbs.enabled=true \
  --set connector-mastercard-cbs.image.tag=v1.0.0 \
  -n paymenthub
```

#### 4. Deploy BPMN Workflow
```bash
# Upload workflow to Zeebe
zbctl deploy orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn --address zeebe-gateway:26500
```

#### 5. Load Test Data
```bash
# Supplementary data
python3 src/utils/data-loading/load-mastercard-cbs-data.py

# Identity mapper
python3 src/utils/data-loading/load-identity-mapper-cbs.py
```

#### 6. Test Deployment
```bash
# Submit test batch
python3 src/utils/data-loading/submit-batch.py \
  -c ~/config.ini \
  -f bulk-gazelle-cbs-demo-10.csv \
  --payment-mode MASTERCARD_CBS
```

---

## Configuration

### Helm Values (values.yaml)

```yaml
connector-mastercard-cbs:
  enabled: true
  replicas: 1

  image:
    repository: openmf/ph-ee-connector-mastercard-cbs
    tag: v1.0.0
    pullPolicy: IfNotPresent

  resources:
    limits:
      cpu: 500m
      memory: 512Mi
    requests:
      cpu: 250m
      memory: 256Mi

  env:
    SPRING_PROFILES_ACTIVE: "production"
    ZEEBE_BROKER_CONTACTPOINT: "zeebe-zeebe-gateway:26500"

    # Mastercard Configuration
    MASTERCARD_API_URL: "https://sandbox.api.mastercard.com"
    MASTERCARD_AUTH_URL: "https://api.mastercard.com/oauth/token"
    MASTERCARD_PARTNER_ID: "MIFOS_GOVSTACK_DEMO"

    # Database
    DATASOURCE_URL: "jdbc:mysql://operationsdb-mysql:3306/operations"
    DATASOURCE_USERNAME: "root"

  secrets:
    mastercard:
      clientId: "mastercard-cbs-credentials"
      clientSecret: "mastercard-cbs-credentials"
    database:
      password: "operationsdb-secret"
```

### Application Properties (application.yaml)

```yaml
spring:
  application:
    name: ph-ee-connector-mastercard-cbs
  datasource:
    url: ${DATASOURCE_URL}
    username: ${DATASOURCE_USERNAME}
    password: ${DATASOURCE_PASSWORD}

zeebe:
  client:
    broker:
      gateway-address: ${ZEEBE_BROKER_CONTACTPOINT}
    worker:
      defaultType: mastercard-cbs
      threads: 2

mastercard:
  api:
    url: ${MASTERCARD_API_URL}
    auth-url: ${MASTERCARD_AUTH_URL}
    partner-id: ${MASTERCARD_PARTNER_ID}
  oauth:
    client-id: ${MASTERCARD_CLIENT_ID}
    client-secret: ${MASTERCARD_CLIENT_SECRET}
    grant-type: client_credentials
  payment:
    timeout-seconds: 30
    max-retries: 3
    retry-delay-seconds: 5
```

---

## Monitoring & Observability

### Logs
```bash
# Connector logs
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub

# Zeebe workflow logs
kubectl logs -f deployment/zeebe-zeebe -n paymenthub | grep mastercard-cbs
```

### Metrics
- Payment success/failure rates
- API latency to Mastercard
- Authentication token refresh rate
- Supplementary data cache hit rate

### Alerts
- Payment failures exceeding threshold
- Mastercard API unavailable
- Authentication failures
- Supplementary data not found

---

## Security Considerations

1. **Credentials Management**:
   - Store Mastercard credentials in Kubernetes secrets
   - Rotate credentials regularly
   - Never log credentials

2. **API Security**:
   - Use TLS for all Mastercard API calls
   - Validate SSL certificates
   - Implement request signing if required

3. **Data Privacy**:
   - Encrypt supplementary data at rest
   - Mask sensitive data in logs
   - Comply with PCI-DSS if applicable

4. **Access Control**:
   - RBAC for connector service
   - Audit logging for all CBS transactions
   - Restrict database access

---

## Limitations & Future Enhancements

### Demo Limitations
1. **10 Payees Only**: Supplementary data limited to demo dataset
2. **No Real Money**: Sandbox environment only
3. **No Warranty**: Post-UAT support not included
4. **Manual Data Loading**: No automated data sync
5. **Simplified Compliance**: Real production requires full KYC/AML

### Future Production Enhancements
1. **Full Supplementary Data Integration**: API to collect regulatory data
2. **Real-time Status Updates**: Webhook support from Mastercard
3. **Comprehensive Error Handling**: Full reconciliation and retry logic
4. **Compliance Workflows**: Automated sanctions screening, AML checks
5. **Multi-Currency Support**: FX rate integration
6. **Reporting**: Dashboard for CBS transaction monitoring
7. **Production Certification**: Mastercard certification process

---

## Support & Maintenance

### Issue Tracking
- File issues in GovStack sandbox repository
- Tag issues with `mastercard-cbs` label

### Documentation
- API integration guide
- Troubleshooting guide
- Runbook for common operations

### Knowledge Transfer
- Architecture walkthrough session
- Code review with development team
- UAT support during testing phase

---

## Timeline & Milestones

### Week 1: Foundation
- [ ] Connector service skeleton
- [ ] Helm chart structure
- [ ] Database schema
- [ ] Test data preparation

### Week 2: Core Development
- [ ] Authentication service
- [ ] Supplementary data service
- [ ] Payment service implementation
- [ ] Zeebe workers

### Week 3: Workflow & Integration
- [ ] BPMN workflow creation
- [ ] Bulk processor integration
- [ ] Error handling
- [ ] Unit tests

### Week 4: Testing & Deployment
- [ ] Data loading scripts
- [ ] Integration tests
- [ ] Docker image build
- [ ] Deployment to sandbox

### Week 5: UAT & Handover
- [ ] User acceptance testing
- [ ] Bug fixes
- [ ] Documentation
- [ ] Knowledge transfer

---

## Appendix

### A. Sample 10 Demo Payees

| MSISDN | Account | Name | Country | Bank | SWIFT |
|--------|---------|------|---------|------|-------|
| 0495822412 | 1234567890 | John Doe | US | First National Bank | FNBAUS33 |
| 0424942603 | 2345678901 | Jane Smith | UK | Barclays Bank | BARCGB22 |
| 0413356886 | 3456789012 | Carlos Rodriguez | ES | Banco Santander | BSCHESMM |
| ... | ... | ... | ... | ... | ... |

### B. API Request/Response Examples

[Detailed examples for all Mastercard CBS API calls]

### C. Troubleshooting Guide

**Issue**: Payment fails with "Supplementary data not found"
- **Cause**: MSISDN not in supplementary data table
- **Fix**: Verify data loaded, check MSISDN format

**Issue**: Authentication fails
- **Cause**: Invalid credentials or expired token
- **Fix**: Verify secrets, check Mastercard sandbox access

[Additional troubleshooting scenarios]

### D. References

- [Mastercard CBS API Documentation](https://developer.mastercard.com/cross-border-services/documentation/)
- [GovStack PayBB Specification](https://www.govstack.global/building-blocks/payments/)
- [Mifos Payment Hub EE Docs](https://mifos.gitbook.io/docs/payment-hub-ee)
- [Zeebe Documentation](https://docs.camunda.io/docs/components/zeebe/zeebe-overview/)

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-01-15 | Initial | Implementation plan created |

