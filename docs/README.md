# PaymentHub EE - Mastercard CBS Connector

Mastercard Cross-Border Services connector for Mifos Payment Hub EE.

## Overview

This connector integrates Mifos Payment Hub EE with Mastercard Cross-Border Services (CBS) to enable cross-border disbursements from GovStack solutions.

**Key Features**:
- OAuth 2.0 authentication with Mastercard CBS API
- Supplementary regulatory data lookup from MySQL
- Payment submission to Mastercard CBS
- Payment status retrieval
- Zeebe-based workflow orchestration
- Comprehensive error handling and retry logic

## Architecture

### Zeebe Workers

This connector provides 8 Zeebe workers:

| Worker Type | Purpose |
|-------------|---------|
| `mastercard-cbs-validate-input` | Validate transaction data |
| `mastercard-cbs-authenticate` | Get OAuth access token |
| `mastercard-cbs-match-regulatory-data` | Lookup supplementary data from DB |
| `mastercard-cbs-initiate-payment` | Submit payment to CBS API |
| `mastercard-cbs-check-status` | Retrieve payment status |
| `mastercard-cbs-update-operations` | Update Operations DB |
| `mastercard-cbs-retry-handler` | Manage retry logic |
| `mastercard-cbs-log-error` | Log errors |

### BPMN Workflow

Works with: `bulk_connector_mastercard_cbs-DFSPID.bpmn`

## Configuration

### Environment Variables

```bash
# Zeebe
ZEEBE_BROKER_CONTACTPOINT=zeebe-gateway:26500
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
DATASOURCE_PASSWORD=<password>

# Logging
LOGGING_LEVEL_ORG_MIFOS=DEBUG
```

## Building

### Prerequisites
- Java 17+
- Gradle 8.x or use Docker build

### Local Build

```bash
# If you don't have Gradle wrapper, use system Gradle
gradle clean build

# Or use Docker to build
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .
```

### Docker Build

```bash
docker build -t ph-ee-connector-mastercard-cbs:1.0.0 .
```

## Running

### Local Development

```bash
# If you don't have Gradle wrapper, use system Gradle
gradle bootRun

# Or run the built JAR
java -jar build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar
```

### Docker

```bash
docker run -p 8080:8080 \
  -e ZEEBE_BROKER_CONTACTPOINT=localhost:26500 \
  -e MASTERCARD_API_URL=http://localhost:8080 \
  -e DATASOURCE_URL=jdbc:mysql://localhost:3306/operations \
  ph-ee-connector-mastercard-cbs:1.0.0
```

### Kubernetes/Helm

_Note: Helm charts not yet implemented. Manual Kubernetes deployment required._

```bash
# Example manual deployment (customize as needed)
kubectl create deployment ph-ee-connector-mastercard-cbs \
  --image=ph-ee-connector-mastercard-cbs:1.0.0 \
  -n paymenthub
```

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Testing

1. Start Zeebe
2. Deploy BPMN workflow
3. Start mock Mastercard API
4. Start connector
5. Create workflow instance:

```bash
zbctl create instance bulk_connector_mastercard_cbs-DFSPID \
  --variables '{
    "transactionId": "test-001",
    "payeeIdentity": "0495822412",
    "payeeAccountNumber": "1001234567",
    "amount": 100.00,
    "currency": "USD"
  }'
```

## Database Schema

Requires table: `mastercard_cbs_supplementary_data`

See: [schema](src/utils/data-loading/mastercard-cbs-schema.sql)

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

### Logs

```bash
# Kubernetes
kubectl logs -f deployment/ph-ee-connector-mastercard-cbs -n paymenthub

# Docker
docker logs -f <container-id>
```

## Troubleshooting

### Workers Not Registered

Check Zeebe connection:
```bash
kubectl logs deployment/ph-ee-connector-mastercard-cbs | grep "Registered worker"
```

### Authentication Failures

Verify credentials:
```bash
kubectl get secret mastercard-cbs-credentials -o yaml
```

### Supplementary Data Not Found

Check database connection and data:
```sql
SELECT COUNT(*) FROM mastercard_cbs_supplementary_data WHERE is_active = true;
```

## Documentation

For detailed guides and information, see:

- **[docs/INDEX.md](INDEX.md)** - Complete documentation index
- **[INTEGRATION_QUICKSTART.md](INTEGRATION_QUICKSTART.md)** - Step-by-step setup with mifos-gazelle
- **[BUILD_AND_TEST.md](BUILD_AND_TEST.md)** - Detailed build and test instructions
- **[OPERATOR_DEPLOYMENT_GUIDE.md](OPERATOR_DEPLOYMENT_GUIDE.md)** - Kubernetes operator deployment
- **[LOCALDEV.md](LOCALDEV.md)** - Local development tips and tricks
- **[MIFOS_GAZELLE_INTEGRATION.md](MIFOS_GAZELLE_INTEGRATION.md)** - Integration architecture
- **[JIRA_REQUIREMENTS_ANALYSIS.md](JIRA_REQUIREMENTS_ANALYSIS.md)** - Requirements reference (PHEE-351)
- **[GENERATED_SOLUTION_SUMMARY.md](GENERATED_SOLUTION_SUMMARY.md)** - Solution overview and architecture
- **[GOVSTACK.md](GOVSTACK.md)** - GovStack G2P architecture reference

## Quick Start

1. Review the [Integration Quickstart](INTEGRATION_QUICKSTART.md)
2. Set up the [database schema](../src/utils/data-loading/mastercard-cbs-schema-v2.sql)
3. Load supplementary data with [load-mastercard-supplementary-data.py](../src/utils/data-loading/load-mastercard-supplementary-data.py)
4. Build and deploy the connector (see [BUILD_AND_TEST.md](BUILD_AND_TEST.md))
5. Deploy the BPMN workflow from [orchestration/](../orchestration/)
6. Submit test batch using mifos-gazelle tools

## Project Structure

```
ph-ee-connector-mccbs/
├── docs/                      # Documentation (this directory)
├── src/                       # Java source code
│   ├── main/java/             # Application code
│   └── utils/data-loading/    # Database scripts and data loaders
├── orchestration/             # BPMN workflows
├── operator/                  # Kubernetes operator
├── build.gradle               # Gradle build configuration
└── Dockerfile                 # Container image build
```

## License

Apache License 2.0
