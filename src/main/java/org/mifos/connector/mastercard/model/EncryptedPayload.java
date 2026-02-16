package org.mifos.connector.mastercard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for encrypted payload sent to/received from Mastercard CBS API
 * Uses encrypted_payload.data structure as per Mastercard's official reference implementation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedPayload {

    @JsonProperty("encrypted_payload")
    private EncryptedData encryptedPayload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncryptedData {
        @JsonProperty("data")
        private String data;
    }
}
