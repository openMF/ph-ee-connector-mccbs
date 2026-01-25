The purpose of this demo is to show how a disbursement from the GovStack solution can flow through Mastercard CBS allowing for cross-border payments.

This is envisaged to eventually be a capability but for this phase is for demo purposes only and so has the following limited scope:

Ability to receive a correct formed disbursement instruction from a source system (api format compliant with GovStack PayBB specification) to Mifos Payment Hub EE operating in a GovStack compliant mode. 

Data will be pre-populated into Account Mapper either by script or before demo starts.

Upon a valid instruction being received the Mifos Payment Hub EE will using its G2P flows process the instruction and submit to the Mastercard CBS Demo Connector (NEW) an instruction to make the payment.

The Mastercard CBS Demo Connector will take the instruction and will match the required regulatory supplementary data from a pre-populated table of 10 payees.

The Mastercard CBS Demo Connector will then send a valid formatted payment instruction to the Mastercard Sandbox (https://sandbox.api.mastercard.com ) according to the specification of the Payment API (

 )

The existing functionality of the Mifos Payment Hub EE solution should also exist in terms of being able to receive valid requests from the source system for the status of the payment (Batch Summary, Batch Details, Batch Payment Details) to enable the source system to have status.

(Optional - Nice to have) The Mifos Payment Hub EE solution will use the Mastercard CBS Retrieve Payment API (

 ) to update the status of the payment in its records.

Testing of the solution and support of the new components being added to the existing instance running in the GovStack sandbox will be required.

The Demo is not expected to be subject to warranty post the user acceptance testing and proof of work in the end environment.

This Demo is expected to be conceptual stepping stone to a full community (production) connector for Mifos Payment Hub EE to the Mastercard CBS service.

Subtasks and more data specifications 
PHEE-351
Create Supplementary Data Table
Description

Create a supplementary Data Table containing the following fields that will be pre-populated with data for lookup:

Data static for all lookups:

    sender.organisation_name

    sender.address.line1

    sender.address.city

    sender.address.country

    payment_origination_country

    Destination country(ISO-3): ZAF

    Beneficiary Currency and Decimal Precision: ZAR(2)

    Desitination Service Tag: ZAK-BK

    Payment Type: B2P

Data variable based on account details (index):

    recipient.first_name

    recipient.last_name

    recipient.address.line1

    recipient.phone

    recipient.email

    recipient.address.country

https://mifosforge.jira.com/browse/PHEE-354 
Ability to look up Supplementary Data Table based on Account Number
Description

Based on an account number look up and retrieve the corresponding data from the supplementary data table   

https://mifosforge.jira.com/browse/PHEE-354
On receipt of a payment instruction merge the data from the supplementary data table lookup. With the following information from the request:

Merged Field
	

Source
	

Source Field

sender.organization_name
	

supplementary data lookup
	

sender.organization_name

sender.address.line1
	

supplementary data lookup
	

sender.address.line1

sender.address.city
	

supplementary data lookup
	

sender.address.city

sender.address.country
	

supplementary data lookup
	

sender.address.country

recipient.first_name
	

supplementary data lookup
	

recipient.first_name

recipient.last_name
	

supplementary data lookup
	

recipient.last_name

recipient.address.line1
	

supplementary data lookup
	

recipient.address.line1

recipient.phone
	

supplementary data lookup
	

recipient.phone

recipient.email
	

supplementary data lookup
	

recipient.email

recipient.address.country
	

supplementary data lookup
	

recipient.address.country

purpose_of_payment
	

request
	

Payments Ref

payment_origination_country
	

supplementary data lookup
	

payment_origination_country

additional_data:701
	

supplementary data lookup
	

Destination Country (ISO-3)

additional_data:208
	

request
	

Recipient Alias Name (Functional ID) optional

Channel Type
	

static
	

Bank Account

Destination Country (ISO-3)
	

supplementary data lookup
	

Destination Country (ISO-3)

Beneficiary Currency and Decimal Precision
	

supplementary data lookup
	

Beneficiary Currency and Decimal Precision

Destination Service Tag
	

supplementary data lookup
	

Destination Service Tag

Payment Type
	

static
	

B2P

Special Note
	

request
	

notes

