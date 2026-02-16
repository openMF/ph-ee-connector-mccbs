package org.mifos.connector.mastercard.model.xml;

import jakarta.xml.bind.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * XML model for Mastercard CBS Payment Request
 * Format matches Mastercard Cross-Border Services API specification (reference app)
 * Root element is <paymentrequest> (no wrapper)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "paymentrequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentRequestXml {

    @XmlElement(name = "transaction_reference")
    private String transactionReference;

    @XmlElement(name = "recipient_account_uri")
    private String recipientAccountUri;

    @XmlElement(name = "payment_amount")
    private PaymentAmount paymentAmount;

    @XmlElement(name = "payment_origination_country")
    private String paymentOriginationCountry;

    @XmlElement(name = "payment_type")
    private String paymentType;

    @XmlElement(name = "sender")
    private Sender sender;

    @XmlElement(name = "recipient")
    private Recipient recipient;

    @XmlElement(name = "purpose_of_payment")
    private String purposeOfPayment;

    @XmlElement(name = "additional_data")
    private AdditionalData additionalData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PaymentAmount {
        @XmlElement(name = "amount")
        private String amount;

        @XmlElement(name = "currency")
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Sender {
        @XmlElement(name = "organization_name")
        private String organizationName;

        @XmlElement(name = "address")
        private Address address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Recipient {
        @XmlElement(name = "first_name")
        private String firstName;

        @XmlElement(name = "last_name")
        private String lastName;

        @XmlElement(name = "address")
        private Address address;

        @XmlElement(name = "email")
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Address {
        @XmlElement(name = "city")
        private String city;

        @XmlElement(name = "country")
        private String country;

        @XmlElement(name = "line1")
        private String line1;

        @XmlElement(name = "line2")
        private String line2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AdditionalData {
        @XmlElement(name = "data_field")
        private List<DataField> dataFields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DataField {
        @XmlElement(name = "name")
        private String name;

        @XmlElement(name = "value")
        private String value;
    }
}
