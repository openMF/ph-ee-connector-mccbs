package org.mifos.connector.mastercard.model.xml;

import jakarta.xml.bind.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * XML model for Mastercard CBS Payment Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "payment")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentResponseXml {

    @XmlElement(name = "transaction_reference")
    private String transactionReference;

    @XmlElement(name = "status")
    private String status;

    @XmlElement(name = "id")
    private String id;

    @XmlElement(name = "resource_type")
    private String resourceType;

    @XmlElement(name = "created")
    private String created;

    @XmlElement(name = "status_timestamp")
    private String statusTimestamp;
}
