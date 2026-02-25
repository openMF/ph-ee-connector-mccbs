package org.mifos.connector.mastercard.config;

import org.mifos.connector.mastercard.interceptor.OAuth1SigningInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, MastercardConfig mastercardConfig, ResourceLoader resourceLoader) throws Exception {
        // Create OAuth1 signing interceptor
        Resource signingKeyResource = resourceLoader.getResource(mastercardConfig.getOauth1().getSigningKeyFile());

        OAuth1SigningInterceptor oauth1Interceptor = new OAuth1SigningInterceptor(
                mastercardConfig.getOauth1().getConsumerKey(),
                signingKeyResource,
                mastercardConfig.getOauth1().getSigningKeyAlias(),
                mastercardConfig.getOauth1().getSigningKeyPassword()
        );

        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(mastercardConfig.getPayment().getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(mastercardConfig.getPayment().getTimeoutSeconds()))
                .requestFactory(this::clientHttpRequestFactory)
                .interceptors(oauth1Interceptor)
                .build();

        // Add JAXB XML message converter for Mastercard CBS API XML support
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>(restTemplate.getMessageConverters());
        Jaxb2RootElementHttpMessageConverter jaxbConverter = new Jaxb2RootElementHttpMessageConverter();
        messageConverters.add(0, jaxbConverter);  // Add at beginning to prioritize XML
        restTemplate.setMessageConverters(messageConverters);

        return restTemplate;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setBufferRequestBody(false);
        return new BufferingClientHttpRequestFactory(factory);
    }
}
