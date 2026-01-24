# Mastercard CBS Demo - File Index

## Quick Reference

This document provides a complete index of all files and directories related to the Mastercard CBS demo implementation.

---

## 📚 Documentation Files

Located in: `docs/`

| File | Purpose | Audience | Size |
|------|---------|----------|------|
| [MASTERCARD_CBS_SUMMARY.md](MASTERCARD_CBS_SUMMARY.md) | Executive summary and overview | All stakeholders | 5 min read |
| [MASTERCARD_CBS_README.md](MASTERCARD_CBS_README.md) | Project reference guide | Developers, DevOps | 15 min read |
| [MASTERCARD_CBS_QUICKSTART.md](MASTERCARD_CBS_QUICKSTART.md) | Step-by-step setup guide | DevOps, QA | 20 min read |
| [MASTERCARD_CBS_IMPLEMENTATION_PLAN.md](MASTERCARD_CBS_IMPLEMENTATION_PLAN.md) | Detailed technical design | Developers, Architects | 30 min read |
| [MASTERCARD_CBS_IMPLEMENTATION_CHECKLIST.md](MASTERCARD_CBS_IMPLEMENTATION_CHECKLIST.md) | Implementation tracking | Project Manager, Team | Reference |
| [MASTERCARD_CBS_FILE_INDEX.md](MASTERCARD_CBS_FILE_INDEX.md) | This file - file directory | All | Reference |
| [GOVSTACK.md](GOVSTACK.md) | GovStack architecture notes | Developers | Background |

**Reading Order for New Team Members**:
1. Start with: `MASTERCARD_CBS_SUMMARY.md`
2. Then read: `MASTERCARD_CBS_README.md`
3. For setup: `MASTERCARD_CBS_QUICKSTART.md`
4. For details: `MASTERCARD_CBS_IMPLEMENTATION_PLAN.md`

---

## 💻 Source Code (To Be Implemented)

### Mastercard CBS Connector

**Location**: `repos/ph-ee-connector-mastercard-cbs/`

```
repos/ph-ee-connector-mastercard-cbs/
├── pom.xml                                             # Maven configuration
├── Dockerfile                                          # Docker image build
├── README.md                                           # Connector documentation
│
├── src/main/java/org/mifos/connector/mastercard/
│   ├── ConnectorMastercardCbsApplication.java         # Spring Boot main class
│   │
│   ├── config/
│   │   ├── MastercardConfig.java                      # Mastercard API config
│   │   ├── ZeebeConfig.java                           # Zeebe client config
│   │   ├── DatabaseConfig.java                        # MySQL config
│   │   └── RestTemplateConfig.java                    # HTTP client config
│   │
│   ├── model/
│   │   ├── SupplementaryData.java                     # Regulatory data entity
│   │   ├── MastercardPaymentRequest.java              # Payment request DTO
│   │   ├── MastercardPaymentResponse.java             # Payment response DTO
│   │   ├── MastercardPaymentStatus.java               # Status DTO
│   │   └── OAuthToken.java                            # OAuth token DTO
│   │
│   ├── service/
│   │   ├── MastercardAuthService.java                 # OAuth authentication
│   │   ├── SupplementaryDataService.java              # Data lookup from MySQL
│   │   ├── MastercardPaymentService.java              # Payment submission
│   │   └── PaymentStatusService.java                  # Status retrieval
│   │
│   └── zeebe/
│       ├── AuthenticateWorker.java                    # Worker: Get OAuth token
│       ├── MatchRegulatoryDataWorker.java             # Worker: Lookup supp data
│       ├── InitiatePaymentWorker.java                 # Worker: Submit payment
│       ├── CheckStatusWorker.java                     # Worker: Retrieve status
│       └── UpdateOperationsWorker.java                # Worker: Update DB
│
├── src/main/resources/
│   ├── application.yaml                               # Application config
│   └── logback-spring.xml                             # Logging config
│
└── src/test/java/org/mifos/connector/mastercard/
    ├── service/                                       # Service unit tests
    └── zeebe/                                         # Worker unit tests
```

