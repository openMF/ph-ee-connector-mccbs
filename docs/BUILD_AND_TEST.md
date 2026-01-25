# Mastercard CBS Connector - Build and Test Guide

## Overview

This guide provides step-by-step instructions to build and test the Mastercard CBS components that are currently implemented.

## Current Implementation Status

### ✅ Completed Components

1. **CBS Connector** - `/home/tdaly/ph-ee-connector-mccbs/`
   - 12 Java source files
   - 8 Zeebe workers (352 lines)
   - Complete service layer
   - Configuration and models
   - Dockerfile for containerization

2. **BPMN Workflow** - `/home/tdaly/ph-ee-connector-mccbs/orchestration/`
   - Complete workflow with service tasks
   - Retry logic and error handling
   - Gateway conditions

3. **Database Schema** - `/home/tdaly/ph-ee-connector-mccbs/src/utils/data-loading/`
   - SQL schema with all fields
   - 10 demo payees included

### ⚠️ Partially Implemented

1. **Mock Mastercard Simulator** - `~/mastercard-cbs-simulator/`
   - Basic project structure only
   - No endpoint implementations yet
   - Requires controller and service implementation

### ⏳ Not Yet Implemented

1. Helm charts
2. Python data loading scripts
3. Integration tests
4. Unit tests

---

## Prerequisites

### Required
- **Java 17+** - `java -version` should show 17 or higher
- **Gradle 8.x** - `gradle --version` (or use Docker build)
- **Docker** (optional) - for containerized builds
- **MySQL** - for database schema
- **Zeebe** - for workflow deployment

### Optional
- **Maven 3.8+** - for simulator build (when implemented)
- **kubectl** - for Kubernetes deployment
- **zbctl** - for Zeebe workflow management

---

## Building the CBS Connector

### Method 1: Local Gradle Build

```bash
cd /home/tdaly/ph-ee-connector-mccbs

# Check Java version
java -version  # Should be 17+

# Build with system Gradle (no wrapper available)
gradle clean build

# Output JAR location
ls -lh build/libs/
# Expected: ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar
```

**Expected Output:**
```
BUILD SUCCESSFUL in 30s
5 actionable tasks: 5 executed
```

### Method 2: Docker Build

```bash
cd /home/tdaly/ph-ee-connector-mccbs

# Build Docker image (multi-stage build, includes Gradle)
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .

# Verify image created
docker images | grep mastercard-cbs
```

**Expected Output:**
```
ph-ee-connector-mastercard-cbs   1.0.0   <image-id>   X minutes ago   300MB
```

---

## Verifying the Build

### Check JAR Contents

```bash
cd /home/tdaly/ph-ee-connector-mccbs

# List classes in JAR
jar -tf build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar | grep MastercardCbsWorkers

# Expected: org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.class
```

### Verify Workers Implemented

```bash
# Count @JobWorker annotations (should be 8)
grep -c "@JobWorker" src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java
```

**Expected Output:** `8`

### List Worker Types

```bash
# Extract worker type names
grep "type = \"mastercard-cbs" src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java
```

**Expected Output:**
```
type = "mastercard-cbs-validate-input"
type = "mastercard-cbs-authenticate"
type = "mastercard-cbs-match-regulatory-data"
type = "mastercard-cbs-initiate-payment"
type = "mastercard-cbs-check-status"
type = "mastercard-cbs-update-operations"
type = "mastercard-cbs-retry-handler"
type = "mastercard-cbs-log-error"
```

---

## Testing Locally (Without Full Infrastructure)

### Test 1: Validate Application Starts

```bash
cd /home/tdaly/ph-ee-connector-mccbs

# Set minimal environment variables
export ZEEBE_BROKER_CONTACTPOINT=localhost:26500
export MASTERCARD_API_URL=http://localhost:8080
export DATASOURCE_URL=jdbc:mysql://localhost:3306/operations
export DATASOURCE_USERNAME=root
export DATASOURCE_PASSWORD=mysql

# Run application
gradle bootRun
```

**Expected Output (if Zeebe not available):**
```
...
Failed to connect to Zeebe broker at localhost:26500
...
Application will retry connection
```

This is expected without Zeebe running - it proves the app starts.

**Stop with:** `Ctrl+C`

### Test 2: Run with Docker (Isolated)

```bash
# Run connector in Docker (will fail to connect to Zeebe, but proves container works)
docker run --rm -p 8080:8080 \
  -e ZEEBE_BROKER_CONTACTPOINT=localhost:26500 \
  -e MASTERCARD_API_URL=http://localhost:8080 \
  -e DATASOURCE_URL=jdbc:mysql://localhost:3306/operations \
  ph-ee-connector-mastercard-cbs:1.0.0

# Check health endpoint (from another terminal)
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP"} or {"status":"DOWN"} with Zeebe details
```

