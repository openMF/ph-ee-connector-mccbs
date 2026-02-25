# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot + Zeebe microservice connector bridging Mifos Payment Hub EE with Mastercard Cross-Border Services (CBS). Uses workflow orchestration with 8 Zeebe workers for payment validation, OAuth authentication, regulatory data enrichment, and payment submission.

## Build Commands

```bash
# Build JAR
gradle clean build

# Build Docker image
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .

# Run locally (requires Zeebe, MySQL)
gradle bootRun

# Run tests
gradle test
```

## boot run and mifs gazelle integration 
- use sudo ./run.sh -a mastercard-demo to deploy or redeploy  from mifos gazelle 
- use ./submit-batch.py -c ~/tomconfig.ini -f bulk-gazelle-mastercard-6.csv --tenant greenbank --govstack --registering-institution greenbank to test 
- use ./src/utils/data-loading/load-mastercard-supplementary-data.sh for loading the supplementary data as specified in ../docs/JIRA_REQUIREMENTS.md 


## Architecture

### Zeebe Workers

All 8 workers are in `src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java`:

| Worker Type | Purpose |
|-------------|---------|
| `mastercard-cbs-validate-input` | Validate transaction fields |
| `mastercard-cbs-authenticate` | Get OAuth access token |
| `mastercard-cbs-match-regulatory-data` | Lookup payee data from MySQL |
| `mastercard-cbs-initiate-payment` | Submit payment to CBS API |
| `mastercard-cbs-check-status` | Retrieve payment status |
| `mastercard-cbs-update-operations` | Update Operations DB (TODO) |
| `mastercard-cbs-retry-handler` | Manage retry counts |
| `mastercard-cbs-log-error` | Log failures |

### Service Layer

- `MastercardAuthService` - OAuth token management with 3000s cache
- `MastercardPaymentService` - Payment submission/status (30s timeout, 3 retries)
- `SupplementaryDataService` - DB queries for payee regulatory data

### Key Configuration

Environment variables (see `src/main/resources/application.yaml`):
- `ZEEBE_BROKER_CONTACTPOINT` - Zeebe gateway address
- `MASTERCARD_API_URL` / `MASTERCARD_AUTH_URL` - CBS API endpoints
- `MASTERCARD_CLIENT_ID` / `MASTERCARD_CLIENT_SECRET` - OAuth credentials
- `DATASOURCE_URL` / `DATASOURCE_USERNAME` / `DATASOURCE_PASSWORD` - MySQL connection

## Key Files

- `orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn` - BPMN workflow definition
- `src/utils/data-loading/mastercard-cbs-schema.sql` - Database schema with demo data
- `operator/` - Kubernetes operator configuration (CRD, RBAC)

## Testing a Workflow

```bash
# Deploy BPMN to Zeebe
zbctl deploy orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn

# Create workflow instance
zbctl create instance bulk_connector_mastercard_cbs-DFSPID \
  --variables '{"transactionId": "test-001", "payeeIdentity": "0495822412", "amount": 100.00, "currency": "USD"}'
```

## Monitoring

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```
## GIT
- do not put Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com> in the git commit log