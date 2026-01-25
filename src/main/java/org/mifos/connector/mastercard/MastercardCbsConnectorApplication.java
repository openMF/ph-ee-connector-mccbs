package org.mifos.connector.mastercard;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = "classpath*:*.bpmn")
public class MastercardCbsConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MastercardCbsConnectorApplication.class, args);
    }
}
