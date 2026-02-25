# Mastercard CBS Integration - Implementation Notes

## Overview

This document summarizes the critical differences between the JIRA requirements documentation and the actual working implementation, based on analysis of Mastercard's reference application (`crossborder-services-reference-app`).

**Key Finding:** The JIRA documentation contained significant inaccuracies. The Mastercard Java reference application is the authoritative source for API format requirements.

---

## XML Structure Differences

### ❌ JIRA Documentation (INCORRECT)

```xml
<PaymentRequestWrapper>
  <paymentrequest>
    <transaction_reference>abc-123</transaction_reference>
    <recipient_account_uri>ban:ES44210000002951164544;bic=BBVAESMM</recipient_account_uri>
    <payment_amount>
      <amount>10.0</amount>
      <currency>USD</currency>
    </payment_amount>
    <payment_origination_country>ZAF</payment_origination_country>
    <payment_type>B2P</payment_type>
    <sender>
      <organization_name>GovStack Ministry</organization_name>
      <address>
        <line1>123 Government Blvd</line1>
        <country>ZAF</country>
      </address>
    </sender>
    <recipient>
      <first_name>Carlos</first_name>
      <last_name>Rodriguez</last_name>
      <address>
        <line1>520 Calle Mayor</line1>
        <country>ES</country>
      </address>
      <email>carlos@example.es</email>
    </recipient>
    <purpose_of_payment>Government social grant payment to Carlos Rodriguez</purpose_of_payment>
  </paymentrequest>
</PaymentRequestWrapper>
```

**Problems:**
- ✗ Extra `<PaymentRequestWrapper>` wrapper element
- ✗ Missing `sender_account_uri` field
- ✗ Incorrect account URI format with `;bic=` suffix
- ✗ Payment type `B2P` instead of `G2P`
- ✗ Mixed case recipient names
- ✗ ISO2 country codes (2 letters)
- ✗ Missing city in recipient address
- ✗ Purpose text too long

### ✅ Working Implementation (FROM REFERENCE APP)

```xml
<paymentrequest>
  <transaction_reference>abc-123</transaction_reference>
  <sender_account_uri>tel:+0495822412</sender_account_uri>
  <recipient_account_uri>ban:ES44210000002951164544</recipient_account_uri>
  <payment_amount>
    <amount>10.0</amount>
    <currency>USD</currency>
  </payment_amount>
  <payment_origination_country>ZAF</payment_origination_country>
  <payment_type>G2P</payment_type>
  <sender>
    <organization_name>GovStack Ministry of Social Welfare</organization_name>
    <address>
      <city>Capital City</city>
      <country>ZAF</country>
      <line1>123 Government Boulevard</line1>
    </address>
  </sender>
  <recipient>
    <first_name>CARLOS</first_name>
    <last_name>RODRIGUEZ</last_name>
    <address>
      <city>Valencia</city>
      <country>ESP</country>
      <line1>520 Calle Mayor</line1>
    </address>
    <email>carlos.rodriguez@example.es</email>
  </recipient>
  <purpose_of_payment>Social welfare payment</purpose_of_payment>
</paymentrequest>
```

**Corrections:**
- ✓ Direct root element (no wrapper)
- ✓ Includes required `sender_account_uri`
- ✓ Simple `ban:` format (no `;bic=`)
- ✓ Payment type `G2P` (Government-to-Person)
- ✓ UPPERCASE recipient names
- ✓ ISO3 country codes (3 letters: ESP not ES)
- ✓ City field included
- ✓ Shortened purpose text

---

## Error Resolution Timeline

### Error 062000 - INVALID_INPUT_FORMAT
**Cause:** Extra `<PaymentRequestWrapper>` wrapper element
**Solution:** Changed `@XmlRootElement(name = "PaymentRequestWrapper")` to `@XmlRootElement(name = "paymentrequest")`
**File:** `PaymentRequestXml.java`

### Error 092000 - MISSING_REQUIRED_INPUT
**Cause:** Missing `sender_account_uri` field
**Solution:** Added `sender_account_uri` field with `tel:+{phone}` format
**Reference:** `RemittanceRequest.setSenderAccountUri("tel:+254108989")`

### Error 072000 - INVALID_INPUT_LENGTH (country)
**Cause:** Using ISO2 country codes (ES) instead of ISO3 (ESP)
**Solution:** Added `convertToISO3()` helper method
**File:** `MastercardPaymentService.java`

### Error 072000 - INVALID_INPUT_LENGTH (purpose)
**Cause:** Purpose text "Government social grant payment to {name}" too long
**Solution:** Shortened to "Social welfare payment"

---

## Database Schema Changes

### supplementary_data Table Updates

**New Column Added:**
```sql
recipient_address_city VARCHAR(100)
```

**Default Value Changed:**
```sql
payment_type VARCHAR(10) NOT NULL DEFAULT 'G2P'  -- Changed from 'B2P'
```

**Data Format Standards:**

| Field | Format | Example | Notes |
|-------|--------|---------|-------|
| `payee_account_number` | IBAN | `ES44210000002951164544` | Generated per country |
| `recipient_first_name` | UPPERCASE | `CARLOS` | Transformed in Java code |
| `recipient_last_name` | UPPERCASE | `RODRIGUEZ` | Transformed in Java code |
| `recipient_address_country` | ISO2 | `ES` | Auto-converted to ISO3 in code |
| `recipient_address_city` | Mixed case | `Valencia` | Required field |
| `payment_type` | Enum | `G2P` | Government-to-Person |

