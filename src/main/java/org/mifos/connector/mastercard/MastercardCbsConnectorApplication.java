package org.mifos.connector.mastercard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// @Deployment removed - workflows are deployed separately with tenant substitution
public class MastercardCbsConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MastercardCbsConnectorApplication.class, args);
    }
}