---

## Database Schema Setup

### Load Schema with Demo Data

```bash
# Connect to MySQL
mysql -h <mysql-host> -u root -p

# Create database if needed
CREATE DATABASE IF NOT EXISTS operations;

# Load schema (includes 10 demo payees)
mysql -h <mysql-host> -u root -p operations < \
  /home/tdaly/ph-ee-connector-mccbs/src/utils/data-loading/mastercard-cbs-schema.sql
```

### Verify Data Loaded

```bash
mysql -h <mysql-host> -u root -p operations -e "
  SELECT COUNT(*) as total_payees
  FROM mastercard_cbs_supplementary_data
  WHERE is_active = true;
"
```

**Expected Output:** `total_payees: 10`

### Sample Data Query

```bash
mysql -h <mysql-host> -u root -p operations -e "
  SELECT
    payee_msisdn,
    beneficiary_full_name,
    bank_name,
    beneficiary_country_code
  FROM mastercard_cbs_supplementary_data
  LIMIT 3;
"
```

**Expected Output:**
```
+---------------+------------------------+---------------------+---------------------------+
| payee_msisdn  | beneficiary_full_name  | bank_name           | beneficiary_country_code |
+---------------+------------------------+---------------------+---------------------------+
| 0495822412    | John Doe               | First National Bank | US                        |
| 0424942603    | Jane Smith             | Barclays Bank       | GB                        |
| 0413356886    | Carlos Rodriguez       | Banco Santander     | ES                        |
+---------------+------------------------+---------------------+---------------------------+
```

---

## BPMN Workflow Testing

### View Workflow File

```bash
# View BPMN XML
cat /home/tdaly/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn | head -50

# Count service tasks (should be 8)
grep -c "serviceTask" /home/tdaly/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn
```

**Expected Output:** `8`

### Deploy to Zeebe (if Zeebe available)

```bash
# Set Zeebe address
export ZEEBE_ADDRESS=<zeebe-gateway>:26500

# Deploy workflow
zbctl deploy /home/tdaly/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn

# Verify deployment
zbctl list workflows | grep mastercard_cbs
```

**Expected Output:**
```
bulk_connector_mastercard_cbs-DFSPID | version: 1 | ...
```

### Visualize Workflow (Optional)

```bash
# Option 1: Use Camunda Modeler desktop app
# File → Open → /home/tdaly/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn

# Option 2: Use online viewer at https://bpmn.io/
# Upload the .bpmn file
```

---

## Mastercard Simulator Build (When Implemented)

### Current Status Check

```bash
cd ~/mastercard-cbs-simulator

# Check what files exist
find src -name "*.java" -type f

# Expected output (skeleton only):
# src/main/java/org/mifos/simulator/mastercard/MastercardCbsSimulatorApplication.java
# src/main/java/org/mifos/simulator/mastercard/model/OAuthTokenResponse.java
```

### Build Skeleton (Verify Maven Setup)

```bash
cd ~/mastercard-cbs-simulator

# Verify Maven can build
mvn clean compile

# Expected: BUILD SUCCESS (but no functionality)
```

**Note:** Controller and service implementations are needed before this is functional.

---

## Integration Testing Checklist

### Prerequisites for End-to-End Testing

Before you can run full integration tests, you need:

- [ ] Zeebe broker running and accessible
- [ ] MySQL database with operations schema
- [ ] Supplementary data table populated
- [ ] Mock Mastercard API implemented and running
- [ ] CBS connector deployed and connected to Zeebe
- [ ] BPMN workflow deployed to Zeebe
- [ ] PaymentHub bulk processor configured

### Test Scenario 1: Connector Registration

```bash
# Deploy connector to Kubernetes
kubectl create deployment ph-ee-connector-mastercard-cbs \
  --image=ph-ee-connector-mastercard-cbs:1.0.0 \
  -n paymenthub

# Check logs for worker registration
kubectl logs -n paymenthub deployment/ph-ee-connector-mastercard-cbs | grep "Registered worker"

# Expected: 8 lines showing each worker registered
```

### Test Scenario 2: Workflow Instance Creation

```bash
# Create test workflow instance (requires Zeebe + deployed workflow)
zbctl create instance bulk_connector_mastercard_cbs-DFSPID \
  --variables '{
    "transactionId": "test-001",
    "payeeIdentity": "0495822412",
    "amount": 100.00,
    "currency": "USD"
  }'

# Monitor workflow execution
zbctl list instances | grep mastercard_cbs
```

### Test Scenario 3: Database Lookup

