-- Mastercard CBS Supplementary Data Table
-- For use with Mifos-Gazelle integration
-- South African government (payer) to international beneficiaries (payees)

CREATE TABLE IF NOT EXISTS mastercard_cbs_supplementary_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Lookup Keys (links to identity_account_mapper)
    payee_msisdn VARCHAR(20) UNIQUE NOT NULL COMMENT 'MSISDN from identity mapper',
    payee_account_number VARCHAR(50) NOT NULL COMMENT 'Account number from identity mapper',

    -- Static Sender Information (South African Government Payer)
    sender_organization_name VARCHAR(255) DEFAULT 'Department of Social Development, South Africa' COMMENT 'Government payer organization',
    sender_address_line1 VARCHAR(255) DEFAULT 'Batho Pele House, 186 Francis Baard Street' COMMENT 'Government office address',
    sender_address_line2 VARCHAR(255) DEFAULT NULL COMMENT 'Additional address line',
    sender_address_city VARCHAR(100) DEFAULT 'Pretoria' COMMENT 'Government office city',
    sender_address_postal_code VARCHAR(20) DEFAULT '0001' COMMENT 'Postal code',
    sender_address_country CHAR(3) DEFAULT 'ZAF' COMMENT 'ISO-3 country code - South Africa',
    payment_origination_country CHAR(3) DEFAULT 'ZAF' COMMENT 'Payment originates from South Africa',

    -- Static Destination/Service Information
    destination_service_tag VARCHAR(20) DEFAULT 'ZAK-BK' COMMENT 'South African banking service tag',
    payment_type VARCHAR(10) DEFAULT 'B2P' COMMENT 'Business to Person - government disbursements',
    channel_type VARCHAR(50) DEFAULT 'Bank Account' COMMENT 'Payment channel type',
    fees_included BOOLEAN DEFAULT TRUE COMMENT 'Fees included in amount',

    -- Static Currency Information
    beneficiary_currency CHAR(3) DEFAULT 'ZAR' COMMENT 'South African Rand - all payments in ZAR',
    beneficiary_currency_decimals TINYINT DEFAULT 2 COMMENT 'Decimal precision for ZAR',

    -- Variable Recipient Details (Per Payee - Can be any country)
    recipient_first_name VARCHAR(100) NOT NULL COMMENT 'Beneficiary first name',
    recipient_middle_name VARCHAR(100) DEFAULT NULL COMMENT 'Beneficiary middle name',
    recipient_last_name VARCHAR(100) NOT NULL COMMENT 'Beneficiary last name',
    recipient_address_line1 VARCHAR(255) NOT NULL COMMENT 'Beneficiary street address',
    recipient_address_line2 VARCHAR(255) DEFAULT NULL COMMENT 'Beneficiary address line 2',
    recipient_address_city VARCHAR(100) NOT NULL COMMENT 'Beneficiary city',
    recipient_address_postal_code VARCHAR(20) DEFAULT NULL COMMENT 'Beneficiary postal code',
    recipient_address_country_subdivision VARCHAR(100) DEFAULT NULL COMMENT 'State/province',
    recipient_address_country CHAR(2) NOT NULL COMMENT 'ISO-2 country code',
    recipient_phone VARCHAR(20) COMMENT 'Beneficiary phone (typically same as MSISDN)',
    recipient_email VARCHAR(255) COMMENT 'Beneficiary email address',
    recipient_nationality VARCHAR(50) DEFAULT NULL COMMENT 'Beneficiary nationality',
    recipient_date_of_birth DATE DEFAULT NULL COMMENT 'Beneficiary DOB',

    -- Bank Details (International banks accepted)
    bank_name VARCHAR(255) NOT NULL COMMENT 'Beneficiary bank name',
    bank_swift_code VARCHAR(11) NOT NULL COMMENT '8 or 11 character SWIFT/BIC code',
    bank_branch_name VARCHAR(255) DEFAULT NULL COMMENT 'Bank branch name',
    bank_branch_code VARCHAR(50) DEFAULT NULL COMMENT 'Bank branch code',
    bank_routing_number VARCHAR(50) DEFAULT NULL COMMENT 'Routing number if applicable',
    bank_country_code CHAR(2) NOT NULL COMMENT 'Bank country code',

    -- Government ID Information (Optional)
    recipient_id_type VARCHAR(50) DEFAULT NULL COMMENT 'ID type (passport, national ID, etc)',
    recipient_id_number VARCHAR(100) DEFAULT NULL COMMENT 'ID number',
    recipient_government_id_uri VARCHAR(500) DEFAULT NULL COMMENT 'Government ID URI format',

    -- Purpose and Compliance
    purpose_of_payment VARCHAR(255) DEFAULT 'Government social grant payment' COMMENT 'Payment purpose',
    source_of_income VARCHAR(100) DEFAULT 'GOVERNMENT' COMMENT 'Source of funds',
    special_note TEXT DEFAULT NULL COMMENT 'Additional notes',

    -- Additional Data Fields (for Mastercard additional_data element)
    additional_data_field_700 VARCHAR(10) DEFAULT NULL COMMENT 'Custom field 700',
    additional_data_field_701 VARCHAR(10) DEFAULT 'ZAF' COMMENT 'Custom field 701 - destination country',
    additional_data_field_208 VARCHAR(255) DEFAULT NULL COMMENT 'Recipient alias name',

    -- Metadata
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Active status',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update timestamp',
    created_by VARCHAR(100) DEFAULT 'system' COMMENT 'Created by user/system',
    updated_by VARCHAR(100) DEFAULT 'system' COMMENT 'Updated by user/system',

    -- Indexes for performance
    INDEX idx_msisdn (payee_msisdn),
    INDEX idx_account (payee_account_number),
    INDEX idx_active (is_active),
    INDEX idx_country (recipient_address_country),
    INDEX idx_bank (bank_swift_code),

    -- Constraints
    CONSTRAINT chk_swift_length CHECK (
        CHAR_LENGTH(bank_swift_code) IN (8, 11)
    ),
    CONSTRAINT chk_sender_country_zaf CHECK (
        sender_address_country = 'ZAF' AND payment_origination_country = 'ZAF'
    ),
    CONSTRAINT chk_beneficiary_currency_zar CHECK (
        beneficiary_currency = 'ZAR'
    )

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Mastercard CBS supplementary regulatory data - SA government to international beneficiaries';

-- Create indexes for foreign key-like relationships
CREATE INDEX idx_mastercard_lookup ON mastercard_cbs_supplementary_data(payee_msisdn, payee_account_number);

-- Sample comment showing relationship
ALTER TABLE mastercard_cbs_supplementary_data
COMMENT='Links to identity_account_mapper.identity_details via payee_msisdn';
