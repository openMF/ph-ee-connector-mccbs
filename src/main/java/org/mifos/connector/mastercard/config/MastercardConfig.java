package org.mifos.connector.mastercard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "mastercard")
public class MastercardConfig {

    @NotNull
    private ApiConfig api = new ApiConfig();

    @NotNull
    private OAuthConfig oauth = new OAuthConfig();

    @NotNull
    private OAuth1Config oauth1 = new OAuth1Config();

    @NotNull
    private EncryptionConfig encryption = new EncryptionConfig();

    @NotNull
    private PaymentConfig payment = new PaymentConfig();

    @NotNull
    private StatusConfig status = new StatusConfig();

    @Getter
    @Setter
    public static class ApiConfig {
        @NotBlank
        private String url;

        @NotBlank
        private String authUrl;

        @NotBlank
        private String partnerId;
    }

    @Getter
    @Setter
    public static class OAuthConfig {
        @NotBlank
        private String clientId;

        @NotBlank
        private String clientSecret;

        @NotBlank
        private String grantType = "client_credentials";

        private Integer tokenCacheSeconds = 3000;
    }

    @Getter
    @Setter
    public static class OAuth1Config {
        @NotBlank
        private String consumerKey;

        @NotBlank
        private String signingKeyFile;

        @NotBlank
        private String signingKeyAlias;

        @NotBlank
        private String signingKeyPassword;
    }

    @Getter
    @Setter
    public static class EncryptionConfig {
        private Boolean enabled = false;

        private String certificateFile;

        private String certificatePassword;

        private String fingerPrint;

        private String decryptionKeyFile;

        private String decryptionKeyAlias;

        private String decryptionKeyPassword;
    }

    @Getter
    @Setter
    public static class PaymentConfig {
        private Integer timeoutSeconds = 30;
        private Integer maxRetries = 3;
        private Integer retryDelaySeconds = 5;
    }

    @Getter
    @Setter
    public static class StatusConfig {
        private Boolean checkEnabled = true;
        private Integer pollIntervalSeconds = 3;
    }
}
