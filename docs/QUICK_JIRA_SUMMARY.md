# Quick JIRA Summary - What Changed

## TL;DR

After analyzing the JIRA ticket (PHEE-351), we discovered **2 critical differences** and **1 integration opportunity**:

1. 🔴 **XML not JSON**: Mastercard CBS API uses XML format (need JAXB support)
2. 🔴 **Real Sandbox**: Target is Mastercard's actual sandbox at `https://sandbox.api.mastercard.com`
3. ✅ **Mifos-Gazelle Integration**: Deploy on mastercard branch, leverage existing data loading tools

**Corrected Understanding**: South Africa is the **PAYER** (government sender), not the payee destination. Multi-country recipients ✅ CORRECT.

**Estimated Work to Fix**: 36-48 hours (4-6 days) - reduced because multi-country payees are already correct

---

## What's Built vs What's Needed

### ✅ What's Already Built (Still Usable)

- Zeebe connector architecture ✅
- 8 workers for workflow steps ✅
- OAuth authentication logic ✅
- Database lookup concept ✅
- BPMN workflow structure ✅
- Dockerfile and build setup ✅

### 🔴 What Needs Major Changes

#### 1. API Format: JSON → XML

**Current**:
```java
@JsonProperty("access_token")
private String accessToken;
```

**Needed**:
```java
@XmlElement(name = "access_token")
private String accessToken;
```

**Why**: Mastercard CBS API accepts/returns XML, not JSON

**Effort**: 8-10 hours (update all model classes + RestTemplate)

#### 2. Payment Direction: South Africa as PAYER ✅ CORRECTED

**Understanding**: South African government (payer) → Multiple beneficiaries (payees)

**Current Implementation**: 10 payees from 10 countries ✅ **CORRECT**
```
US, UK, Spain, Italy, France, Germany, Japan, China, Saudi Arabia, India
```

**JIRA Requirements**:
- **Sender** (payer): South African government (static fields to add)
- **Recipients** (payees): Can be in multiple countries ✅
- **Currency**: ZAR (all recipients receive South African Rand)

**Effort**: 2-3 hours (add static sender fields to schema, keep existing payee data)

#### 3. Endpoint: Mock → Real Sandbox

**Current**:
```yaml
mastercard.api.url: http://localhost:8080
```

**Needed**:
```yaml
mastercard.api.url: https://sandbox.api.mastercard.com
```

**Effort**: 8-12 hours (sandbox integration + testing)

### ⚠️ What Needs Minor Updates

#### 4. Database Schema Fields

**Current**: Missing fields
- Sender organization name
- Sender address fields
- Recipient phone
- Recipient email
- Destination service tag
- Currency decimal precision

**Needed**: Add these fields to schema

**Effort**: Included in item #2 above

#### 5. Composite Field Logic

**Current**: Not implemented

**Needed**: Build `recipient_account_uri` from account + SWIFT
- Format: `ban:1234567890;bic=SBZAZAJJ`
- Validate SWIFT has "ZA" in positions 5-6

**Effort**: 6-8 hours

---

## Impact on Components

### CBS Connector (Java)

| Component | Status | Changes Needed |
|-----------|--------|----------------|
| Workers | ✅ Built | ⚠️ Update for XML models |
| Services | ✅ Built | ⚠️ Update for composite fields |
| Models | 🔴 JSON | 🔴 Convert to XML (JAXB) |
| Config | ✅ Built | ⚠️ Update sandbox URL |

**Effort**: 14-18 hours

### Database Schema

| Component | Status | Changes Needed |
|-----------|--------|----------------|
| Table structure | ✅ Built | ⚠️ Add missing fields |
| Demo data | 🔴 Multi-country | 🔴 Replace with SA data |
| Constraints | ⚠️ Minimal | ⚠️ Add SA constraints |

**Effort**: 4-6 hours

### Mock Simulator

| Component | Status | Changes Needed |
|-----------|--------|----------------|
| Project structure | ✅ Built | No changes |
| Endpoints | ⚠️ Skeleton | ⚠️ Implement XML endpoints |
| Storage | ❌ Not built | ⚠️ Implement in-memory store |

**Effort**: 10-12 hours

### BPMN Workflow

| Component | Status | Changes Needed |
|-----------|--------|----------------|
| Workflow structure | ✅ Built | ✅ No changes |
| Service tasks | ✅ Built | ✅ No changes |
| Error handling | ✅ Built | ✅ No changes |

**Effort**: 0 hours (no changes needed!)

---

## South African Banks (Research Needed)

We need to populate demo data with real SA banks. Here are the major ones:

| Bank Name | SWIFT Code | Notes |
|-----------|------------|-------|
| Standard Bank | SBZAZAJJ | Largest SA bank |
| First National Bank | FIRNZAJJ | FNB |
| Nedbank | NEDSZAJJ | One of big 4 |
| ABSA Bank | ABSAZAJJ | Part of Barclays group |
| Capitec Bank | CABLZAJJ | Retail focused |
| Investec | IVESZAJJ | Private bank |
| Discovery Bank | TBD | Digital bank |
| TymeBank | TBD | Digital bank |
| Bank Zero | TBD | Digital bank |

