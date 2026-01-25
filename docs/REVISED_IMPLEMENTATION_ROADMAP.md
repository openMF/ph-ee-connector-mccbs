# Revised Implementation Roadmap - Based on JIRA PHEE-351

## Executive Summary

Based on the detailed JIRA requirements, we need to make significant adjustments to the current implementation:

**Key Changes Required**:
1. ✅ **South Africa Focus**: All 10 demo payees must be in South Africa (ZAF), not multiple countries
2. 🔴 **XML API Format**: Mastercard CBS API uses XML, not JSON - requires JAXB support
3. 🔴 **Real Sandbox**: Target is Mastercard's real sandbox at `https://sandbox.api.mastercard.com`
4. ⚠️ **Schema Changes**: Add static sender fields, SA-specific constraints, additional fields
5. ⚠️ **Composite Fields**: Build `recipient_account_uri` from account + SWIFT code

**Current Status**: Core connector built with JSON models and generic international data

**Gap**: Approximately 48-64 hours of work to align with JIRA specifications

---

## Phased Implementation Approach

### Phase 1: Database Schema Redesign (IMMEDIATE)

**Duration**: 4-6 hours
**Priority**: P0 - Blocking

#### Tasks

1. **Create New South African Schema** (2 hours)
   ```bash
   # New file: mastercard-cbs-schema-south-africa.sql
   ```
   - Add static sender fields (government organization)
   - Add SA-specific constraints (destination_country = 'ZAF')
   - Add recipient phone and email fields
   - Add destination service tag field
   - Add currency decimal precision field
   - Add SWIFT code constraint (`^[A-Z]{4}ZA[A-Z0-9]{2}`)

2. **Generate South African Demo Data** (2 hours)
   - Research 10 major South African banks with SWIFT codes
   - Create 10 realistic SA payees:
     - South African names
     - SA addresses (Johannesburg, Cape Town, Durban, Pretoria, etc.)
     - SA phone numbers (format: +27...)
     - SA email addresses (.co.za domains)
     - SA bank accounts
     - SA SWIFT codes (e.g., SBZAZAJJ for Standard Bank)

3. **Add Database Constraints** (1 hour)
   - SWIFT code format validation
   - Destination country = 'ZAF' constraint
   - Currency = 'ZAR' constraint
   - Phone number format validation (+27...)

4. **Test Migration** (1 hour)
   - Load into test database
   - Validate all constraints
   - Query test data

#### Deliverables

- ✅ `mastercard-cbs-schema-south-africa.sql` with 10 SA payees
- ✅ Schema validation script
- ✅ Migration guide

#### Acceptance Criteria

- [ ] All 10 payees are in South Africa (country code ZA)
- [ ] All SWIFT codes have ZA in positions 5-6
- [ ] All phone numbers start with +27
- [ ] Static sender organization fields populated
- [ ] Schema constraints prevent non-SA data

---

### Phase 2: XML Support Implementation (IMMEDIATE)

**Duration**: 8-10 hours
**Priority**: P0 - Blocking

#### Tasks

1. **Add JAXB Dependencies** (1 hour)
   ```gradle
   // build.gradle
   implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.0'
   implementation 'org.glassfish.jaxb:jaxb-runtime:4.0.0'
   ```

2. **Create XML Request Models** (3 hours)
   - `PaymentRequestWrapper` (root element)
   - `PaymentRequestDetail` (paymentrequest element)
   - `PaymentAmount` (amount + currency)
   - `Address` (nested address structure)
   - `Sender` (organization sender)
   - `Recipient` (person recipient)
   - `AdditionalData` (data_field collection)

   Example structure:
   ```java
   @XmlRootElement(name = "PaymentRequestWrapper")
   @XmlAccessorType(XmlAccessType.FIELD)
   public class MastercardXmlPaymentRequest {
       @XmlElement(name = "paymentrequest")
       private PaymentRequest paymentRequest;
   }
   ```

3. **Create XML Response Models** (2 hours)
   - Parse Mastercard XML responses
   - Extract payment status
   - Extract reference numbers

4. **Update RestTemplate for XML** (1 hour)
   ```java
   // Add Jaxb2RootElementHttpMessageConverter
   // Keep JSON converter for OAuth
   ```

5. **Add XML Marshalling Tests** (2 hours)
   - Test request serialization
   - Test response deserialization
   - Validate against Mastercard XSD (if available)

#### Deliverables

- ✅ JAXB-annotated model classes
- ✅ XML RestTemplate configuration
- ✅ Unit tests for XML marshalling
- ✅ Sample XML request/response files

#### Acceptance Criteria

