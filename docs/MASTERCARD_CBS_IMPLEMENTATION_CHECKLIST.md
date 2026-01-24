# Mastercard CBS Demo - Implementation Checklist

## Overview

This checklist tracks the implementation of the Mastercard CBS demo connector for PaymentHub EE. Use this to track progress and ensure all components are delivered.

**Project Goal**: Enable GovStack → PaymentHub → Mastercard CBS cross-border payment flow

---

## Phase 1: Foundation & Planning ✓

- [x] Review requirements and scope
- [x] Document architecture design
- [x] Create implementation plan
- [x] Define component structure
- [x] Design database schema
- [x] Create project documentation

**Deliverables**:
- [MASTERCARD_CBS_IMPLEMENTATION_PLAN.md](MASTERCARD_CBS_IMPLEMENTATION_PLAN.md)
- [MASTERCARD_CBS_QUICKSTART.md](MASTERCARD_CBS_QUICKSTART.md)
- [MASTERCARD_CBS_README.md](MASTERCARD_CBS_README.md)

---

## Phase 2: Mock Mastercard CBS API Simulator

### 2.1 Project Setup
- [ ] Create Maven project structure
- [ ] Configure Spring Boot dependencies
- [ ] Set up logging configuration
- [ ] Create application.yaml

**Files**:
- `repos/mastercard-cbs-simulator/pom.xml`
- `repos/mastercard-cbs-simulator/src/main/resources/application.yaml`

### 2.2 OAuth Implementation
- [ ] Create OAuthTokenRequest model
- [ ] Create OAuthTokenResponse model
- [ ] Implement OAuth controller
- [ ] Add token generation service
- [ ] Configure Spring Security

**Files**:
- `model/OAuthTokenRequest.java`
- `model/OAuthTokenResponse.java`
- `controller/OAuthController.java`
- `service/TokenService.java`
- `config/SecurityConfig.java`

### 2.3 Payment API Implementation
- [ ] Create PaymentRequest model
- [ ] Create PaymentResponse model
- [ ] Create Payment entity (in-memory)
- [ ] Implement payment submission endpoint
- [ ] Implement payment status endpoint
- [ ] Add payment simulation logic

**Files**:
- `model/PaymentRequest.java`
- `model/PaymentResponse.java`
- `model/Payment.java`
- `controller/PaymentController.java`
- `service/PaymentService.java`

### 2.4 Docker & Kubernetes
- [ ] Create Dockerfile
- [ ] Create Kubernetes deployment.yaml
- [ ] Create Kubernetes service.yaml
- [ ] Create ConfigMap for configuration
- [ ] Test local Docker build
- [ ] Test Kubernetes deployment

**Files**:
- `Dockerfile`
- `k8s/deployment.yaml`
- `k8s/service.yaml`
- `k8s/configmap.yaml`

**Commands to Test**:
```bash
# Build
cd repos/mastercard-cbs-simulator
mvn clean package
docker build -t mastercard-cbs-simulator:1.0.0 .

# Test locally
docker run -p 8080:8080 mastercard-cbs-simulator:1.0.0

# Test OAuth
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=demo&client_secret=demo"

# Deploy to K8s
kubectl create namespace mastercard-simulator
kubectl apply -f k8s/ -n mastercard-simulator
kubectl get pods -n mastercard-simulator
```

---

## Phase 3: CBS Connector Service

### 3.1 Project Setup
- [ ] Create Maven project structure
- [ ] Configure Spring Boot dependencies
- [ ] Add Zeebe Spring Client dependency
- [ ] Add MySQL JDBC dependency
- [ ] Create application.yaml
- [ ] Configure logging

**Files**:
- `repos/ph-ee-connector-mastercard-cbs/pom.xml`
- `src/main/resources/application.yaml`
- `src/main/resources/logback-spring.xml`

### 3.2 Configuration Classes
- [ ] Create MastercardConfig
- [ ] Create ZeebeConfig
- [ ] Create DatabaseConfig
- [ ] Create RestTemplateConfig
- [ ] Add configuration validation

**Files**:
- `config/MastercardConfig.java`
- `config/ZeebeConfig.java`
- `config/DatabaseConfig.java`
- `config/RestTemplateConfig.java`

### 3.3 Data Models
- [ ] Create SupplementaryData entity
- [ ] Create MastercardPaymentRequest DTO
- [ ] Create MastercardPaymentResponse DTO
- [ ] Create MastercardPaymentStatus DTO
- [ ] Create OAuthToken DTO
- [ ] Add Jackson annotations

