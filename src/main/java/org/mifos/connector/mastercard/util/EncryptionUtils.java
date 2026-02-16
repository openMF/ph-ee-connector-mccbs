package org.mifos.connector.mastercard.util;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Utility class for JWE encryption and decryption of Mastercard CBS API payloads.
 * Based on Mastercard's reference implementation for Cross-Border Services.
 */
@Slf4j
public class EncryptionUtils {

    private EncryptionUtils() {
        // Private constructor to hide public constructor
    }

    // JWE encryption algorithm and method as per Mastercard CBS requirements
    private static final JWEAlgorithm ALG = JWEAlgorithm.RSA_OAEP_256;
    private static final EncryptionMethod ENC_MTHD = EncryptionMethod.A256GCM;

    /**
     * Encrypts the payload using JWE with Mastercard's public key
     *
     * @param plainData           The plain text payload to encrypt
     * @param certificateFile     Mastercard's encryption certificate (.crt or .p12)
     * @param keyFingerPrint      Encryption key fingerprint
     * @param requestContentType  Content type (application/json or application/xml)
     * @param certificatePassword Password for .p12 certificate file (can be null for .crt files)
     * @return JWE encrypted string
     */
    public static String jweEncrypt(String plainData, Resource certificateFile, String keyFingerPrint,
                                    String requestContentType, String certificatePassword) {
        try {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) getPublicKeyFromCertificate(certificateFile, certificatePassword);
            return encryptWithPublicKey(plainData, rsaPublicKey, keyFingerPrint, requestContentType);
        } catch (Exception e) {
            log.error("Error encrypting payload", e);
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a JWE encrypted response from Mastercard CBS
     *
     * @param cipher               The encrypted JWE string
     * @param privateKeyFile       Private key file for decryption (.p12 or .pem)
     * @param decryptionKeyAlias   Key alias (for .p12 files)
     * @param decryptionPassword   Password (for .p12 files)
     * @return Decrypted plain text
     */
    public static String jweDecrypt(String cipher, Resource privateKeyFile, String decryptionKeyAlias,
                                    String decryptionPassword) {
        try {
            JWEObject jwe = JWEObject.parse(cipher);

            // Get private key from file
            PrivateKey privateKey = getPrivateKey(privateKeyFile, decryptionKeyAlias, decryptionPassword);

            // Decrypt
            jwe.decrypt(new RSADecrypter((RSAPrivateKey) privateKey));
            return jwe.getPayload().toString();
        } catch (Exception e) {
            log.error("Error decrypting payload", e);
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Loads private key from PKCS12 keystore or PEM file
     */
    private static PrivateKey getPrivateKey(Resource keyFile, String keyAlias, String password)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException,
            UnrecoverableKeyException {

        try (InputStream is = keyFile.getInputStream()) {
            // Try PKCS12 format first
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(is, password != null ? password.toCharArray() : null);
            return (RSAPrivateKey) keyStore.getKey(keyAlias, password != null ? password.toCharArray() : null);
        }
    }

    /**
     * Encrypts data with RSA public key using JWE
     */
    private static String encryptWithPublicKey(String plainData, RSAPublicKey rsaPublicKey, String keyFingerPrint,
                                               String requestContentType) throws JOSEException {
        // Build JWE header
        JWEHeader.Builder builder = new JWEHeader.Builder(ALG, ENC_MTHD);

        if (keyFingerPrint != null) {
            builder.keyID(keyFingerPrint);
        }

        if (requestContentType != null) {
            builder.contentType(requestContentType);
        }

        JWEHeader jweHeader = builder.build();

        // Create JWE object
        JWEObject jwe = new JWEObject(jweHeader, new Payload(plainData));

        // Encrypt with RSA public key
        RSAEncrypter encrypter = new RSAEncrypter(rsaPublicKey);
        jwe.encrypt(encrypter);

        return jwe.serialize();
    }

    /**
     * Loads public key from certificate file (.crt or from .p12)
     */
    private static PublicKey getPublicKeyFromCertificate(Resource certFile, String password)
            throws IOException, CertificateException, NoSuchProviderException, KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException {

        try (InputStream is = certFile.getInputStream()) {
            String filename = certFile.getFilename();

            // Check if it's a PKCS12 file
            if (filename != null && filename.endsWith(".p12")) {
                // Load from PKCS12 keystore
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                char[] passwordChars = password != null ? password.toCharArray() : "keystorepassword".toCharArray();
                keyStore.load(is, passwordChars);

                // Get the first alias
                String alias = keyStore.aliases().nextElement();
                return keyStore.getCertificate(alias).getPublicKey();
            } else {
                // Load from PEM/CRT file
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return factory.generateCertificate(is).getPublicKey();
            }
        }
    }
}