- [ ] Can serialize payment request to XML matching Mastercard format
- [ ] Can deserialize Mastercard XML responses
- [ ] OAuth still works with JSON (token endpoint)
- [ ] No compilation errors
- [ ] Tests pass

---

### Phase 3: Static Fields and Composite Logic (HIGH PRIORITY)

**Duration**: 6-8 hours
**Priority**: P1

#### Tasks

1. **Update SupplementaryData Model** (2 hours)
   ```java
   // Add static sender fields
   private String senderOrganizationName;
   private String senderAddressLine1;
   private String senderAddressCity;
   private String senderAddressCountry;
   private String paymentOriginationCountry;

   // Add SA-specific fields
   private String destinationServiceTag;
   private String beneficiaryCurrency;
   private Integer beneficiaryCurrencyDecimals;
   private String paymentType;
   private String channelType;
   private Boolean feesIncluded;

   // Add missing recipient fields
   private String recipientPhone;
   private String recipientEmail;
   ```

2. **Implement Composite Field Builder** (2 hours)
   ```java
   public String buildRecipientAccountUri(String account, String swift) {
       validateSwiftCode(swift);
       return String.format("ban:%s;bic=%s", account, swift);
   }

   private void validateSwiftCode(String swift) {
       if (!swift.matches("^[A-Z]{4}ZA[A-Z0-9]{2}([A-Z0-9]{3})?$")) {
           throw new IllegalArgumentException(
               "SWIFT code must be South African: " + swift
           );
       }
   }
   ```

3. **Update Match Regulatory Data Worker** (2 hours)
   - Retrieve supplementary data
   - Extract static sender fields
   - Extract recipient details
   - Build recipient_account_uri
   - Set all variables for next step

4. **Update Initiate Payment Worker** (2 hours)
   - Build XML request from merged data
   - Include additional_data field (701 = ZAF)
   - Set payment_type = B2P
   - Set fees_included = true
   - Call Mastercard API with XML

#### Deliverables

- ✅ Updated model classes
- ✅ Composite field builders
- ✅ SWIFT validation utility
- ✅ Updated workers

#### Acceptance Criteria

- [ ] Can build recipient_account_uri correctly
- [ ] SWIFT validation catches invalid codes
- [ ] Static fields merged correctly
- [ ] XML request includes all required fields
- [ ] Workers pass unit tests

---

### Phase 4: Mock API Implementation (MEDIUM PRIORITY)

**Duration**: 10-12 hours
**Priority**: P2

#### Tasks

1. **Implement OAuth Token Endpoint** (2 hours)
   - POST `/oauth/token`
   - Accept client_credentials grant
   - Return JSON token response
   - Validate client_id and client_secret

2. **Implement Payment Submission Endpoint** (4 hours)
   - POST `/send/v1/partners/{partner-id}/crossborder/payment`
   - Accept XML request body
   - Parse XML payment request
   - Validate required fields
   - Store in-memory payment
   - Return XML response with payment ID

3. **Implement Payment Status Endpoint** (2 hours)
   - GET `/send/v1/partners/{partner-id}/crossborder/payment/{payment-id}`
   - Retrieve payment from in-memory store
   - Return XML status response
   - Simulate status transitions (PENDING → PROCESSING → COMPLETED)

4. **Add In-Memory Storage Service** (2 hours)
   ```java
   @Service
   public class PaymentStorageService {
       private final Map<String, Payment> payments = new ConcurrentHashMap<>();

       public String storePayment(MastercardXmlPaymentRequest request) {
           String paymentId = generatePaymentId();
           payments.put(paymentId, new Payment(paymentId, request, "PENDING"));
           return paymentId;
       }
   }
   ```