**Key Classes**:
- `MastercardAuthService` - Handles OAuth token acquisition and caching
- `SupplementaryDataService` - Queries MySQL for beneficiary details
- `MastercardPaymentService` - Submits payments to CBS API
- `InitiatePaymentWorker` - Main Zeebe worker for payment processing

---

### Mock Mastercard CBS API Simulator

**Location**: `repos/mastercard-cbs-simulator/`

```
repos/mastercard-cbs-simulator/
├── pom.xml                                            # Maven configuration
├── Dockerfile                                         # Docker image build
├── README.md                                          # Simulator documentation
│
├── src/main/java/org/mifos/simulator/mastercard/
│   ├── MastercardCbsSimulatorApplication.java        # Spring Boot main class
│   │
│   ├── controller/
│   │   ├── OAuthController.java                      # POST /oauth/token
│   │   └── PaymentController.java                    # POST/GET /send/v1/partners/transfer
│   │
│   ├── model/
│   │   ├── OAuthTokenRequest.java                    # Token request
│   │   ├── OAuthTokenResponse.java                   # Token response
│   │   ├── PaymentRequest.java                       # Payment request
│   │   ├── PaymentResponse.java                      # Payment response
│   │   └── Payment.java                              # Payment entity (in-memory)
│   │
│   ├── service/
│   │   ├── TokenService.java                         # Token generation
│   │   └── PaymentService.java                       # Payment simulation logic
│   │
│   └── config/
│       └── SecurityConfig.java                        # Spring Security config
│
├── src/main/resources/
│   └── application.yaml                               # Simulator config
│
└── k8s/
    ├── deployment.yaml                                # Kubernetes deployment
    ├── service.yaml                                   # Kubernetes service
    └── configmap.yaml                                 # Configuration
```

**Key Endpoints**:
- `POST /oauth/token` - Generate OAuth access token
- `POST /send/v1/partners/transfer` - Submit payment
- `GET /send/v1/partners/transfer/{id}` - Get payment status

---

## 🎯 Workflows

**Location**: `orchestration/feel/`

| File | Description | Workflow ID |
|------|-------------|-------------|
| `bulk_connector_mastercard_cbs-DFSPID.bpmn` | CBS payment workflow | `bulk_connector_mastercard_cbs-{tenant}` |

**Service Tasks**:
1. `mastercard-cbs-authenticate` - Get OAuth token
2. `mastercard-cbs-match-regulatory-data` - Lookup supplementary data
3. `mastercard-cbs-initiate-payment` - Submit payment to CBS
4. `mastercard-cbs-check-status` - Retrieve payment status (optional)
5. `mastercard-cbs-update-operations` - Update Operations DB

---

## 🗄️ Database

**Location**: `src/utils/data-loading/`

### Schema

| File | Description |
|------|-------------|
| `mastercard-cbs-schema.sql` | CREATE TABLE for supplementary data |

**Table**: `mastercard_cbs_supplementary_data` in `operations` database

**Indexes**:
- `idx_msisdn` on `payee_msisdn`
- `idx_account` on `payee_account_number`

### Demo Data

| File | Description | Records |
|------|-------------|---------|
| `mastercard-cbs-demo-payees.csv` | 10 demo beneficiaries with full details | 10 |

**Columns**: MSISDN, account, name, address, bank, SWIFT, country, purpose, etc.

---

## 🔧 Data Loading Scripts

**Location**: `src/utils/data-loading/`

| Script | Purpose | Language |
|--------|---------|----------|
| `load-mastercard-cbs-supplementary-data.py` | Load demo payees into MySQL | Python |
| `load-identity-mapper-cbs-demo.py` | Populate identity account mapper | Python |

**Usage**:
```bash
python3 load-mastercard-cbs-supplementary-data.py --config ~/tomconfig.ini
python3 load-identity-mapper-cbs-demo.py --config ~/tomconfig.ini
```

---

## 🧪 Test Files

**Location**: `src/utils/data-loading/`

### Test Data

| File | Description | Scenario |
|------|-------------|----------|
| `bulk-cbs-demo-10.csv` | Happy path - all 10 payees | 10/10 success |
| `bulk-cbs-demo-partial-failure.csv` | Missing supplementary data | 8/10 success |

**CSV Format**:
```csv
id,request_id,payment_mode,payee_identifier_type,payee_identifier,amount,currency,note
0,uuid1,MASTERCARD_CBS,MSISDN,0495822412,100.00,USD,Test payment
```