recipient_account_uri
	

composite
	

ban:{request:creditParty:value}.;bic=[A-Z]{4}ZA.{2}.
(8 or 11 characters SWIFT code in BIC)

transaction_reference
	

request
	

transactionId

amount
	

request
	

amount

currency
	

request
	

currency

fees_included
	

static
	

true

https://mifosforge.jira.com/browse/PHEE-356

Be able based on the merged data send a correctly formatted call to the Mastercard CBS Sandbox Payments API:



API Spec: https://developer.mastercard.com/cross-border-services/documentation/api-ref/payment-api/ 

{PartnerID}: to be supplied

POST/send/v1/partners/{partner-id}/crossborder/payment

<PaymentRequestWrapper>
  <paymentrequest>
    <transaction_reference>0982156QWECRTBYH034810klsrtsdrsd15490_7</transaction_reference>
    <proposal_id>prp_AFO0lQZIOfo-DmbP4cZfoDzh_1</proposal_id>
    <local_date_time>1231142230</local_date_time>
    <sender_account_uri>ewallet:payorID123456;sp=MTO#456</sender_account_uri>
    <recipient_account_uri>ban:30056001140114000251817;bic=CCFRFRPP</recipient_account_uri>
    <payment_amount>
      <amount>192.64</amount>
      <currency>USD</currency>
    </payment_amount>
    <payment_origination_country>CAN</payment_origination_country>
    <fx_type>
      <forward>
        <fees_included>true</fees_included>
        <receiver_currency>GBP</receiver_currency>
      </forward>
      <reverse>
        <sender_currency>USD</sender_currency>
      </reverse>
    </fx_type>
    <receiving_bank_name>Royal Exchange</receiving_bank_name>
    <receiving_bank_branch_name>Quad Cities</receiving_bank_branch_name>
    <bank_code>NP021</bank_code>
    <payment_type>B2B</payment_type>
    <source_of_income>Sal</source_of_income>
    <sender>
      <first_name>JOHN</first_name>
      <middle_name>Adam</middle_name>
      <last_name>SMITH</last_name>
      <organization_name>ABC Company, Mastercard Inc</organization_name>
      <nationality>USA</nationality>
      <address>
        <city>ANYTOWN</city>
        <postal_code>69999</postal_code>
        <country_subdivision>NM or New Mexico</country_subdivision>
        <country>USA</country>
        <line2>Suite 100</line2>
        <line1>42 WEST ELM AVENUE</line1>
      </address>
      <government_ids>
        <government_id_uri>ppn:123456789;expiration-date=2019-05-27;issue-date=2011-07-12;country=USA</government_id_uri>
      </government_ids>
      <date_of_birth>1985-06-24</date_of_birth>
    </sender>
    <recipient>
      <first_name>JOHN</first_name>
      <middle_name>Adam</middle_name>
      <last_name>SMITH</last_name>
      <organization_name>ABC Company, Mastercard Inc</organization_name>
      <nationality>USA</nationality>
      <address>
        <city>ANYTOWN</city>
        <postal_code>69999</postal_code>
        <country_subdivision>NM or New Mexico</country_subdivision>
        <country>USA</country>
        <line2>Suite 100</line2>
        <line1>42 WEST ELM AVENUE</line1>
      </address>
      <government_ids>
        <government_id_uri>ppn:123456789;expiration-date=2019-05-27;issue-date=2011-07-12;country=USA</government_id_uri>
      </government_ids>
      <email>customer@gmail.com</email>
    </recipient>
    <purpose_of_payment>Payment of goods and services</purpose_of_payment>
    <payment_file_identifier>15af297yg</payment_file_identifier>
    <card_rate_id>31rtypcq0m2yd15ml6f1a0zv14</card_rate_id>
    <additional_data>
      <data_field>
        <name>700</name>
        <value>USA</value>
      </data_field>
    </additional_data>
  </paymentrequest>
</PaymentRequestWrapper>

https://mifosforge.jira.com/browse/PHEE-357
Be able to interpret the response to the Payments API call and correctly update PayBB payment status based upon it:

 https://mifosforge.jira.com/browse/PHEE-358
 Description

OPTIONAL

Update the payment status based on the retrieve Payment API

 