```bash
# Verify supplementary data can be queried
mysql -h <mysql-host> -u root -p operations -e "
  SELECT * FROM mastercard_cbs_supplementary_data
  WHERE payee_msisdn = '0495822412';
"

# Expected: Full record for John Doe
```

---

## Troubleshooting

### Issue: Gradle not found

```bash
# Install Gradle
# Ubuntu/Debian:
sudo apt install gradle

# macOS:
brew install gradle

# Or use Docker build method instead
```

### Issue: Java version mismatch

```bash
# Check Java version
java -version

# If < 17, install Java 17:
# Ubuntu/Debian:
sudo apt install openjdk-17-jdk

# macOS:
brew install openjdk@17
```

### Issue: Build fails with dependency errors

```bash
# Clean Gradle cache
rm -rf ~/.gradle/caches/

# Retry build
cd /home/tdaly/ph-ee-connector-mccbs
gradle clean build --refresh-dependencies
```

### Issue: MySQL schema load fails

```bash
# Check SQL syntax
mysql -h <mysql-host> -u root -p operations --show-warnings < \
  /home/tdaly/ph-ee-connector-mccbs/src/utils/data-loading/mastercard-cbs-schema.sql

# If table exists, drop first:
mysql -h <mysql-host> -u root -p operations -e \
  "DROP TABLE IF EXISTS mastercard_cbs_supplementary_data;"
```

### Issue: Docker build fails

```bash
# Check Docker is running
docker ps

# Build with verbose output
docker build --progress=plain -t ph-ee-connector-mastercard-cbs:1.0.0 \
  /home/tdaly/ph-ee-connector-mccbs
```

---

## Code Quality Checks

### Check for TODOs

```bash
cd /home/tdaly/ph-ee-connector-mccbs
grep -r "TODO" src/main/java/ || echo "No TODOs found"
```

### Count Lines of Code

```bash
cd /home/tdaly/ph-ee-connector-mccbs

# Java source
find src/main/java -name "*.java" -exec wc -l {} + | tail -1

# BPMN
wc -l orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn

# SQL
wc -l src/utils/data-loading/mastercard-cbs-schema.sql
```

### Verify Dependencies

```bash
cd /home/tdaly/ph-ee-connector-mccbs

# List all dependencies
gradle dependencies | grep -E "spring-boot|zeebe|mysql"
```

---

## Next Steps After Build Success

1. **Implement Mock API**
   - Add controller classes to `~/mastercard-cbs-simulator/`
   - Implement OAuth endpoint
   - Implement payment submission endpoint
   - Implement status retrieval endpoint

2. **Create Helm Charts**
   - Connector deployment
   - Simulator deployment
   - ConfigMaps for configuration
   - Secrets for credentials

3. **Write Integration Tests**
   - End-to-end workflow tests
   - Worker unit tests
   - Database interaction tests

4. **Create Data Loading Scripts**
   - Python script for supplementary data
   - Python script for identity mapper
   - Batch CSV generators

5. **Deploy to Test Environment**
   - Deploy to Kubernetes cluster
   - Configure PaymentHub integration
   - Run UAT scenarios

---

## Quick Reference

### File Locations

| Component | Location |
|-----------|----------|
| Connector source | `/home/tdaly/ph-ee-connector-mccbs/src/main/java/` |
| Connector JAR | `/home/tdaly/ph-ee-connector-mccbs/build/libs/` |
| BPMN workflow | `/home/tdaly/ph-ee-connector-mccbs/orchestration/` |
| SQL schema | `/home/tdaly/ph-ee-connector-mccbs/src/utils/data-loading/` |
| Simulator source | `~/mastercard-cbs-simulator/src/main/java/` |
| Documentation | `/home/tdaly/ph-ee-connector-mccbs/docs/` |

### Build Commands

```bash
# Connector - Gradle
cd /home/tdaly/ph-ee-connector-mccbs && gradle clean build

# Connector - Docker
cd /home/tdaly/ph-ee-connector-mccbs && docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .

# Simulator - Maven (when implemented)
cd ~/mastercard-cbs-simulator && mvn clean package

# Database schema
mysql -h <host> -u root -p operations < /home/tdaly/ph-ee-connector-mccbs/src/utils/data-loading/mastercard-cbs-schema.sql
```

### Verification Commands

```bash
# Check JAR exists
ls -lh /home/tdaly/ph-ee-connector-mccbs/build/libs/*.jar

# Count workers
grep -c "@JobWorker" /home/tdaly/ph-ee-connector-mccbs/src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java

# Check database
mysql -h <host> -u root -p operations -e "SELECT COUNT(*) FROM mastercard_cbs_supplementary_data;"

# Check Docker image
docker images | grep mastercard-cbs
```

---

**Document Status**: Updated with actual implementation status
**Last Updated**: January 2026
**Next Review**: After simulator implementation complete
