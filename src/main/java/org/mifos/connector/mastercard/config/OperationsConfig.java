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
@ConfigurationProperties(prefix = "operations")
public class OperationsConfig {

    @NotNull
    private ApiConfig api = new ApiConfig();

    @Getter
    @Setter
    public static class ApiConfig {
        @NotBlank
        private String baseUrl;

        private Integer timeoutSeconds = 10;

        private Boolean enabled = true;
    }
}