**Note**: Need to verify these SWIFT codes and get more details for demo data

---

## XML API Example

This is what the actual API format looks like (from JIRA):

```xml
<PaymentRequestWrapper>
  <paymentrequest>
    <transaction_reference>0982156QWECRTBYH034810</transaction_reference>
    <recipient_account_uri>ban:30056001140114000251817;bic=SBZAZAJJ</recipient_account_uri>
    <payment_amount>
      <amount>192.64</amount>
      <currency>ZAR</currency>
    </payment_amount>
    <payment_origination_country>ZAF</payment_origination_country>
    <payment_type>B2P</payment_type>
    <sender>
      <organization_name>Government of South Africa</organization_name>
      <address>
        <city>Pretoria</city>
        <country>ZAF</country>
        <line1>Government Building</line1>
      </address>
    </sender>
    <recipient>
      <first_name>Thabo</first_name>
      <last_name>Mbeki</last_name>
      <address>
        <city>Johannesburg</city>
        <country>ZA</country>
        <line1>123 Main Road</line1>
      </address>
      <email>thabo.mbeki@example.co.za</email>
    </recipient>
    <purpose_of_payment>Government social grant payment</purpose_of_payment>
    <additional_data>
      <data_field>
        <name>701</name>
        <value>ZAF</value>
      </data_field>
    </additional_data>
  </paymentrequest>
</PaymentRequestWrapper>
```

**Key Points**:
- Root element: `<PaymentRequestWrapper>`
- Nested `<paymentrequest>` element
- All fields lowercase with underscores
- Sender is organization (government), not person
- Recipient is person (beneficiary)
- Currency is always ZAR
- Country is always ZAF
- Payment type is always B2P (Business to Person)

---

## Priority Order for Implementation

### P0 - Blocking (Must Have)

1. **Add JAXB/XML Support** (8-10 hours)
   - Without this, can't talk to Mastercard API
   - Highest technical risk

2. **Update Schema for South Africa** (4-6 hours)
   - Required for correct demo
   - Blocks testing

### P1 - High (Should Have)

3. **Implement Composite Fields** (6-8 hours)
   - Required for correct API format
   - Relatively straightforward

4. **Update Workers for Static Fields** (included in #3)
   - Required for complete request
   - Depends on schema update

### P2 - Medium (Could Have)

5. **Mock API Implementation** (10-12 hours)
   - Useful for testing
   - Can develop without Mastercard credentials

6. **Mastercard Sandbox Integration** (8-12 hours)
   - Depends on getting credentials from Mastercard
   - Can mock until available

### P3 - Low (Nice to Have)

7. **Helm Charts** (not started)
   - Manual K8s deployment works for demo
   - Can defer

8. **Python Data Loading Scripts** (not started)
   - SQL INSERT statements work for demo
   - Can defer

---

## Immediate Next Steps (This Week)

### Today (Friday)

- [x] Review JIRA ticket analysis
- [x] Understand required changes
- [ ] Research South African banks
- [ ] Start new schema design

### Monday

- [ ] Create South African schema with 10 SA payees
- [ ] Test schema in database
- [ ] Add JAXB dependencies to build.gradle

### Tuesday

- [ ] Convert model classes to XML (JAXB)
- [ ] Update RestTemplate for XML
- [ ] Write XML marshalling tests

### Wednesday

- [ ] Implement composite field logic
- [ ] Add SWIFT validation
- [ ] Update workers

### Thursday

- [ ] Integration testing
- [ ] Bug fixes
- [ ] Documentation updates

### Friday (Next Week)

- [ ] Deploy to test environment
- [ ] Run UAT scenarios
- [ ] Demo to stakeholders

---

## Questions to Resolve

1. **Mastercard Credentials**: When can we get partner ID and sandbox access?
2. **South African Account Numbers**: What's the standard format?
3. **Static Sender Data**: What should the government organization details be?
4. **Service Tag**: Is "ZAK-BK" correct for all SA banks?
5. **Testing**: Do we test with mock API first or wait for sandbox access?

---

## Key Documents to Read

1. **[JIRA_REQUIREMENTS_ANALYSIS.md](JIRA_REQUIREMENTS_ANALYSIS.md)** - Detailed gap analysis
2. **[REVISED_IMPLEMENTATION_ROADMAP.md](REVISED_IMPLEMENTATION_ROADMAP.md)** - Week-by-week plan
3. **[jira-ticket.md](jira-ticket.md)** - Original JIRA requirements
4. **[BUILD_AND_TEST.md](BUILD_AND_TEST.md)** - How to build current code

---

## Bottom Line

**Good News**: Core architecture is solid ✅

**Challenge**: Need XML support and SA-specific data 🔴

**Estimate**: 6-8 days of focused work to align with JIRA specs

**Risk**: Mastercard sandbox credentials might delay final testing

**Mitigation**: Build mock API that uses XML format for testing

**Status**: Ready to proceed with Phase 1 (Schema) and Phase 2 (XML Support)

---

**Created**: January 24, 2026
**Based On**: PHEE-351 analysis
**For**: Quick executive overview
**Next**: Start implementation Monday morning
