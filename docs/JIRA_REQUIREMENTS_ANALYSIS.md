# JIRA Requirements Analysis - PHEE-351

## Ticket Overview

**JIRA**: [PHEE-351](https://mifosforge.jira.com/browse/PHEE-351) - Mastercard CBS Demo Connector

**Purpose**: Enable cross-border disbursements from GovStack through Mifos Payment Hub EE to Mastercard CBS

**Scope**: Demo/proof-of-concept with limited functionality (10 payees, South Africa focus)

---

## Key Requirements from JIRA Ticket

### 1. Payment Flow: South African Payer

**✅ CORRECTED UNDERSTANDING**: G2P bulk disbursement flow (many-to-one):

- **PAYER (Sender)**: South African government - **ONE** entity disbursing
- **PAYEES (Recipients)**: Multiple beneficiaries - **MANY** people receiving
- **Payment Origination Country**: ZAF (South Africa - where money comes FROM)
- **Destination Country**: Variable (where beneficiaries are located)
- **Beneficiary Currency**: ZAR (South African Rand) - recipients receive ZAR
- **Destination Service Tag**: ZAK-BK (South Africa Banking system)
- **Payment Type**: B2P (Business to Person - government disbursements)

**Integration with Mifos-Gazelle**:
- Use existing `identity_account_mapper` data (same MSISDNs)
- Leverage `generate-mifos-vnext-data.py` pattern
- Deploy on mastercard branch of mifos-gazelle
- Existing schema with 10 demo payees from different countries is **CORRECT**

### 2. Mastercard Sandbox Endpoint

**🔴 CRITICAL**: Use **real Mastercard Sandbox**, not mock API

- **Endpoint**: `https://sandbox.api.mastercard.com`
- **API**: POST `/send/v1/partners/{partner-id}/crossborder/payment`
- **Authentication**: OAuth 2.0 (partner ID to be supplied by Mastercard)
- **Format**: XML (not JSON!)

**Current Implementation Issue**:
- Code designed for mock API at `http://localhost:8080`
- Models built for JSON serialization
- Need XML serialization support

### 3. Supplementary Data Table Requirements

#### Static Fields (Same for All Payees)

| Field | Value | Notes |
|-------|-------|-------|
| sender.organization_name | TBD | Government organization name |
| sender.address.line1 | TBD | Government office address |
| sender.address.city | TBD | Government office city |
| sender.address.country | TBD | Originating country (likely ZAF) |
| payment_origination_country | TBD | ISO-3 code |
| Destination Country (ISO-3) | **ZAF** | South Africa (fixed) |
| Beneficiary Currency | **ZAR(2)** | South African Rand (fixed) |
| Destination Service Tag | **ZAK-BK** | SA Banking (fixed) |
| Payment Type | **B2P** | Business to Person (fixed) |
| Channel Type | **Bank Account** | Fixed value |
| fees_included | **true** | Fixed value |

#### Variable Fields (Per Payee)

| Field | Source | Description |
|-------|--------|-------------|
| recipient.first_name | Lookup | Beneficiary first name |
| recipient.last_name | Lookup | Beneficiary last name |
| recipient.address.line1 | Lookup | Beneficiary address |
| recipient.phone | Lookup | Beneficiary phone (MSISDN) |
| recipient.email | Lookup | Beneficiary email |
| recipient.address.country | Lookup | Should be ZAF for all |

#### Composite Fields

**recipient_account_uri**: Constructed from request data
- Format: `ban:{account_number};bic={SWIFT_CODE}`
- SWIFT code must be 8 or 11 characters
- Pattern: `[A-Z]{4}ZA.{2}` for South African banks (ZA in positions 5-6)
- Example: `ban:30056001140114000251817;bic=ABZAZAJJ` or `ban:30056001140114000251817;bic=ABZAZAJJXXX`

#### Request Fields (From Payment Instruction)

| Field | Source Field | Mapped To |
|-------|-------------|-----------|
| purpose_of_payment | Payments Ref | Purpose text |
| additional_data:208 | Recipient Alias Name | Optional functional ID |
| Special Note | notes | Additional notes |
| transaction_reference | transactionId | Unique transaction ID |
| amount | amount | Payment amount |
| currency | currency | Payment currency |

### 4. API Request Format (XML)

The Mastercard CBS API uses **XML**, not JSON. Example structure:

```xml
<PaymentRequestWrapper>
  <paymentrequest>
    <transaction_reference>0982156QWECRTBYH034810klsrtsdrsd15490_7</transaction_reference>
    <recipient_account_uri>ban:30056001140114000251817;bic=ABZAZAJJ</recipient_account_uri>
    <payment_amount>
      <amount>192.64</amount>
      <currency>ZAR</currency>
    </payment_amount>
    <payment_origination_country>ZAF</payment_origination_country>
    <payment_type>B2P</payment_type>
    <sender>
      <organization_name>ABC Company, Mastercard Inc</organization_name>
      <address>
        <city>ANYTOWN</city>
        <country>ZAF</country>
        <line1>42 WEST ELM AVENUE</line1>
      </address>
    </sender>
    <recipient>
      <first_name>JOHN</first_name>
      <last_name>SMITH</last_name>
      <address>
        <city>ANYTOWN</city>
        <country>ZAF</country>
        <line1>42 WEST ELM AVENUE</line1>
      </address>
      <email>customer@gmail.com</email>
    </recipient>
    <purpose_of_payment>Payment of goods and services</purpose_of_payment>
    <additional_data>
      <data_field>
        <name>701</name>
        <value>ZAF</value>
      </data_field>
    </additional_data>
  </paymentrequest>
</PaymentRequestWrapper>
```

### 5. Status Updates

**Required**:
- Update PaymentHub Operations DB based on Mastercard response
- Support existing PayBB status APIs:
  - Batch Summary
  - Batch Details
  - Batch Payment Details

**Optional (Nice to Have)**:
- Use Mastercard CBS Retrieve Payment API to poll for status updates
- Update PaymentHub records based on retrieved status

### 6. GovStack Compliance

- Receive disbursement instruction via GovStack PayBB compliant API
- Process through PaymentHub G2P flows
- Use Identity Account Mapper (pre-populated)
- Return status via standard PayBB APIs

---

## Gap Analysis: Current Implementation vs JIRA Requirements

### ✅ Matches JIRA Requirements

1. **Zeebe Workflow Architecture** - Correct approach
2. **Supplementary Data Lookup** - Concept correct
3. **OAuth Authentication** - Required and implemented
4. **Database Schema** - Concept correct (but fields need adjustment)
5. **PaymentHub Integration** - Correct architecture

### 🔴 Critical Gaps

#### 1. API Format: JSON vs XML

**Current**: Models use Jackson `@JsonProperty` for JSON serialization

```java
@JsonProperty("access_token")
private String accessToken;
```

**Required**: XML format with JAXB annotations

```java
@XmlElement(name = "access_token")
private String accessToken;
```

**Impact**: High - All model classes need JAXB annotations

#### 2. Geographic Scope: Multi-Country vs South Africa Only

**Current**: 10 demo payees from 10 countries
```sql
-- US, UK, ES, IT, FR, DE, JP, CN, SA, IN
```

**Required**: All 10 payees in South Africa (ZAF)
```sql
-- All with beneficiary_country_code = 'ZA'
-- All with bank_country_code = 'ZA'
-- All SWIFT codes starting with ????ZA??
```

**Impact**: Medium - SQL data needs regeneration

#### 3. API Endpoint: Mock vs Real Sandbox

**Current**: Configuration points to mock API
```yaml
mastercard.api.url: ${MASTERCARD_API_URL:http://localhost:8080}
```

**Required**: Real Mastercard Sandbox
```yaml
mastercard.api.url: ${MASTERCARD_API_URL:https://sandbox.api.mastercard.com}
```

**Impact**: Low - Configuration change, but mock still useful for testing

#### 4. Database Schema Fields

**Current Schema**: Generic international fields
- `beneficiary_country_code` (variable)
- `bank_country_code` (variable)
- Generic SWIFT codes

**Required Schema**: South Africa specific + static fields
- `sender_organization_name` (static - government)
- `sender_address_line1` (static)
- `sender_address_city` (static)
- `sender_address_country` (static)
- `payment_origination_country` (static)
- `destination_country` (always 'ZAF')
- `destination_service_tag` (always 'ZAK-BK')
- `beneficiary_currency` (always 'ZAR')
- `payment_type` (always 'B2P')
- `recipient_phone` (MSISDN - currently not in schema)
- `recipient_email` (currently not in schema)

**Impact**: High - Schema redesign needed

#### 5. Composite Field Generation

**Current**: Not implemented

**Required**: Generate `recipient_account_uri` from account + SWIFT code
- Format: `ban:{account};bic={SWIFT}`
- Validation: SWIFT must match `[A-Z]{4}ZA.{2}` pattern
- Length: SWIFT must be 8 or 11 characters

**Impact**: Medium - Add logic to payment service

#### 6. Additional Data Fields

**Current**: Not implemented

**Required**: Support `additional_data` XML element with field 701
```xml
<additional_data>
  <data_field>
    <name>701</name>
    <value>ZAF</value>
  </data_field>
</additional_data>
```

**Impact**: Medium - Add to request model

### ⚠️ Moderate Gaps

#### 7. Static vs Dynamic Fields

**Current**: All fields treated as variable

**Required**: Separate static configuration from per-payee data
- Static fields: Should come from config, not database
- Variable fields: Per-payee from database

**Impact**: Medium - Refactor data merging logic

#### 8. Field Mapping Complexity

**Current**: Simple 1-to-1 field mapping assumed

**Required**: Complex field merging from 3 sources:
1. Request (transactionId, amount, currency, notes)
2. Supplementary data lookup (recipient details)
3. Static configuration (sender org, destination country, etc.)

**Impact**: Medium - Update worker logic

### ✅ Minor Gaps

#### 9. SWIFT Code Validation

**Current**: No validation

**Required**: Validate SWIFT codes are South African
- Must match `[A-Z]{4}ZA.{2}` for 8-char codes
- Must match `[A-Z]{4}ZA.{2}XXX` for 11-char codes
- Positions 5-6 must be 'ZA'

**Impact**: Low - Add validation in worker

#### 10. Partner ID Configuration

**Current**: Hardcoded demo value
```yaml
mastercard.partner-id: MIFOS_GOVSTACK
```

**Required**: To be supplied by Mastercard

**Impact**: Low - Configuration update when received

---

## Recommended Schema Changes

### Proposed New Schema

```sql
CREATE TABLE mastercard_cbs_supplementary_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Lookup Keys
    payee_msisdn VARCHAR(20) UNIQUE NOT NULL,
    payee_account_number VARCHAR(50) NOT NULL,

    -- Static Sender Information (Same for All - Government Payer)
    sender_organization_name VARCHAR(255) DEFAULT 'Government of South Africa',
    sender_address_line1 VARCHAR(255) DEFAULT 'Government Building, Pretoria',
    sender_address_city VARCHAR(100) DEFAULT 'Pretoria',
    sender_address_country CHAR(3) DEFAULT 'ZAF',
    payment_origination_country CHAR(3) DEFAULT 'ZAF',

    -- Static Destination Information (Same for All - South Africa)
    destination_country CHAR(3) DEFAULT 'ZAF',
    destination_service_tag VARCHAR(20) DEFAULT 'ZAK-BK',
    beneficiary_currency CHAR(3) DEFAULT 'ZAR',
    beneficiary_currency_decimals TINYINT DEFAULT 2,
    payment_type VARCHAR(10) DEFAULT 'B2P',
    channel_type VARCHAR(50) DEFAULT 'Bank Account',
    fees_included BOOLEAN DEFAULT TRUE,

    -- Variable Recipient Details (Per Payee)
    recipient_first_name VARCHAR(100) NOT NULL,
    recipient_last_name VARCHAR(100) NOT NULL,
    recipient_address_line1 VARCHAR(255) NOT NULL,
    recipient_address_city VARCHAR(100) NOT NULL,
    recipient_address_country CHAR(3) DEFAULT 'ZA',
    recipient_phone VARCHAR(20),           -- MSISDN
    recipient_email VARCHAR(255),

    -- Bank Details (South African Banks Only)
    bank_name VARCHAR(255) NOT NULL,
    bank_swift_code VARCHAR(11) NOT NULL,  -- 8 or 11 chars, must have ZA in positions 5-6
    bank_branch_name VARCHAR(255),
    bank_code VARCHAR(50),

    -- Purpose and Notes
    purpose_of_payment VARCHAR(255) DEFAULT 'Government social grant payment',
    source_of_income VARCHAR(100) DEFAULT 'GOVERNMENT',

    -- Metadata
    is_active BOOLEAN DEFAULT TRUE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes
    INDEX idx_msisdn (payee_msisdn),
    INDEX idx_account (payee_account_number),
    INDEX idx_active (is_active),

    -- Constraints
    CONSTRAINT chk_swift_south_africa CHECK (
        bank_swift_code REGEXP '^[A-Z]{4}ZA[A-Z0-9]{2}([A-Z0-9]{3})?$'
    ),
    CONSTRAINT chk_destination_country CHECK (destination_country = 'ZAF'),
    CONSTRAINT chk_beneficiary_currency CHECK (beneficiary_currency = 'ZAR')

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Sample South African Demo Data

```sql
INSERT INTO mastercard_cbs_supplementary_data (
    payee_msisdn, payee_account_number,
    recipient_first_name, recipient_last_name,
    recipient_address_line1, recipient_address_city,
    recipient_phone, recipient_email,
    bank_name, bank_swift_code, bank_branch_name
) VALUES
-- Payee 1
('27821234567', '1234567890',
 'Thabo', 'Mbeki',
 '123 Main Road', 'Johannesburg',
 '27821234567', 'thabo.mbeki@example.co.za',
 'Standard Bank', 'SBZAZAJJ', 'Sandton Branch'),

-- Payee 2
('27821234568', '2345678901',
 'Lindiwe', 'Khumalo',
 '45 Church Street', 'Cape Town',
 '27821234568', 'lindiwe.khumalo@example.co.za',
 'First National Bank', 'FIRNZAJJ', 'Cape Town CBD'),

-- Payee 3
('27821234569', '3456789012',
 'Sipho', 'Nkosi',
 '78 Market Street', 'Durban',
 '27821234569', 'sipho.nkosi@example.co.za',
 'Nedbank', 'NEDSZAJJ', 'Durban Central'),

-- Continue for 10 payees...
-- All with ZA country codes and ZA SWIFT codes
;
```

---

## Code Changes Required

### 1. Add JAXB Dependencies

**build.gradle**:
```gradle
dependencies {
    // Existing dependencies...

    // Add JAXB for XML support
    implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.0'
    implementation 'org.glassfish.jaxb:jaxb-runtime:4.0.0'
}
```

### 2. Update Model Classes for XML

**Before** (JSON):
```java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MastercardPaymentRequest {
    @JsonProperty("transaction_reference")
    private String transactionReference;
}
```

**After** (XML):
```java
@Data
@Builder
@XmlRootElement(name = "PaymentRequestWrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class MastercardPaymentRequest {
    @XmlElement(name = "paymentrequest")
    private PaymentRequestDetail paymentRequest;
}

@Data
@Builder
@XmlAccessorType(XmlAccessType.FIELD)
class PaymentRequestDetail {
    @XmlElement(name = "transaction_reference")
    private String transactionReference;

    @XmlElement(name = "recipient_account_uri")
    private String recipientAccountUri;

    @XmlElement(name = "payment_amount")
    private PaymentAmount paymentAmount;

    // ... more fields
}
```

### 3. Update RestTemplate for XML

**RestTemplateConfig.java**:
```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Add XML message converters
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();

        // JAXB for XML
        Jaxb2RootElementHttpMessageConverter jaxbConverter =
            new Jaxb2RootElementHttpMessageConverter();
        messageConverters.add(jaxbConverter);

        // Keep JSON support for OAuth
        MappingJackson2HttpMessageConverter jsonConverter =
            new MappingJackson2HttpMessageConverter();
        messageConverters.add(jsonConverter);

        restTemplate.setMessageConverters(messageConverters);

        return restTemplate;
    }
}
```

### 4. Add Composite Field Logic

**MastercardPaymentService.java**:
```java
public String buildRecipientAccountUri(String accountNumber, String swiftCode) {
    // Validate SWIFT code for South Africa
    if (!swiftCode.matches("^[A-Z]{4}ZA[A-Z0-9]{2}([A-Z0-9]{3})?$")) {
        throw new IllegalArgumentException(
            "SWIFT code must be South African (positions 5-6 must be 'ZA'): " + swiftCode
        );
    }

    // Format: ban:{account};bic={SWIFT}
    return String.format("ban:%s;bic=%s", accountNumber, swiftCode);
}
```

### 5. Update Worker for Static Fields

**MastercardCbsWorkers.java** - matchRegulatoryData worker:
```java
@JobWorker(type = "mastercard-cbs-match-regulatory-data", autoComplete = true)
public Map<String, Object> matchRegulatoryData(
        @Variable(name = "payeeIdentity") String payeeIdentity,
        @Variable(name = "payeeAccountNumber") String accountNumber) {

    // ... existing lookup logic ...

    // Add static fields from supplementary data
    variables.put("senderOrganizationName", suppData.getSenderOrganizationName());
    variables.put("senderAddressLine1", suppData.getSenderAddressLine1());
    variables.put("senderAddressCity", suppData.getSenderAddressCity());
    variables.put("senderAddressCountry", suppData.getSenderAddressCountry());
    variables.put("paymentOriginationCountry", suppData.getPaymentOriginationCountry());
    variables.put("destinationCountry", suppData.getDestinationCountry());
    variables.put("destinationServiceTag", suppData.getDestinationServiceTag());
    variables.put("beneficiaryCurrency", suppData.getBeneficiaryCurrency());
    variables.put("paymentType", suppData.getPaymentType());
    variables.put("channelType", suppData.getChannelType());
    variables.put("feesIncluded", suppData.isFeesIncluded());

    // Build composite field
    String recipientAccountUri = buildRecipientAccountUri(
        accountNumber,
        suppData.getBankSwiftCode()
    );
    variables.put("recipientAccountUri", recipientAccountUri);

    return variables;
}
```

---

## Updated Implementation Plan

### Phase 1: Schema and Data (Priority 1)

**Tasks**:
1. Update SQL schema with new fields
2. Add South African constraints
3. Generate 10 South African demo payees
4. Add sender organization static fields
5. Validate SWIFT codes are SA-compliant

**Deliverables**:
- Updated `mastercard-cbs-schema.sql`
- Script to validate existing data
- Migration script if needed

**Estimated Effort**: 4-6 hours

### Phase 2: XML Support (Priority 1)

**Tasks**:
1. Add JAXB dependencies to build.gradle
2. Update all model classes with JAXB annotations
3. Create XML-specific request/response models
4. Update RestTemplate configuration
5. Test XML serialization/deserialization

**Deliverables**:
- Updated model classes
- XML message converter configuration
- Unit tests for XML marshalling

**Estimated Effort**: 8-10 hours

### Phase 3: Composite Fields and Static Data (Priority 2)

**Tasks**:
1. Implement recipient_account_uri builder
2. Add SWIFT code validation
3. Update workers to use static fields from DB
4. Add additional_data field support
5. Implement field merging logic

**Deliverables**:
- Updated MastercardPaymentService
- Updated MastercardCbsWorkers
- SWIFT validation utility

**Estimated Effort**: 6-8 hours

### Phase 4: Mastercard Sandbox Integration (Priority 2)

**Tasks**:
1. Obtain partner ID from Mastercard
2. Configure sandbox endpoint
3. Test OAuth flow with sandbox
4. Test payment submission with sandbox
5. Handle sandbox-specific errors

**Deliverables**:
- Updated configuration
- Sandbox credentials (secure)
- Integration test results

**Estimated Effort**: 8-12 hours (includes waiting for credentials)

### Phase 5: Mock API Update (Priority 3)

**Tasks**:
1. Implement XML endpoints in mock simulator
2. Add OAuth token endpoint
3. Add payment submission endpoint
4. Add payment status retrieval endpoint
5. Simulate Mastercard responses

**Deliverables**:
- Complete mock simulator
- XML response templates
- Test data

**Estimated Effort**: 10-12 hours

### Phase 6: Testing and Documentation (Priority 3)

**Tasks**:
1. End-to-end tests with mock API
2. Integration tests with sandbox (if available)
3. Update all documentation
4. Create troubleshooting guide
5. UAT with GovStack sandbox

**Deliverables**:
- Test suite
- Updated documentation
- UAT test results
- Demo video/recording

**Estimated Effort**: 12-16 hours

---

## Total Estimated Effort

| Phase | Effort | Priority |
|-------|--------|----------|
| Schema and Data | 4-6 hours | P1 |
| XML Support | 8-10 hours | P1 |
| Composite Fields | 6-8 hours | P2 |
| Sandbox Integration | 8-12 hours | P2 |
| Mock API Update | 10-12 hours | P3 |
| Testing & Docs | 12-16 hours | P3 |
| **TOTAL** | **48-64 hours** | **6-8 days** |

---

## Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Mastercard partner ID delay | High | Medium | Use mock API for development |
| XML format issues | High | Low | Thorough testing with samples |
| South African bank codes unknown | Medium | Medium | Research SA banking system |
| Schema migration breaks existing | Medium | Low | Test migration thoroughly |
| Sandbox access issues | High | Medium | Maintain mock API fallback |

---

## Next Steps (Immediate Actions)

1. **Update Database Schema** ✅ Priority 1
   - Create new schema file with SA-specific fields
   - Generate 10 SA demo payees
   - Test constraints

2. **Add JAXB Support** ✅ Priority 1
   - Update build.gradle
   - Start converting models to XML

3. **Request Mastercard Credentials** ✅ Priority 1
   - Contact Mastercard for partner ID
   - Request sandbox access
   - Get API documentation

4. **Research South African Banking** ✅ Priority 2
   - Get list of SA bank SWIFT codes
   - Understand account number formats
   - Verify service tags

5. **Update Documentation** ✅ Priority 2
   - Update all docs with SA focus
   - Remove multi-country references
   - Add XML examples

---

**Document Created**: January 24, 2026
**Based On**: JIRA PHEE-351 and sub-tasks
**Status**: Analysis Complete - Ready for Implementation
**Next Review**: After Phase 1 completion