### Test Scripts

| Script | Purpose | Usage |
|--------|---------|-------|
| `test-cbs-flow.sh` | End-to-end integration test | `./test-cbs-flow.sh --config ~/tomconfig.ini` |

**Test Scenarios**:
- `--scenario happy-path` - All succeed
- `--scenario partial-failure` - Some fail
- `--scenario api-errors` - Simulate CBS API errors

---

## ⚙️ Configuration

### Helm Charts

**Location**: `repos/ph_template/helm/ph-ee-engine/`

#### CBS Connector Chart

```
connector-mastercard-cbs/
├── Chart.yaml                                         # Chart metadata
├── values.yaml                                        # Default values
├── README.md                                          # Chart documentation
│
└── templates/
    ├── deployment.yaml                                # Pod deployment
    ├── service.yaml                                   # Service definition
    ├── secret.yaml                                    # Secrets (credentials)
    ├── serviceaccount.yaml                            # K8s service account
    ├── clusterrole.yaml                               # RBAC role
    └── clusterrolebinding.yaml                        # RBAC binding
```

**Install**:
```bash
helm upgrade ph-ee . \
  --set connector-mastercard-cbs.enabled=true \
  -n paymenthub
```

### PaymentHub Values Updates

**File**: `repos/ph_template/helm/ph-ee-engine/values.yaml`

**Changes**:
```yaml
connector-mastercard-cbs:
  enabled: true
  replicas: 1
  image:
    repository: openmf/ph-ee-connector-mastercard-cbs
    tag: 1.0.0
  env:
    MASTERCARD_API_URL: http://mastercard-simulator:8080
  # ... additional config
```

### Bulk Processor Updates

**File**: `repos/ph_template/helm/ph-ee-engine/bulk-processor/values.yaml`

**Changes**:
```yaml
payment:
  modes:
    - CLOSEDLOOP
    - MOJALOOP
    - MASTERCARD_CBS  # ADD THIS
```

---

## 🐳 Docker Images

### Images to Build

| Image | Dockerfile Location | Purpose |
|-------|---------------------|---------|
| `openmf/ph-ee-connector-mastercard-cbs` | `repos/ph-ee-connector-mastercard-cbs/Dockerfile` | CBS connector service |
| `mastercard-cbs-simulator` | `repos/mastercard-cbs-simulator/Dockerfile` | Mock CBS API |

**Build Commands**:
```bash
# Connector
cd repos/ph-ee-connector-mastercard-cbs
mvn clean package
docker build -t openmf/ph-ee-connector-mastercard-cbs:1.0.0 .

# Simulator
cd repos/mastercard-cbs-simulator
mvn clean package
docker build -t mastercard-cbs-simulator:1.0.0 .
```

---

## 📊 Monitoring & Logs

### Log Locations (Kubernetes)

```bash
# CBS Connector
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub

# Mock CBS API
kubectl logs -f deployment/mastercard-cbs-simulator -n mastercard-simulator

# Bulk Processor (CBS-related)
kubectl logs -f deployment/ph-ee-bulk-processor -n paymenthub | grep CBS

# Zeebe Workflows
kubectl logs -f deployment/zeebe-zeebe -n paymenthub | grep mastercard_cbs
```

### Database Queries

**File**: `docs/MASTERCARD_CBS_QUICKSTART.md#database-queries-for-monitoring`

**Key Queries**:
- Check batch progress
- Check individual transfers
- Check CBS payment references
- Check supplementary data coverage

---

## 🔐 Secrets

### Kubernetes Secrets

**Create**:
```bash
# Mastercard credentials
kubectl create secret generic mastercard-cbs-credentials \
  --from-literal=client-id='demo' \
  --from-literal=client-secret='demo' \
  -n paymenthub

# Database password
kubectl create secret generic mastercard-cbs-db \
  --from-literal=password='<db-password>' \
  -n paymenthub
```

**Usage**: Mounted as environment variables in connector deployment

---

## 📁 Directory Structure (Complete)