**Files**:
- `model/SupplementaryData.java`
- `model/MastercardPaymentRequest.java`
- `model/MastercardPaymentResponse.java`
- `model/MastercardPaymentStatus.java`
- `model/OAuthToken.java`

### 3.4 Service Layer
- [ ] Create MastercardAuthService (OAuth)
- [ ] Create SupplementaryDataService (MySQL)
- [ ] Create MastercardPaymentService (API calls)
- [ ] Add error handling
- [ ] Add retry logic
- [ ] Add comprehensive logging

**Files**:
- `service/MastercardAuthService.java`
- `service/SupplementaryDataService.java`
- `service/MastercardPaymentService.java`
- `service/PaymentStatusService.java`

### 3.5 Zeebe Workers
- [ ] Implement AuthenticateWorker
- [ ] Implement MatchRegulatoryDataWorker
- [ ] Implement InitiatePaymentWorker
- [ ] Implement CheckStatusWorker
- [ ] Implement UpdateOperationsWorker
- [ ] Add worker error handling
- [ ] Add worker logging

**Files**:
- `zeebe/AuthenticateWorker.java`
- `zeebe/MatchRegulatoryDataWorker.java`
- `zeebe/InitiatePaymentWorker.java`
- `zeebe/CheckStatusWorker.java`
- `zeebe/UpdateOperationsWorker.java`

**Worker Summary**:
| Worker | Type | Input Variables | Output Variables | Error Handling |
|--------|------|-----------------|------------------|----------------|
| Authenticate | `mastercard-cbs-authenticate` | - | `cbsAccessToken`, `cbsTokenExpiry` | Retry 3x |
| Match Data | `mastercard-cbs-match-regulatory-data` | `payeeIdentity` | `supplementaryData` | Fail if not found |
| Initiate | `mastercard-cbs-initiate-payment` | `payeeAccountNumber`, `amount`, `currency`, `supplementaryData` | `cbsPaymentId`, `cbsPaymentStatus` | Retry 3x |
| Check Status | `mastercard-cbs-check-status` | `cbsPaymentId` | `cbsPaymentStatus`, `cbsPaymentDetails` | Retry 2x |
| Update Ops | `mastercard-cbs-update-operations` | `transactionId`, `cbsPaymentId`, `cbsPaymentStatus` | - | Retry 3x |

### 3.6 Unit Tests
- [ ] Test MastercardAuthService
- [ ] Test SupplementaryDataService
- [ ] Test MastercardPaymentService
- [ ] Test Zeebe workers
- [ ] Test error scenarios
- [ ] Test retry logic

**Files**:
- `src/test/java/org/mifos/connector/mastercard/service/`
- `src/test/java/org/mifos/connector/mastercard/zeebe/`

### 3.7 Docker & Helm
- [ ] Create Dockerfile
- [ ] Create Helm chart structure
- [ ] Create Chart.yaml
- [ ] Create values.yaml
- [ ] Create deployment.yaml template
- [ ] Create service.yaml template
- [ ] Create secret.yaml template
- [ ] Create serviceaccount.yaml
- [ ] Create clusterrole.yaml
- [ ] Test Helm chart deployment

**Files**:
- `Dockerfile`
- `repos/ph_template/helm/ph-ee-engine/connector-mastercard-cbs/Chart.yaml`
- `repos/ph_template/helm/ph-ee-engine/connector-mastercard-cbs/values.yaml`
- `repos/ph_template/helm/ph-ee-engine/connector-mastercard-cbs/templates/*.yaml`

**Commands to Test**:
```bash
# Build
cd repos/ph-ee-connector-mastercard-cbs
mvn clean package
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .

# Test Helm chart
cd repos/ph_template/helm/ph-ee-engine
helm lint connector-mastercard-cbs/
helm template connector-mastercard-cbs/ --debug

# Deploy
helm upgrade ph-ee . \
  --set connector-mastercard-cbs.enabled=true \
  -n paymenthub

# Verify
kubectl get pods -n paymenthub | grep mastercard-cbs
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub
```

---

## Phase 4: BPMN Workflow

### 4.1 Workflow Design
- [ ] Design workflow flow diagram
- [ ] Define service tasks
- [ ] Define workflow variables
- [ ] Define error handling paths
- [ ] Define retry logic
- [ ] Document workflow

**Documentation**:
- Flow diagram in implementation plan
- Service task definitions
- Variable mapping table

### 4.2 BPMN Implementation
- [ ] Create BPMN file in Camunda Modeler
- [ ] Add start event
- [ ] Add authenticate service task
- [ ] Add match data service task
- [ ] Add initiate payment service task
- [ ] Add check status service task (optional)
- [ ] Add update operations service task
- [ ] Add error boundary events
- [ ] Add retry logic
- [ ] Add end event
- [ ] Validate BPMN XML

