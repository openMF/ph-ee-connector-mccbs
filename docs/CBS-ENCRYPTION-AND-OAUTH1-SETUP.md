# Mastercard CBS Encryption and OAuth1 Setup Guide

This guide explains how to configure the ph-ee-connector-mccbs to work with the real Mastercard CBS sandbox using OAuth 1.0a signing and JWE encryption.

## Overview

The connector has been updated to support Mastercard CBS requirements:
- **OAuth 1.0a** request signing (replaces OAuth2 for sandbox)
- **JWE (JSON Web Encryption)** for request/response payloads
- **x-encrypted: true** header for encrypted API calls

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ Payment Hub EE Connector                                 │
│ ┌─────────────────┐                                     │
│ │ Request Payload │                                     │
│ └────────┬────────┘                                     │
│          │                                               │
│          ▼                                               │
│ ┌─────────────────┐   1. Encrypt with Mastercard's     │
│ │ JWE Encryption  │      public key (RSA-OAEP-256)     │
│ │ EncryptionUtils │                                     │
│ └────────┬────────┘                                     │
│          │                                               │
│          ▼                                               │
│ ┌─────────────────┐   2. Wrap in encrypted_payload     │
│ │ Encrypted JSON  │      format                         │
│ └────────┬────────┘                                     │
│          │                                               │
│          ▼                                               │
│ ┌─────────────────┐   3. Sign with OAuth1 private key  │
│ │ OAuth1 Signer   │      Add Authorization header      │
│ │ (Interceptor)   │      Add x-mc-routing header       │
│ └────────┬────────┘                                     │
│          │                                               │
│          ▼                                               │
│ ┌─────────────────┐   4. Add x-encrypted: true header  │
│ │ RestTemplate    │                                     │
│ └────────┬────────┘                                     │
└──────────┼──────────────────────────────────────────────┘
           │
           │ HTTPS POST
           ▼
┌─────────────────────────────────────────────────────────┐
│ Mastercard CBS Sandbox API                               │
│ https://sandbox.api.mastercard.com                       │
└─────────────────────────────────────────────────────────┘
```

## Required Certificates and Keys

### 1. OAuth1 Signing Key (.p12)

**File**: `src/main/resources/certs/signing-key.p12`
**From**: Mastercard Developers portal → Your Project → Keys section
**Original name**: `mifos paymenthub cbs connector -sandbox-signing.p12`
**Used for**: Signing API requests for authentication
**Algorithm**: RSA with SHA-256

### 2. Mastercard Encryption Certificate (.p12)

**File**: `src/main/resources/certs/mastercard-encryption-key.p12`
**From**: Mastercard Developers portal → Your Project → Keys section
**Original name**: `mastercard-cross-border-services-mccbssandbox-mastercard-encryption-key.p12`
**Used for**: Encrypting request payloads TO Mastercard
**Contains**: Mastercard's public key
**Algorithm**: RSA-OAEP-256 with A256GCM

### 3. Client Decryption Key (.pem)

**File**: `src/main/resources/certs/client-encryption-key.pem`
**From**: Mastercard Developers portal → Your Project → Keys section
**Original name**: `mastercard-cross-border-services-clientenc1770810236013-client-encryption-key.pem`
**Used for**: Decrypting response payloads FROM Mastercard
**Contains**: Your private key

## Setup Instructions

### Step 1: Download Keys from Mastercard Portal

1. Log in to https://developer.mastercard.com
2. Navigate to your project: "mifos-paymenthub-cbs-connector"
3. Go to **Keys** section
4. Download the key package (ZIP file: `MCD_Sandbox_mifos-paymenthub-cbs-connector_API_Keys.zip`)
5. Extract the ZIP file

### Step 2: Copy Keys to Connector

```bash
# From your extracted Mastercard keys directory
cd ~/my-mac-dir/mastercard

# Copy and rename signing key
cp "mifos paymenthub cbs connector -sandbox-signing.p12" \
   ~/ph-ee-connector-mccbs/src/main/resources/certs/signing-key.p12

# Copy encryption certificate
cp mastercard-cross-border-services-mccbssandbox-mastercard-encryption-key.p12 \
   ~/ph-ee-connector-mccbs/src/main/resources/certs/mastercard-encryption-key.p12

# Copy decryption key
cp mastercard-cross-border-services-clientenc1770810236013-client-encryption-key.pem \
   ~/ph-ee-connector-mccbs/src/main/resources/certs/client-encryption-key.pem