---

## Data Transformation Rules

### Country Code Conversion (ISO2 → ISO3)

Implementation in `MastercardPaymentService.convertToISO3()`:

```java
ES → ESP  (Spain)
GB → GBR  (United Kingdom)
US → USA  (United States)
IT → ITA  (Italy)
FR → FRA  (France)
DE → DEU  (Germany)
JP → JPN  (Japan)
CN → CHN  (China)
SA → SAU  (Saudi Arabia)
IN → IND  (India)
ZA → ZAF  (South Africa)
```

### Name Formatting

```java
// Database: "Carlos" / "Rodriguez"
// XML Output: "CARLOS" / "RODRIGUEZ"
String firstName = suppData.getRecipientFirstName().toUpperCase();
String lastName = suppData.getRecipientLastName().toUpperCase();
```

### Account URI Formatting

```java
// Sender (payer organization)
sender_account_uri: "tel:+{phone_number}"
// Example: "tel:+0495822412"

// Recipient (bank account)
recipient_account_uri: "ban:{account_number}"
// Example: "ban:ES44210000002951164544"
// NOT: "ban:ES44210000002951164544;bic=BBVAESMM"
```

---

## Reference Application Findings

### Source Files Analyzed

**Key Reference App File:**
```
/crossborder-services-reference-app/src/test/java/com/mastercard/crossborder/api/helper/
  CrossBorderAPITestHelper.java
```

**Method:** `setPaymentDataForGovernmentToPerson()`

**Critical Findings:**
1. Root element is `<paymentrequest>` (line 18: `@XmlRootElement(name = "paymentrequest")`)
2. Sender uses phone format: `"tel:+254108989"`
3. Recipient uses e-wallet format in example: `"ewallet:paypal_user011"`
4. Country codes are ISO3: `"IND"`, `"ARE"`, not ISO2
5. Names are UPPERCASE: `"JOHN"`, `"SMITH"`
6. Address includes: `line1`, `line2`, `city`, `countrySubdivision`, `postalCode`, `country`

---

## Testing Results

### First Successful Payment

**Date:** 2026-02-16 03:01:52 UTC

**Request:**
```xml
<paymentrequest>
  <transaction_reference>3a91f06d-4632-4b2d-a0f2-01c94d67b407</transaction_reference>
  <sender_account_uri>tel:+0495822412</sender_account_uri>
  <recipient_account_uri>ban:ES44210000002951164544</recipient_account_uri>
  <payment_amount>
    <amount>10.0</amount>
    <currency>USD</currency>
  </payment_amount>
  <payment_origination_country>ZAF</payment_origination_country>
  <payment_type>G2P</payment_type>
  ...
</paymentrequest>
```

**Response:**
```
Payment ID: rem_tGgYeeAM_qs03NoxvPhUbmcRlh0
Status: SUCCESS
```

### Environment
- Mastercard Sandbox: `https://sandbox.api.mastercard.com`
- Endpoint: `/send/v1/partners/mifos_ph_mccbs/crossborder/payment`
- Authentication: OAuth 1.0a with RSA signing
- Encryption: Disabled (unencrypted XML)

---

## Key Lessons Learned

### 1. Trust the Reference Application
The JIRA documentation was incomplete and contained errors. Always verify against Mastercard's official reference application code.

### 2. XML Structure Validation
The Mastercard API performs strict XML structure validation. Extra elements (like wrapper tags) cause immediate rejection with error code 062000.

### 3. Country Code Standards
Mastercard APIs require ISO 3166-1 alpha-3 (ISO3) country codes universally. ISO2 codes will fail validation.

### 4. Case Sensitivity
Recipient names must be UPPERCASE. This is enforced by the API and not documented in JIRA requirements.

### 5. Required vs Optional Fields
Several fields marked as "optional" in JIRA docs are actually required:
- `sender_account_uri` (mandatory)
- `recipient.address.city` (mandatory)

### 6. Field Length Validation
The API enforces strict length validation on text fields like `purpose_of_payment`. Keep values concise.

---

## Migration Notes

### For Existing Deployments

If upgrading from JIRA-based implementation:

1. **Drop and recreate supplementary data table:**
   ```bash
   cd /ph-ee-connector-mccbs/src/utils/data-loading
   ./load-mastercard-supplementary-data.sh -c ~/config.ini --drop-table
   ```

2. **Rebuild connector JAR:**
   ```bash
   cd /ph-ee-connector-mccbs
   ./gradlew clean build -x test
   ```

3. **Restart connector pod:**
   ```bash
   kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs
   ```

### Breaking Changes

- XML root element changed: `PaymentRequestWrapper` removed
- New required field: `sender_account_uri`
- Country codes now auto-converted to ISO3
- Names auto-uppercased
- City field now mandatory

---

## Contact

For questions about Mastercard CBS API format:
- **Email:** APISupport@mastercard.com
- **Reference App:** `crossborder-services-reference-app` (Java SDK)

**Document Version:** 1.0
**Last Updated:** 2026-02-16
**Status:** Verified against Mastercard Sandbox