```
mifos-gazelle/
│
├── docs/                                              # Documentation
│   ├── MASTERCARD_CBS_SUMMARY.md                     # ⭐ Start here
│   ├── MASTERCARD_CBS_README.md                      # Project reference
│   ├── MASTERCARD_CBS_QUICKSTART.md                  # Setup guide
│   ├── MASTERCARD_CBS_IMPLEMENTATION_PLAN.md         # Technical design
│   ├── MASTERCARD_CBS_IMPLEMENTATION_CHECKLIST.md    # Progress tracking
│   ├── MASTERCARD_CBS_FILE_INDEX.md                  # This file
│   └── GOVSTACK.md                                   # GovStack notes
│
├── repos/                                             # Source code
│   ├── ph-ee-connector-mastercard-cbs/               # CBS connector service
│   │   ├── src/main/java/...                         # Java source
│   │   ├── src/main/resources/                       # Config files
│   │   ├── src/test/java/...                         # Unit tests
│   │   ├── pom.xml                                   # Maven build
│   │   └── Dockerfile                                # Docker build
│   │
│   ├── mastercard-cbs-simulator/                     # Mock CBS API
│   │   ├── src/main/java/...                         # Java source
│   │   ├── src/main/resources/                       # Config files
│   │   ├── k8s/                                      # K8s manifests
│   │   ├── pom.xml                                   # Maven build
│   │   └── Dockerfile                                # Docker build
│   │
│   └── ph_template/helm/ph-ee-engine/                # Helm charts
│       ├── connector-mastercard-cbs/                 # CBS connector chart
│       │   ├── Chart.yaml
│       │   ├── values.yaml
│       │   └── templates/
│       ├── bulk-processor/values.yaml                # Updated with CBS mode
│       └── values.yaml                               # Updated with CBS config
│
├── orchestration/feel/                                # BPMN workflows
│   └── bulk_connector_mastercard_cbs-DFSPID.bpmn     # CBS workflow
│
├── src/utils/data-loading/                            # Scripts and data
│   ├── mastercard-cbs-schema.sql                     # Database schema
│   ├── mastercard-cbs-demo-payees.csv                # Demo data (10 payees)
│   ├── load-mastercard-cbs-supplementary-data.py     # Data loader
│   ├── load-identity-mapper-cbs-demo.py              # Identity mapper setup
│   ├── bulk-cbs-demo-10.csv                          # Test batch CSV
│   ├── bulk-cbs-demo-partial-failure.csv             # Test failure scenario
│   └── test-cbs-flow.sh                              # Integration test
│
└── config/
    └── ph_values.yaml                                 # Updated PaymentHub config
```

---

## 🚀 Quick Start File Checklist

To get started with implementation, you'll need these files in order:

### Phase 1: Setup (Day 1)
1. ✅ Read: `docs/MASTERCARD_CBS_SUMMARY.md`
2. ✅ Read: `docs/MASTERCARD_CBS_README.md`
3. ✅ Read: `docs/MASTERCARD_CBS_QUICKSTART.md`
4. Create: `repos/mastercard-cbs-simulator/pom.xml`
5. Create: `repos/mastercard-cbs-simulator/src/main/java/.../MastercardCbsSimulatorApplication.java`

### Phase 2: Mock API (Week 1)
6. Create: All files in `repos/mastercard-cbs-simulator/src/main/java/`
7. Create: `repos/mastercard-cbs-simulator/Dockerfile`
8. Create: `repos/mastercard-cbs-simulator/k8s/*.yaml`
9. Test: Build and deploy simulator

### Phase 3: Connector (Week 2)
10. Create: `repos/ph-ee-connector-mastercard-cbs/pom.xml`
11. Create: All files in `repos/ph-ee-connector-mastercard-cbs/src/main/java/`
12. Create: `repos/ph-ee-connector-mastercard-cbs/src/main/resources/application.yaml`
13. Test: Build connector locally

### Phase 4: Database (Week 2)
14. Create: `src/utils/data-loading/mastercard-cbs-schema.sql`
15. Create: `src/utils/data-loading/mastercard-cbs-demo-payees.csv`
16. Create: `src/utils/data-loading/load-mastercard-cbs-supplementary-data.py`
17. Run: Load data into MySQL