**File**:
- `orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn`

**Service Tasks**:
```xml
<!-- Example structure -->
<serviceTask id="authenticate" name="Authenticate">
  <extensionElements>
    <zeebe:taskDefinition type="mastercard-cbs-authenticate" />
  </extensionElements>
</serviceTask>

<serviceTask id="matchData" name="Match Regulatory Data">
  <extensionElements>
    <zeebe:taskDefinition type="mastercard-cbs-match-regulatory-data" />
  </extensionElements>
</serviceTask>

<!-- Add error handling -->
<boundaryEvent id="authError" attachedToRef="authenticate">
  <errorEventDefinition errorRef="Error_Auth" />
</boundaryEvent>
```

### 4.3 Workflow Testing
- [ ] Deploy workflow to Zeebe
- [ ] Create test instance with mock data
- [ ] Verify each service task executes
- [ ] Test error handling
- [ ] Test retry logic
- [ ] Verify workflow completes successfully

**Commands**:
```bash
# Deploy workflow
zbctl deploy orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn \
  --address zeebe-gateway:26500

# List workflows
zbctl list workflows --address zeebe-gateway:26500

# Create test instance
zbctl create instance bulk_connector_mastercard_cbs-govstack \
  --variables '{"payeeIdentity":"0495822412","amount":100,"currency":"USD"}' \
  --address zeebe-gateway:26500

# Monitor
zbctl list instances --address zeebe-gateway:26500
```

---

## Phase 5: Database & Data Loading

### 5.1 Database Schema
- [ ] Create supplementary data table SQL
- [ ] Add indexes for performance
- [ ] Add foreign key constraints (if any)
- [ ] Document schema
- [ ] Test schema creation

**File**:
- `src/utils/data-loading/mastercard-cbs-schema.sql`

**Commands**:
```bash
# Create table
mysql -h <mysql-host> -u root -p operations < mastercard-cbs-schema.sql

# Verify
mysql -h <mysql-host> -u root -p operations -e "DESCRIBE mastercard_cbs_supplementary_data;"
```

### 5.2 Demo Payees Data
- [ ] Create CSV with 10 demo payees
- [ ] Include diverse countries/banks
- [ ] Validate all required fields
- [ ] Test data realism

**File**:
- `src/utils/data-loading/mastercard-cbs-demo-payees.csv`

**Sample Payees**:
| MSISDN | Country | Bank | Account |
|--------|---------|------|---------|
| 0495822412 | US | First National Bank | 1001234567 |
| 0424942603 | GB | Barclays | 2002345678 |
| 0413356886 | ES | Santander | 3003456789 |
| ... | ... | ... | ... |

### 5.3 Data Loading Script (Supplementary Data)
- [ ] Create Python script
- [ ] Add database connection
- [ ] Add CSV parsing
- [ ] Add data validation
- [ ] Add INSERT statements
- [ ] Add error handling
- [ ] Add logging
- [ ] Test script execution

**File**:
- `src/utils/data-loading/load-mastercard-cbs-supplementary-data.py`

**Usage**:
```bash
python3 load-mastercard-cbs-supplementary-data.py \
  --config ~/tomconfig.ini \
  --demo-data mastercard-cbs-demo-payees.csv \
  --verbose
```

### 5.4 Identity Mapper Loading Script
- [ ] Create Python script
- [ ] Map 10 MSISDNs to accounts
- [ ] Set institution_code = "MASTERCARD_CBS"
- [ ] Add error handling
- [ ] Test script execution

**File**:
- `src/utils/data-loading/load-identity-mapper-cbs-demo.py`

**Usage**:
```bash
python3 load-identity-mapper-cbs-demo.py \
  --config ~/tomconfig.ini \
  --institution-id MASTERCARD_CBS \
  --verbose
```

### 5.5 Verification Queries
- [ ] Document verification SQL queries
- [ ] Test supplementary data count
- [ ] Test identity mapper entries
- [ ] Test data integrity

**Queries**:
```sql
-- Verify supplementary data
SELECT COUNT(*) FROM mastercard_cbs_supplementary_data;

-- Verify identity mapper
SELECT COUNT(*)
FROM identity_details id
JOIN payment_modality_details pm ON id.master_id = pm.master_id
WHERE pm.institution_code = 'MASTERCARD_CBS';

-- Check specific MSISDN
SELECT *
FROM mastercard_cbs_supplementary_data
WHERE payee_msisdn = '0495822412';
```

