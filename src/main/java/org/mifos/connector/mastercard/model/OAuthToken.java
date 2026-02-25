package org.mifos.connector.mastercard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuthToken {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("scope")
    private String scope;

    // Calculated field - not from API
    private Instant expiryTime;

    public boolean isExpired() {
        if (expiryTime == null) {
            return true;
        }
        return Instant.now().isAfter(expiryTime);
    }

    public void calculateExpiryTime() {
        if (expiresIn != null && expiresIn > 0) {
            // Subtract 60 seconds as buffer
            this.expiryTime = Instant.now().plusSeconds(expiresIn - 60);
        }
    }
}