# Verify
ls -la ~/ph-ee-connector-mccbs/src/main/resources/certs/
```

**⚠️ IMPORTANT**: These files are in `.gitignore` and will NEVER be committed to git.

### Step 3: Update Configuration

#### For Mifos-Gazelle Deployment

Edit `~/mifos-gazelle/config/config.ini` (or your custom config like `~/tomconfig.ini`):

```ini
[mastercard-demo]
enabled = true

# API Configuration
MASTERCARD_API_URL = https://sandbox.api.mastercard.com
MASTERCARD_PARTNER_ID = mifos-paymenthub-cbs-connector  # Your actual partner ID

# OAuth1 Configuration (from Mastercard portal)
MASTERCARD_CONSUMER_KEY = your-consumer-key-from-portal
MASTERCARD_SIGNING_KEY_ALIAS = keyalias
MASTERCARD_SIGNING_KEY_PASSWORD = keystorepassword

# Encryption Configuration
MASTERCARD_ENCRYPTION_ENABLED = true  # Enable encryption for sandbox
MASTERCARD_ENCRYPTION_FINGERPRINT = your-encryption-fingerprint-from-portal
MASTERCARD_DECRYPTION_KEY_ALIAS = keyalias
MASTERCARD_DECRYPTION_KEY_PASSWORD = keystorepassword

# LocalDev mode (optional - for development)
MASTERCARD_LOCALDEV_ENABLED = true
MASTERCARD_CBS_HOME = $HOME/ph-ee-connector-mccbs
```

#### For Standalone Deployment

Set environment variables:

```bash
export MASTERCARD_API_URL=https://sandbox.api.mastercard.com
export MASTERCARD_PARTNER_ID=mifos-paymenthub-cbs-connector
export MASTERCARD_CONSUMER_KEY=your-consumer-key
export MASTERCARD_SIGNING_KEY_PASSWORD=keystorepassword
export MASTERCARD_ENCRYPTION_ENABLED=true
export MASTERCARD_ENCRYPTION_FINGERPRINT=your-fingerprint
export MASTERCARD_DECRYPTION_KEY_PASSWORD=keystorepassword
```

## Configuration Reference

| Config Key | Description | Example |
|------------|-------------|---------|
| `MASTERCARD_API_URL` | CBS API base URL | `https://sandbox.api.mastercard.com` |
| `MASTERCARD_PARTNER_ID` | Your partner identifier | `mifos-paymenthub-cbs-connector` |
| `MASTERCARD_CONSUMER_KEY` | OAuth1 consumer key | From portal "Keys" section |
| `MASTERCARD_SIGNING_KEY_FILE` | Path to signing .p12 | `classpath:certs/signing-key.p12` |
| `MASTERCARD_SIGNING_KEY_ALIAS` | Keystore alias | `keyalias` |
| `MASTERCARD_SIGNING_KEY_PASSWORD` | Keystore password | From portal |
| `MASTERCARD_ENCRYPTION_ENABLED` | Enable JWE encryption | `true` for sandbox, `false` for mock |
| `MASTERCARD_ENCRYPTION_CERT` | Encryption certificate | `classpath:certs/mastercard-encryption-key.p12` |
| `MASTERCARD_ENCRYPTION_FINGERPRINT` | Encryption key ID | From portal |
| `MASTERCARD_DECRYPTION_KEY` | Decryption key path | `classpath:certs/client-encryption-key.pem` |
| `MASTERCARD_DECRYPTION_KEY_ALIAS` | Decryption key alias | `keyalias` |
| `MASTERCARD_DECRYPTION_KEY_PASSWORD` | Decryption password | From portal |

## Testing Configuration

### Test with Mock Simulator (No Encryption)

```ini
MASTERCARD_API_URL = http://mastercard-simulator.mastercard-demo.svc.cluster.local:8080
MASTERCARD_ENCRYPTION_ENABLED = false
```

### Test with Sandbox (With Encryption)

```ini
MASTERCARD_API_URL = https://sandbox.api.mastercard.com
MASTERCARD_ENCRYPTION_ENABLED = true
```

## Deployment

### With Mifos-Gazelle

```bash
cd ~/mifos-gazelle
sudo ./run.sh -a mastercard-demo -f ~/tomconfig.ini
```

### Verify Deployment

