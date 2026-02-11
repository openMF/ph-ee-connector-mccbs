# Documentation Index

This directory contains all documentation for the Mastercard CBS Connector for PaymentHub EE.

## 📖 Start Here

New to this project? Start with these documents in order:

1. **[README.md](README.md)** - Project overview and quick reference
2. **[INTEGRATION_QUICKSTART.md](INTEGRATION_QUICKSTART.md)** - Step-by-step setup guide
3. **[BUILD_AND_TEST.md](BUILD_AND_TEST.md)** - How to build and test the connector

## 📚 Core Documentation

### Setup & Deployment

| Document | Purpose | When to Use |
|----------|---------|-------------|
| [INTEGRATION_QUICKSTART.md](INTEGRATION_QUICKSTART.md) | Complete setup guide for mifos-gazelle integration | First-time setup, integration with existing PaymentHub |
| [BUILD_AND_TEST.md](BUILD_AND_TEST.md) | Build instructions, testing procedures | Building from source, running tests |
| [OPERATOR_DEPLOYMENT_GUIDE.md](OPERATOR_DEPLOYMENT_GUIDE.md) | Kubernetes operator deployment | Production deployment, GitOps setup |
| [LOCALDEV.md](LOCALDEV.md) | Local development workflow | Development, debugging, rapid iteration |

### Architecture & Design

| Document | Purpose | When to Use |
|----------|---------|-------------|
| [MIFOS_GAZELLE_INTEGRATION.md](MIFOS_GAZELLE_INTEGRATION.md) | Integration architecture, data flow | Understanding system architecture |
| [GENERATED_SOLUTION_SUMMARY.md](GENERATED_SOLUTION_SUMMARY.md) | Solution overview, key components | High-level understanding, stakeholder briefing |
| [GOVSTACK.md](GOVSTACK.md) | GovStack G2P bulk disbursement architecture | Understanding G2P flows, compliance requirements |

### Requirements & Analysis

| Document | Purpose | When to Use |
|----------|---------|-------------|
| [JIRA_REQUIREMENTS_ANALYSIS.md](JIRA_REQUIREMENTS_ANALYSIS.md) | Detailed requirements (PHEE-351) | Understanding scope, requirements, API specs |

## 🎯 Common Tasks

### I want to...

#### Set up the connector for the first time
1. Read [INTEGRATION_QUICKSTART.md](INTEGRATION_QUICKSTART.md)
2. Follow Step 1-11 for complete setup
3. Refer to [BUILD_AND_TEST.md](BUILD_AND_TEST.md) for build details

#### Understand the architecture
1. Start with [GENERATED_SOLUTION_SUMMARY.md](GENERATED_SOLUTION_SUMMARY.md)
2. Deep dive into [MIFOS_GAZELLE_INTEGRATION.md](MIFOS_GAZELLE_INTEGRATION.md)
3. Review [GOVSTACK.md](GOVSTACK.md) for G2P context

#### Deploy to production
1. Review [OPERATOR_DEPLOYMENT_GUIDE.md](OPERATOR_DEPLOYMENT_GUIDE.md)
2. Check requirements in [JIRA_REQUIREMENTS_ANALYSIS.md](JIRA_REQUIREMENTS_ANALYSIS.md)
3. Use build instructions from [BUILD_AND_TEST.md](BUILD_AND_TEST.md)

#### Debug issues
1. Check troubleshooting section in [INTEGRATION_QUICKSTART.md](INTEGRATION_QUICKSTART.md)
2. Review [LOCALDEV.md](LOCALDEV.md) for development tips
3. See monitoring section in [README.md](README.md)

#### Understand data flow
1. Read data flow section in [MIFOS_GAZELLE_INTEGRATION.md](MIFOS_GAZELLE_INTEGRATION.md)
2. Review database schema in [GENERATED_SOLUTION_SUMMARY.md](GENERATED_SOLUTION_SUMMARY.md)
3. Check [GOVSTACK.md](GOVSTACK.md) for G2P batch processing

## 🗂️ Related Resources

### Source Code & Scripts
- **Database Schema**: `../src/utils/data-loading/mastercard-cbs-schema-v2.sql`
- **Data Loader**: `../src/utils/data-loading/load-mastercard-supplementary-data.py`
- **Batch Generator**: `../src/utils/data-loading/generate-mastercard-batch.py`
- **BPMN Workflow**: `../orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn`

### External Documentation
- [Mifos Payment Hub EE](https://mifos.gitbook.io/docs/payment-hub-ee)
- [GovStack Payments Building Block](https://www.govstack.global/building-blocks/payments/)
- [Mastercard CBS Documentation](https://developer.mastercard.com/cross-border-services/documentation/)
- [Zeebe Workflow Engine](https://docs.camunda.io/)

## 📊 Document Status

| Document | Status | Last Updated | Completeness |
|----------|--------|--------------|--------------|
| README.md | ✅ Current | 2026-01-27 | Complete |
| INTEGRATION_QUICKSTART.md | ✅ Current | 2026-01-24 | Complete |
| BUILD_AND_TEST.md | ✅ Current | 2026-01-24 | Complete |
| OPERATOR_DEPLOYMENT_GUIDE.md | ✅ Current | 2026-01-25 | Complete |
| LOCALDEV.md | ✅ Current | 2026-01-26 | Complete |
| MIFOS_GAZELLE_INTEGRATION.md | ✅ Current | 2026-01-24 | Complete |
| GENERATED_SOLUTION_SUMMARY.md | ✅ Current | 2026-01-24 | Complete |
| JIRA_REQUIREMENTS_ANALYSIS.md | ✅ Current | 2026-01-24 | Complete |
| GOVSTACK.md | ✅ Current | 2026-01-24 | Complete |

## 🔄 Documentation Maintenance

This documentation has been rationalized to remove duplicates and overlapping content.

### Recent Rationalization (2026-02-11)

**Files Removed:**
- `MASTERCARD-INTEGRATION-SUMMARY.md` → Redundant with MASTERCARD-CBS-INTEGRATION.md (less detailed)
- `MASTERCARD-LOCALDEV.md` → Merged into LOCALDEV.md (simulator + config.ini sections added)

**Files Enhanced:**
- `LOCALDEV.md` → Now includes simulator localdev, mifos-gazelle integration, and Helm vs Operator comparison
- `MIFOS_GAZELLE_INTEGRATION.md` → Added cross-references to GovStack docs to avoid duplication

**Files Kept (each serves distinct purpose):**
- `BUILD_AND_TEST.md` - Initial build and component verification
- `MASTERCARD-DEPLOY-AND-TEST.md` - GovStack-specific deployment and testing
- `GOVSTACK.md` - General GovStack architecture concepts
- `MASTERCARD-GOVSTACK-IMPLEMENTATION.md` - Mastercard CBS GovStack implementation
- `MASTERCARD-CBS-INTEGRATION.md` - Kubernetes operator technical deployment
- `MIFOS_GAZELLE_INTEGRATION.md` - Data loading scripts and integration patterns

**Documentation Philosophy**: Keep only current, non-duplicate documentation that serves a clear purpose. Use cross-references to avoid content duplication.

---

**Need help?** Start with [README.md](README.md) or [INTEGRATION_QUICKSTART.md](INTEGRATION_QUICKSTART.md)