5. **Add Dockerfile and Build** (1 hour)
   ```dockerfile
   FROM maven:3.9-eclipse-temurin-17 AS build
   WORKDIR /app
   COPY pom.xml .
   RUN mvn dependency:go-offline
   COPY src ./src
   RUN mvn package -DskipTests

   FROM eclipse-temurin:17-jre-alpine
   WORKDIR /app
   COPY --from=build /app/target/*.jar app.jar
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

#### Deliverables

- ✅ OAuth controller
- ✅ Payment controller
- ✅ Storage service
- ✅ Dockerfile
- ✅ application.properties

#### Acceptance Criteria

- [ ] OAuth endpoint returns valid tokens
- [ ] Payment endpoint accepts XML
- [ ] Status endpoint returns XML
- [ ] Can build and run with Docker
- [ ] Mock API passes integration tests

---

### Phase 5: Mastercard Sandbox Integration (CONTINGENT)

**Duration**: 8-12 hours
**Priority**: P2 (depends on credential availability)

#### Tasks

1. **Obtain Mastercard Credentials** (External - variable time)
   - Contact Mastercard
   - Request sandbox partner ID
   - Get OAuth credentials
   - Get API documentation

2. **Configure Sandbox Endpoint** (1 hour)
   ```yaml
   mastercard:
     api:
       url: https://sandbox.api.mastercard.com
       auth-url: https://sandbox.api.mastercard.com/oauth/token
       partner-id: ${MASTERCARD_PARTNER_ID}  # From Mastercard
     oauth:
       client-id: ${MASTERCARD_CLIENT_ID}    # From Mastercard
       client-secret: ${MASTERCARD_CLIENT_SECRET}  # From Mastercard
   ```

3. **Test OAuth Flow** (2 hours)
   - Get access token from sandbox
   - Validate token
   - Handle authentication errors
   - Implement token refresh

4. **Test Payment Submission** (3 hours)
   - Submit test payment
   - Validate XML format accepted
   - Handle API errors
   - Parse response

5. **Test Status Retrieval** (2 hours)
   - Query payment status
   - Parse XML status response
   - Map to internal status codes

6. **Handle Sandbox-Specific Issues** (2 hours)
   - Debug certificate issues
   - Handle rate limiting
   - Test error scenarios

#### Deliverables

- ✅ Sandbox configuration
- ✅ Integration test results
- ✅ Error handling for sandbox
- ✅ Troubleshooting guide

#### Acceptance Criteria

- [ ] Can authenticate with sandbox
- [ ] Can submit payment successfully
- [ ] Can retrieve payment status
- [ ] Error handling works correctly
- [ ] All API calls logged properly

---

### Phase 6: End-to-End Testing (HIGH PRIORITY)

**Duration**: 12-16 hours
**Priority**: P1

#### Tasks

1. **Deploy to Test Environment** (3 hours)
   - Build connector Docker image
   - Build simulator Docker image
   - Deploy to Kubernetes
   - Configure environment variables
   - Deploy BPMN workflow

2. **Load Test Data** (2 hours)
   - Create supplementary data table
   - Load 10 SA payees
   - Populate identity account mapper
   - Create test batches

3. **Execute Test Scenarios** (4 hours)

   **Scenario 1: Happy Path**
   - Submit batch with all 10 SA payees
   - Verify identity resolution
   - Verify supplementary data lookup
   - Verify XML payment submission
   - Verify status updates
   - Expected: 10/10 successful

   **Scenario 2: Partial Failure**
   - Submit batch with 12 payees (2 not in supplementary data)
   - Expected: 10/12 successful, 2 failed with "No supplementary data"

   **Scenario 3: Invalid Data**
   - Submit with invalid account number
   - Expected: Failure with clear error message

   **Scenario 4: API Error Handling**
   - Simulate Mastercard API errors
   - Verify retry logic
   - Verify error logging

   **Scenario 5: Status Polling**
   - Submit payment
   - Poll for status updates
   - Verify status progression

4. **Performance Testing** (2 hours)
   - Measure processing time per payment
   - Test with concurrent batches
   - Verify no memory leaks
   - Check resource usage

5. **Documentation Update** (3 hours)
   - Update all docs with SA focus
   - Add XML examples
   - Update troubleshooting guide
   - Create demo video

#### Deliverables

- ✅ Test environment deployed
- ✅ Test data loaded
- ✅ UAT test results
- ✅ Performance metrics
- ✅ Updated documentation

#### Acceptance Criteria

- [ ] All happy path tests pass
- [ ] Error scenarios handled correctly
- [ ] Processing time < 3 seconds per payment
- [ ] No errors in logs (except expected test errors)
- [ ] Documentation accurate

---

## Timeline and Milestones

### Week 1: Foundation (P0 Items)

**Days 1-2**: Schema and Data
- New SA schema
- 10 SA demo payees
- Schema validation

**Days 3-4**: XML Support
- JAXB dependencies
- XML models
- RestTemplate config
- XML tests

**Day 5**: Static Fields and Composites
- Composite field builders
- SWIFT validation
- Updated workers

**Milestone 1**: Core infrastructure supports South Africa and XML ✅

### Week 2: Implementation (P1-P2 Items)

**Days 6-7**: Mock API
- OAuth endpoint
- Payment endpoint
- Status endpoint
- Docker build

**Days 8-9**: Sandbox Integration (if credentials available)
- Configure sandbox
- Test OAuth
- Test payments
- Handle errors

**Day 10**: Testing
- Deploy to test env
- Load test data
- Execute scenarios

**Milestone 2**: End-to-end flow works with mock API ✅

### Week 3: Polish and Deploy (P2-P3 Items)

**Days 11-12**: Remaining Testing
- Performance tests
- UAT scenarios
- Bug fixes

**Days 13-14**: Documentation and Deployment
- Update all docs
- Create demo
- Deploy to GovStack sandbox

**Milestone 3**: Ready for production use ✅

---

## Resource Requirements

### Personnel

- **Java Developer** (Senior): 10-12 days
  - Schema design
  - XML implementation
  - Worker updates
  - Mock API

- **DevOps Engineer** (Junior): 3-4 days
  - Docker builds
  - Kubernetes deployment
  - Environment configuration

- **QA Engineer** (Mid-level): 4-5 days
  - Test planning
  - UAT execution
  - Bug verification

### Infrastructure

- **Development Environment**
  - Local Kubernetes (k3s)
  - MySQL database
  - Zeebe broker

- **Test Environment**
  - GovStack sandbox access
  - Kubernetes cluster
  - Monitoring tools

- **External Dependencies**
  - Mastercard sandbox access (TBD)
  - Partner ID from Mastercard (TBD)
  - API documentation from Mastercard

---

## Risk Management

### High Risks

| Risk | Mitigation | Status |
|------|-----------|--------|
| Mastercard credentials delay | Use mock API for development | ✅ Mitigated |
| XML format incompatibility | Test with real Mastercard samples | ⚠️ Monitor |
| South African bank data unavailable | Research SA banking system early | ⏳ In progress |
| Schema change breaks existing code | Thorough testing + migration script | ⏳ Planned |

### Medium Risks

| Risk | Mitigation | Status |
|------|-----------|--------|
| JAXB version conflicts | Use latest stable versions | ✅ Low risk |
| Performance issues with XML | Profile and optimize | ⏳ Test later |
| GovStack sandbox issues | Have local test environment | ✅ Mitigated |

---

## Success Criteria

### Technical Success

- [ ] All 10 SA payees can receive payments
- [ ] XML request format matches Mastercard specification
- [ ] Supplementary data lookup works correctly
- [ ] Composite fields (recipient_account_uri) built correctly
- [ ] Static fields populated from database
- [ ] Can connect to Mastercard sandbox (when credentials available)
- [ ] Mock API works for testing
- [ ] All tests pass
- [ ] No critical bugs

### Business Success

- [ ] Demo successfully shown to stakeholders
- [ ] GovStack PayBB compliance maintained
- [ ] Processing time acceptable (< 3 sec per payment)
- [ ] Clear error messages for failures
- [ ] Documentation complete and accurate
- [ ] Code ready for handover
- [ ] Foundation for production connector established

---

## Next Immediate Actions (This Week)

### Day 1 (Today)

1. ✅ **Create JIRA Requirements Analysis** - DONE
2. ✅ **Create Revised Implementation Roadmap** - DONE
3. ⏳ **Research South African Banks**
   - Get list of major SA banks
   - Get SWIFT codes for each
   - Understand account number formats
4. ⏳ **Draft New Schema**
   - Create mastercard-cbs-schema-south-africa.sql
   - Add all required fields
   - Add constraints

### Day 2

5. ⏳ **Generate SA Demo Data**
   - Create 10 realistic SA payees
   - Use real SA bank SWIFT codes
   - Validate all data

6. ⏳ **Test New Schema**
   - Load into database
   - Run validation queries
   - Test constraints

### Day 3-4

7. ⏳ **Add JAXB Support**
   - Update build.gradle
   - Create XML model classes
   - Configure RestTemplate

8. ⏳ **Test XML Marshalling**
   - Write unit tests
   - Validate XML format

### Day 5

9. ⏳ **Update Workers**
   - Implement composite fields
   - Add SWIFT validation
   - Test with new schema

10. ⏳ **Document Changes**
    - Update README
    - Update quickstart guide
    - Update implementation checklist

---

## Contact and Escalation

### For Questions

- **Technical Issues**: Review JIRA_REQUIREMENTS_ANALYSIS.md
- **Mastercard API**: Contact Mastercard support for sandbox access
- **GovStack**: Check GovStack PayBB specification

### For Escalation

- **Blocking Issues**: Document in project tracker
- **Credential Delays**: Escalate to Mastercard account manager
- **Scope Changes**: Review with product owner

---

**Document Created**: January 24, 2026
**Based On**: JIRA PHEE-351 detailed analysis
**Status**: Ready for implementation
**Next Review**: End of Week 1 (after schema and XML support complete)