---

## Phase 6: Integration & Configuration

### 6.1 Bulk Processor Updates
- [ ] Update values.yaml with CBS payment mode
- [ ] Test payment mode routing
- [ ] Verify workflow selection logic

**File**:
- `repos/ph_template/helm/ph-ee-engine/bulk-processor/values.yaml`

**Changes**:
```yaml
payment:
  modes:
    - CLOSEDLOOP
    - MOJALOOP
    - MASTERCARD_CBS  # ADD THIS
```

### 6.2 PaymentHub Helm Values
- [ ] Add connector-mastercard-cbs section
- [ ] Configure environment variables
- [ ] Configure secrets
- [ ] Configure resources (CPU/memory)
- [ ] Test Helm upgrade

**File**:
- `repos/ph_template/helm/ph-ee-engine/values.yaml`

### 6.3 Secrets Management
- [ ] Create Kubernetes secret for Mastercard credentials
- [ ] Create secret for database password
- [ ] Mount secrets in connector deployment
- [ ] Test secret access

**Commands**:
```bash
kubectl create secret generic mastercard-cbs-credentials \
  --from-literal=client-id='demo' \
  --from-literal=client-secret='demo' \
  -n paymenthub

kubectl create secret generic mastercard-cbs-db \
  --from-literal=password='<db-password>' \
  -n paymenthub
```

---

## Phase 7: Testing & Validation

### 7.1 Test CSV Files
- [ ] Create test batch CSV (10 payees)
- [ ] Create test batch CSV (partial failure)
- [ ] Create test batch CSV (invalid MSISDNs)
- [ ] Document CSV format

**Files**:
- `src/utils/data-loading/bulk-cbs-demo-10.csv`
- `src/utils/data-loading/bulk-cbs-demo-partial-failure.csv`

### 7.2 End-to-End Test Script
- [ ] Create bash test script
- [ ] Add pre-flight checks
- [ ] Add batch submission
- [ ] Add progress monitoring
- [ ] Add status validation
- [ ] Add cleanup

**File**:
- `src/utils/data-loading/test-cbs-flow.sh`

**Usage**:
```bash
./test-cbs-flow.sh --config ~/tomconfig.ini --scenario happy-path
./test-cbs-flow.sh --config ~/tomconfig.ini --scenario partial-failure
```

### 7.3 UAT Test Cases
- [ ] Test Case 1: Happy path (10/10 success)
- [ ] Test Case 2: Partial failure (missing supp data)
- [ ] Test Case 3: CBS API errors
- [ ] Test Case 4: Status retrieval
- [ ] Test Case 5: Batch status queries
- [ ] Document test results

**Test Matrix**:
| Test Case | Description | Expected Result | Status |
|-----------|-------------|-----------------|--------|
| TC-1 | All 10 payees with valid data | 10/10 completed | ☐ |
| TC-2 | 8 valid, 2 missing supp data | 8/10 completed, 2/10 failed | ☐ |
| TC-3 | Simulate CBS API errors | Retries then fails | ☐ |
| TC-4 | Status retrieval from CBS | Status updated in DB | ☐ |
| TC-5 | Query batch APIs | Correct totals returned | ☐ |

### 7.4 Performance Testing
- [ ] Test batch processing time (10 payments)
- [ ] Test concurrent batch processing
- [ ] Monitor resource usage
- [ ] Document performance metrics

**Metrics**:
- Target: 10 payments in < 2 minutes
- Memory: < 512Mi per pod
- CPU: < 0.5 cores per pod

---

## Phase 8: Documentation & Handover

### 8.1 Documentation
- [x] Implementation plan
- [x] Quick start guide
- [x] README
- [x] Implementation checklist (this file)
- [ ] Architecture diagrams
- [ ] API documentation
- [ ] Troubleshooting guide
- [ ] Runbook

### 8.2 Code Documentation
- [ ] JavaDoc for all public classes/methods
- [ ] Inline comments for complex logic
- [ ] README in each repo
- [ ] SQL schema comments

### 8.3 Knowledge Transfer
- [ ] Schedule architecture walkthrough
- [ ] Code walkthrough session
- [ ] Demo recording
- [ ] Q&A session
- [ ] Document common issues

### 8.4 Handover Checklist
- [ ] All code committed to Git
- [ ] Docker images pushed to registry
- [ ] Helm charts tested
- [ ] Documentation complete
- [ ] UAT completed and signed off
- [ ] Known issues documented
- [ ] Support contact information provided

---

## Phase 9: Deployment to GovStack Sandbox