### Phase 5: Workflow (Week 3)
18. Create: `orchestration/feel/bulk_connector_mastercard_cbs-DFSPID.bpmn`
19. Deploy: Upload to Zeebe
20. Test: Create workflow instance

### Phase 6: Integration (Week 3-4)
21. Create: `repos/ph_template/helm/ph-ee-engine/connector-mastercard-cbs/`
22. Update: `repos/ph_template/helm/ph-ee-engine/values.yaml`
23. Update: `repos/ph_template/helm/ph-ee-engine/bulk-processor/values.yaml`
24. Deploy: Full PaymentHub with CBS connector

### Phase 7: Testing (Week 4-5)
25. Create: `src/utils/data-loading/bulk-cbs-demo-10.csv`
26. Create: `src/utils/data-loading/test-cbs-flow.sh`
27. Run: End-to-end tests
28. Execute: UAT test cases

---

## 📞 Getting Help

### Documentation Issues
- **Missing information**: Check other planning docs
- **Unclear instructions**: Refer to Quick Start guide
- **Architecture questions**: See Implementation Plan

### Implementation Issues
- **Code structure**: Follow existing connector patterns (connector-slcb, connector-mojaloop)
- **BPMN workflow**: Reference existing bulk_connector workflows
- **Deployment**: Use existing Helm chart patterns

### File Not Found
- **Check this index**: All files listed above
- **Implementation status**: See Implementation Checklist
- **Future files**: May not exist yet, refer to plan

---

## 📝 Notes

### Files That Exist
- ✅ All documentation (6 files in docs/)
- ✅ Project structure defined
- ✅ File locations specified

### Files To Be Created
- ⏳ Source code (Java)
- ⏳ BPMN workflow
- ⏳ Helm charts
- ⏳ Data loading scripts
- ⏳ Test files

### External Dependencies
- Existing PaymentHub components (unchanged)
- Existing workflows (unchanged)
- Existing database tables (extended)

---

## 🔄 Updates & Maintenance

**Document Version**: 1.0
**Last Updated**: 2025-12-21

**Update Policy**:
- Update this index when new files are added
- Keep file paths accurate
- Document file moves or renames
- Add new sections as needed

**Ownership**:
- Maintainer: CBS Demo Implementation Team
- Review Frequency: Weekly during development
- Final Review: Before project handover

---

## ✅ Verification Checklist

Use this to verify all files are in place:

### Documentation
- [x] MASTERCARD_CBS_SUMMARY.md
- [x] MASTERCARD_CBS_README.md
- [x] MASTERCARD_CBS_QUICKSTART.md
- [x] MASTERCARD_CBS_IMPLEMENTATION_PLAN.md
- [x] MASTERCARD_CBS_IMPLEMENTATION_CHECKLIST.md
- [x] MASTERCARD_CBS_FILE_INDEX.md (this file)

### Source Code (Connector)
- [ ] pom.xml
- [ ] Dockerfile
- [ ] Config classes (4)
- [ ] Model classes (5)
- [ ] Service classes (4)
- [ ] Zeebe workers (5)
- [ ] Unit tests

### Source Code (Simulator)
- [ ] pom.xml
- [ ] Dockerfile
- [ ] Controller classes (2)
- [ ] Model classes (5)
- [ ] Service classes (2)
- [ ] K8s manifests (3)

### Workflows
- [ ] bulk_connector_mastercard_cbs-DFSPID.bpmn

### Database
- [ ] mastercard-cbs-schema.sql
- [ ] mastercard-cbs-demo-payees.csv

### Scripts
- [ ] load-mastercard-cbs-supplementary-data.py
- [ ] load-identity-mapper-cbs-demo.py
- [ ] test-cbs-flow.sh

### Test Data
- [ ] bulk-cbs-demo-10.csv
- [ ] bulk-cbs-demo-partial-failure.csv

### Helm Charts
- [ ] connector-mastercard-cbs/Chart.yaml
- [ ] connector-mastercard-cbs/values.yaml
- [ ] connector-mastercard-cbs/templates/*.yaml

### Configuration Updates
- [ ] values.yaml (PaymentHub)
- [ ] bulk-processor/values.yaml

---

**End of File Index**

For questions or clarifications on file locations, refer to the Quick Start guide or Implementation Plan.