```bash
# Check pod is running
kubectl get pods -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs

# Check logs for OAuth1 and encryption initialization
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs --tail=50

# Look for these log messages:
# - "OAuth1 signing key loaded successfully"
# - "Initiating CBS payment... encryption: true"
# - "Payload encrypted successfully"
# - "Response decrypted successfully"
```

## Troubleshooting

### OAuth1 Signing Errors

**Error**: `Failed to load OAuth1 signing key`

**Solution**:
- Check signing-key.p12 exists in `src/main/resources/certs/`
- Verify `MASTERCARD_SIGNING_KEY_PASSWORD` is correct
- Verify `MASTERCARD_SIGNING_KEY_ALIAS` matches keystore

### Encryption Errors

**Error**: `Encryption failed`

**Solution**:
- Check mastercard-encryption-key.p12 exists
- Verify `MASTERCARD_ENCRYPTION_ENABLED=true`
- Check `MASTERCARD_ENCRYPTION_FINGERPRINT` is set

### Decryption Errors

**Error**: `Decryption failed`

**Solution**:
- Check client-encryption-key.pem exists
- Verify `MASTERCARD_DECRYPTION_KEY_PASSWORD` is correct

### 401 Unauthorized

**Causes**:
- Invalid consumer key
- Signature mismatch (wrong signing key)
- Expired keys

**Solution**:
- Verify `MASTERCARD_CONSUMER_KEY` from portal
- Check key expiry dates in Mastercard Developers portal
- Regenerate keys if needed

### Payload Format Errors

**Error**: `Invalid encrypted payload format`

**Solution**:
- Ensure `x-encrypted: true` header is set
- Verify payload is wrapped in `{"encrypted_payload":{"data":"..."}}`
- Check encryption fingerprint matches

## Security Best Practices

1. **Never commit keys**: Keys are in `.gitignore`
2. **Rotate keys regularly**: Check expiry in Mastercard portal
3. **Use secrets management**: For production, use Kubernetes secrets or vault
4. **Monitor key usage**: Check Mastercard portal for API usage
5. **Restrict access**: Limit who can access key files

## Implementation Details

### Dependencies Added

```gradle
// build.gradle
oauth1SignerVersion = '1.6.2'
nimbusJoseJwtVersion = '9.37.3'

dependencies {
    implementation "com.mastercard.developer:oauth1-signer:${oauth1SignerVersion}"
    implementation "com.nimbusds:nimbus-jose-jwt:${nimbusJoseJwtVersion}"
}
```

### Key Classes

- **EncryptionUtils**: JWE encryption/decryption helper
- **OAuth1SigningInterceptor**: RestTemplate interceptor for OAuth1 signing
- **MastercardPaymentService**: Updated to support encryption
- **EncryptedPayload**: Model for encrypted request/response wrapper

### Request Flow

1. Build MastercardPaymentRequest object
2. If encryption enabled:
   - Serialize to JSON string
   - Encrypt with JWE using Mastercard's public key
   - Wrap in `{"encrypted_payload":{"data":"..."}}`
   - Add `x-encrypted: true` header
3. OAuth1 interceptor signs request:
   - Generates OAuth signature using signing key
   - Adds `Authorization` header
   - Adds `x-mc-routing: nextgen-apigw` header
4. Send via RestTemplate

### Response Flow

1. Receive encrypted response
2. Extract `data` from `{"encrypted_payload":{"data":"..."}}`
3. Decrypt JWE string using client private key
4. Deserialize JSON to MastercardPaymentResponse

## References

- [Mastercard OAuth 1.0a Documentation](https://developer.mastercard.com/platform/documentation/authentication/using-oauth-1a-to-access-mastercard-apis/)
- [Mastercard Client Encryption Library](https://github.com/Mastercard/client-encryption-java)
- [Mastercard OAuth1 Signer](https://github.com/Mastercard/oauth1-signer-java)
- [Mastercard CBS Reference App](https://github.com/Mastercard/crossborder-services-reference-app)

## Support

For issues:
- Check logs: `kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs`
- Review this guide
- Contact Mastercard support: apisupport@mastercard.com
- Check Mastercard Developers portal for key status

---

**Document Created**: February 13, 2026
**For**: PHEE-351 - Mastercard CBS Demo Connector
**Related Docs**:
- [MASTERCARD-CBS-INTEGRATION.md](MASTERCARD-CBS-INTEGRATION.md)
- [MASTERCARD-DEPLOY-AND-TEST.md](MASTERCARD-DEPLOY-AND-TEST.md)