### 9.1 Pre-Deployment
- [ ] Verify sandbox access
- [ ] Verify database credentials
- [ ] Verify Kubernetes access
- [ ] Backup existing configuration

### 9.2 Deployment Steps
- [ ] Deploy mock Mastercard API
- [ ] Create supplementary data table
- [ ] Load demo data
- [ ] Load identity mapper data
- [ ] Deploy CBS connector
- [ ] Deploy BPMN workflow
- [ ] Verify all pods running
- [ ] Run smoke tests

### 9.3 Post-Deployment Validation
- [ ] Test batch submission via API
- [ ] Verify payment processing
- [ ] Check batch status APIs
- [ ] Monitor logs for errors
- [ ] Validate data integrity

### 9.4 Production Readiness (Future)
- [ ] Replace mock API with real Mastercard Sandbox
- [ ] Production OAuth credentials
- [ ] SSL/TLS certificates
- [ ] Monitoring and alerting
- [ ] Backup and disaster recovery
- [ ] SLA definitions
- [ ] Support procedures

---

## Success Criteria

### Functional Requirements
- ✓ Receive GovStack PayBB-compliant disbursement instructions
- ✓ Resolve payee identities via Identity Account Mapper
- ✓ Match regulatory data from pre-populated table (10 payees)
- ✓ Submit payments to Mastercard CBS API
- ✓ Track payment status
- ✓ Provide batch status via existing APIs
- ✓ (Optional) Retrieve payment status from Mastercard

### Non-Functional Requirements
- ✓ Process 10 payments in < 2 minutes
- ✓ 95%+ success rate for valid payees
- ✓ Proper error handling and logging
- ✓ Clean, documented code
- ✓ Deployable via Helm charts
- ✓ Works in GovStack sandbox environment

### Documentation Requirements
- ✓ Implementation plan
- ✓ Quick start guide
- ✓ Architecture documentation
- ✓ Testing documentation
- ✓ Troubleshooting guide

---

## Current Status

**Overall Progress**: Planning Complete, Implementation Ready to Start

**Completed**:
- ✅ Requirements analysis
- ✅ Architecture design
- ✅ Implementation plan
- ✅ Documentation structure
- ✅ Database schema design

**In Progress**:
- 🔄 Mock API implementation
- 🔄 Connector service implementation
- 🔄 BPMN workflow creation

**Not Started**:
- ⏳ Data loading scripts
- ⏳ Testing scripts
- ⏳ UAT execution
- ⏳ Deployment to sandbox

---

## Next Steps

1. **Immediate** (Today):
   - Start mock Mastercard API implementation
   - Set up connector service project structure
   - Create database schema SQL file

2. **This Week**:
   - Complete mock API simulator
   - Implement connector service core logic
   - Create BPMN workflow
   - Write data loading scripts

3. **Next Week**:
   - Deploy to local test environment
   - Execute UAT test cases
   - Fix bugs and issues
   - Finalize documentation

4. **Following Week**:
   - Deploy to GovStack sandbox
   - Conduct final UAT with stakeholders
   - Knowledge transfer session
   - Project handover

---

## Risk & Issues

### Risks
| Risk | Impact | Mitigation | Status |
|------|--------|------------|--------|
| Mastercard API spec incomplete | High | Using mock API for demo | Mitigated |
| Identity mapper compatibility | Medium | Following existing GovStack patterns | Low risk |
| Performance issues with 10+ payees | Low | Demo limited to 10 payees | Mitigated |
| Deployment complexity | Medium | Using existing Helm chart patterns | Low risk |

### Open Issues
| Issue | Priority | Owner | Status |
|-------|----------|-------|--------|
| Confirm Mastercard API access approach | High | Product | Resolved (mock API) |
| Finalize supplementary data fields | Medium | Tech Lead | Resolved |
| Verify GovStack sandbox access | Medium | DevOps | Pending |

---

## Sign-Off

### Development Team
- [ ] Code complete and tested
- [ ] Documentation complete
- [ ] UAT passed
- [ ] Deployment successful
- [ ] Knowledge transfer completed

**Date**: _____________
**Signature**: _____________

### Product Owner
- [ ] Requirements met
- [ ] Demo successful
- [ ] Documentation adequate
- [ ] Ready for user acceptance

**Date**: _____________
**Signature**: _____________

### GovStack Stakeholder
- [ ] Integration tested
- [ ] Performance acceptable
- [ ] Documentation reviewed
- [ ] Accepted for demo

**Date**: _____________
**Signature**: _____________

---

**Document Version**: 1.0
**Last Updated**: 2025-12-21
**Maintained By**: PaymentHub CBS Demo Team
