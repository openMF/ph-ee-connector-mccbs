-- Mastercard CBS Supplementary Data Table
-- Stores regulatory and beneficiary details required for CBS payments

CREATE TABLE IF NOT EXISTS mastercard_cbs_supplementary_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Lookup Keys
    payee_msisdn VARCHAR(20) UNIQUE NOT NULL COMMENT 'Payee MSISDN for lookup',
    payee_account_number VARCHAR(50) NOT NULL COMMENT 'Beneficiary account number',

    -- Beneficiary Personal Details
    beneficiary_full_name VARCHAR(255) NOT NULL COMMENT 'Full legal name',
    beneficiary_first_name VARCHAR(100) COMMENT 'First name',
    beneficiary_last_name VARCHAR(100) COMMENT 'Last name',

    -- Beneficiary Address
    beneficiary_address_line1 VARCHAR(255) COMMENT 'Address line 1',
    beneficiary_address_line2 VARCHAR(255) COMMENT 'Address line 2',
    beneficiary_city VARCHAR(100) COMMENT 'City',
    beneficiary_state VARCHAR(100) COMMENT 'State/Province',
    beneficiary_postal_code VARCHAR(20) COMMENT 'Postal/ZIP code',
    beneficiary_country_code CHAR(2) NOT NULL COMMENT 'ISO 3166-1 alpha-2 country code',

    -- Bank Details
    bank_name VARCHAR(255) NOT NULL COMMENT 'Bank name',
    bank_bic_swift VARCHAR(11) COMMENT 'BIC/SWIFT code',
    bank_routing_number VARCHAR(50) COMMENT 'Bank routing number',
    bank_country_code CHAR(2) NOT NULL COMMENT 'Bank country code',
    bank_branch_code VARCHAR(50) COMMENT 'Bank branch code',

    -- Regulatory/Compliance Fields
    purpose_of_payment VARCHAR(255) DEFAULT 'Government disbursement' COMMENT 'Purpose of payment',
    source_of_funds VARCHAR(100) DEFAULT 'GOVERNMENT' COMMENT 'Source of funds',
    beneficiary_tax_id VARCHAR(50) COMMENT 'Tax identification number',
    beneficiary_id_type VARCHAR(50) COMMENT 'ID document type',
    beneficiary_id_number VARCHAR(100) COMMENT 'ID document number',

    -- Metadata
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Active status flag',

    -- Indexes
    INDEX idx_msisdn (payee_msisdn),
    INDEX idx_account (payee_account_number),
    INDEX idx_active (is_active),
    INDEX idx_country (beneficiary_country_code)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Supplementary regulatory data for Mastercard CBS payments';

-- Sample data for 10 demo payees
INSERT INTO mastercard_cbs_supplementary_data
(payee_msisdn, payee_account_number, beneficiary_full_name, beneficiary_first_name, beneficiary_last_name,
 beneficiary_address_line1, beneficiary_city, beneficiary_state, beneficiary_postal_code, beneficiary_country_code,
 bank_name, bank_bic_swift, bank_country_code, purpose_of_payment)
VALUES
-- Payee 1 - US
('0495822412', '1001234567', 'John Doe', 'John', 'Doe',
 '123 Main Street', 'New York', 'NY', '10001', 'US',
 'First National Bank', 'FNBAUS33', 'US', 'Government pension'),

-- Payee 2 - UK
('0424942603', '2002345678', 'Jane Smith', 'Jane', 'Smith',
 '45 High Street', 'London', NULL, 'SW1A 1AA', 'GB',
 'Barclays Bank', 'BARCGB22', 'GB', 'Social welfare payment'),

-- Payee 3 - Spain
('0413356886', '3003456789', 'Carlos Rodriguez', 'Carlos', 'Rodriguez',
 'Calle Mayor 10', 'Madrid', NULL, '28013', 'ES',
 'Banco Santander', 'BSCHESMM', 'ES', 'Education grant'),

-- Payee 4 - Italy
('0487123456', '4004567890', 'Maria Garcia', 'Maria', 'Garcia',
 'Via Roma 25', 'Rome', NULL, '00100', 'IT',
 'UniCredit Bank', 'UNCRITMM', 'IT', 'Healthcare subsidy'),

-- Payee 5 - France
('0456789012', '5005678901', 'Pierre Dubois', 'Pierre', 'Dubois',
 'Rue de la Paix 5', 'Paris', NULL, '75002', 'FR',
 'BNP Paribas', 'BNPAFRPP', 'FR', 'Agricultural support'),

-- Payee 6 - Germany
('0498765432', '6006789012', 'Hans Mueller', 'Hans', 'Mueller',
 'Hauptstrasse 15', 'Berlin', NULL, '10115', 'DE',
 'Deutsche Bank', 'DEUTDEFF', 'DE', 'Family allowance'),

-- Payee 7 - Japan
('0423456789', '7007890123', 'Yuki Tanaka', 'Yuki', 'Tanaka',
 '1-1-1 Shibuya', 'Tokyo', NULL, '150-0002', 'JP',
 'Mitsubishi UFJ', 'BOTKJPJT', 'JP', 'Disaster relief'),

-- Payee 8 - China
('0465432109', '8008901234', 'Li Wei', 'Li', 'Wei',
 '888 Nanjing Road', 'Shanghai', NULL, '200001', 'CN',
 'Bank of China', 'BKCHCNBJ', 'CN', 'Rural development'),

-- Payee 9 - Saudi Arabia
('0487654321', '9009012345', 'Ahmed Hassan', 'Ahmed', 'Hassan',
 'King Fahd Road', 'Riyadh', NULL, '11564', 'SA',
 'Al Rajhi Bank', 'RJHISARI', 'SA', 'Housing assistance'),

-- Payee 10 - India
('0491234567', '1000123456', 'Priya Sharma', 'Priya', 'Sharma',
 'MG Road 42', 'Mumbai', NULL, '400001', 'IN',
 'HDFC Bank', 'HDFCINBB', 'IN', 'Women empowerment');

-- Verify data loaded
SELECT COUNT(*) as total_records FROM mastercard_cbs_supplementary_data WHERE is_active = true;
