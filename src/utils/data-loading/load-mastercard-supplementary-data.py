#!/usr/bin/env python3
"""
Load Mastercard CBS Supplementary Data

Populates the mastercard_cbs_supplementary_data table with regulatory/compliance
information needed for Mastercard CBS cross-border payments.

Integration with Mifos-Gazelle:
- Uses same MSISDNs from identity_account_mapper
- Follows pattern of generate-mifos-vnext-data.py
- Works with existing mifos-gazelle tenant data

Usage:
    ./load-mastercard-supplementary-data.py -c ~/tomconfig.ini [--regenerate]
"""

import argparse
import configparser
import mysql.connector
import os
import random
import sys
from datetime import datetime, date

# International banks for demo data (matching countries from identity_account_mapper)
DEMO_BANKS = {
    'US': [
        {'name': 'JPMorgan Chase Bank', 'swift': 'CHASUS33', 'branch': 'New York Main'},
        {'name': 'Bank of America', 'swift': 'BOFAUS3N', 'branch': 'Los Angeles'},
        {'name': 'Wells Fargo Bank', 'swift': 'WFBIUS6S', 'branch': 'San Francisco'},
    ],
    'GB': [
        {'name': 'Barclays Bank', 'swift': 'BARCGB22', 'branch': 'London Main'},
        {'name': 'HSBC Bank', 'swift': 'HSBCGB2L', 'branch': 'Canary Wharf'},
        {'name': 'Lloyds Bank', 'swift': 'LOYDGB2L', 'branch': 'City of London'},
    ],
    'ES': [
        {'name': 'Banco Santander', 'swift': 'BSCHESMM', 'branch': 'Madrid Central'},
        {'name': 'BBVA', 'swift': 'BBVAESMM', 'branch': 'Barcelona'},
        {'name': 'CaixaBank', 'swift': 'CAIXESBB', 'branch': 'Valencia'},
    ],
    'IT': [
        {'name': 'UniCredit Bank', 'swift': 'UNCRITMM', 'branch': 'Milan Main'},
        {'name': 'Intesa Sanpaolo', 'swift': 'BCITITMM', 'branch': 'Turin'},
        {'name': 'Banco BPM', 'swift': 'BAPPIT21', 'branch': 'Rome'},
    ],
    'FR': [
        {'name': 'BNP Paribas', 'swift': 'BNPAFRPP', 'branch': 'Paris La Defense'},
        {'name': 'Societe Generale', 'swift': 'SOGEFRPP', 'branch': 'Paris'},
        {'name': 'Credit Agricole', 'swift': 'AGRIFRPP', 'branch': 'Montpellier'},
    ],
    'DE': [
        {'name': 'Deutsche Bank', 'swift': 'DEUTDEFF', 'branch': 'Frankfurt Main'},
        {'name': 'Commerzbank', 'swift': 'COBADEFF', 'branch': 'Frankfurt'},
        {'name': 'DZ Bank', 'swift': 'GENODEFF', 'branch': 'Frankfurt'},
    ],
    'JP': [
        {'name': 'Mitsubishi UFJ Bank', 'swift': 'BOTKJPJT', 'branch': 'Tokyo Main'},
        {'name': 'Sumitomo Mitsui Banking Corp', 'swift': 'SMBCJPJT', 'branch': 'Tokyo'},
        {'name': 'Mizuho Bank', 'swift': 'MHCBJPJT', 'branch': 'Tokyo'},
    ],
    'CN': [
        {'name': 'Bank of China', 'swift': 'BKCHCNBJ', 'branch': 'Beijing Main'},
        {'name': 'Industrial and Commercial Bank', 'swift': 'ICBKCNBJ', 'branch': 'Shanghai'},
        {'name': 'China Construction Bank', 'swift': 'PCBCCNBJ', 'branch': 'Shenzhen'},
    ],
    'SA': [
        {'name': 'Al Rajhi Bank', 'swift': 'RJHISARI', 'branch': 'Riyadh Main'},
        {'name': 'Saudi National Bank', 'swift': 'NCBKSAJE', 'branch': 'Jeddah'},
        {'name': 'Riyad Bank', 'swift': 'RIBLSARI', 'branch': 'Riyadh'},
    ],
    'IN': [
        {'name': 'HDFC Bank', 'swift': 'HDFCINBB', 'branch': 'Mumbai Main'},
        {'name': 'ICICI Bank', 'swift': 'ICICINBB', 'branch': 'New Delhi'},
        {'name': 'State Bank of India', 'swift': 'SBININBB', 'branch': 'Bangalore'},
    ],
}

# Demo recipient names by country
DEMO_NAMES = {
    'US': [('John', 'Doe'), ('Mary', 'Smith'), ('James', 'Johnson')],
    'GB': [('William', 'Brown'), ('Emma', 'Wilson'), ('Oliver', 'Taylor')],
    'ES': [('Carlos', 'Rodriguez'), ('Maria', 'Garcia'), ('Antonio', 'Martinez')],
    'IT': [('Marco', 'Rossi'), ('Giulia', 'Romano'), ('Luca', 'Ferrari')],
    'FR': [('Pierre', 'Dubois'), ('Marie', 'Leroy'), ('Jean', 'Moreau')],
    'DE': [('Hans', 'Mueller'), ('Anna', 'Schmidt'), ('Klaus', 'Weber')],
    'JP': [('Yuki', 'Tanaka'), ('Hiro', 'Sato'), ('Akiko', 'Suzuki')],
    'CN': [('Li', 'Wei'), ('Wang', 'Ming'), ('Zhang', 'Hua')],
    'SA': [('Ahmed', 'Hassan'), ('Fatima', 'Al-Saud'), ('Mohammed', 'Al-Rashid')],
    'IN': [('Priya', 'Sharma'), ('Raj', 'Patel'), ('Deepa', 'Kumar')],
}

# Demo cities by country
DEMO_CITIES = {
    'US': ['New York', 'Los Angeles', 'Chicago', 'Houston'],
    'GB': ['London', 'Manchester', 'Birmingham', 'Edinburgh'],
    'ES': ['Madrid', 'Barcelona', 'Valencia', 'Seville'],
    'IT': ['Rome', 'Milan', 'Naples', 'Turin'],
    'FR': ['Paris', 'Marseille', 'Lyon', 'Toulouse'],
    'DE': ['Berlin', 'Hamburg', 'Munich', 'Frankfurt'],
    'JP': ['Tokyo', 'Osaka', 'Kyoto', 'Yokohama'],
    'CN': ['Beijing', 'Shanghai', 'Guangzhou', 'Shenzhen'],
    'SA': ['Riyadh', 'Jeddah', 'Mecca', 'Medina'],
    'IN': ['Mumbai', 'Delhi', 'Bangalore', 'Chennai'],
}


def load_config(config_file):
    """Load configuration from INI file"""
    config = configparser.ConfigParser()
    config_path = os.path.expanduser(config_file)

    if not os.path.exists(config_path):
        print(f"Error: Config file not found: {config_path}")
        sys.exit(1)

    config.read(config_path)
    return config


def connect_operations_db(config):
    """Connect to operations database"""
    try:
        connection = mysql.connector.connect(
            host=config.get('operations_db', 'host'),
            user=config.get('operations_db', 'user'),
            password=config.get('operations_db', 'password'),
            database='operations',
            autocommit=False
        )
        return connection
    except mysql.connector.Error as err:
        print(f"Error connecting to operations database: {err}")
        sys.exit(1)


def connect_identity_mapper_db(config):
    """Connect to identity_account_mapper database"""
    try:
        connection = mysql.connector.connect(
            host=config.get('operations_db', 'host'),
            user=config.get('operations_db', 'user'),
            password=config.get('operations_db', 'password'),
            database='identity_account_mapper',
            autocommit=False
        )
        return connection
    except mysql.connector.Error as err:
        print(f"Error connecting to identity_account_mapper database: {err}")
        sys.exit(1)


def get_existing_beneficiaries(identity_conn):
    """
    Get existing beneficiaries from identity_account_mapper
    Returns list of (msisdn, account_number, institution_code) tuples
    """
    cursor = identity_conn.cursor()

    query = """
        SELECT DISTINCT
            id.payee_identity,
            pmd.destination_account,
            pmd.institution_code
        FROM identity_details id
        JOIN payment_modality_details pmd ON id.master_id = pmd.master_id
        WHERE id.payee_identity_type = 'MSISDN'
          AND pmd.destination_account IS NOT NULL
          AND pmd.institution_code IS NOT NULL
        ORDER BY id.payee_identity
    """

    cursor.execute(query)
    beneficiaries = cursor.fetchall()
    cursor.close()

    print(f"Found {len(beneficiaries)} beneficiaries in identity_account_mapper")
    return beneficiaries


def map_institution_to_country(institution_code):
    """
    Map institution code to country code
    Based on common patterns in test data
    """
    # In real implementation, this would query a proper mapping table
    # For demo, use simple heuristics
    country_map = {
        'greenbank': 'US',
        'redbank': 'GB',
        'bluebank': 'ES',
    }

    # Try direct mapping
    if institution_code.lower() in country_map:
        return country_map[institution_code.lower()]

    # Default rotation through countries for demo
    countries = ['US', 'GB', 'ES', 'IT', 'FR', 'DE', 'JP', 'CN', 'SA', 'IN']
    # Use hash for consistent assignment
    idx = hash(institution_code) % len(countries)
    return countries[idx]


def generate_recipient_data(msisdn, country_code):
    """Generate realistic recipient data for given country"""

    # Get demo data for country
    banks = DEMO_BANKS.get(country_code, DEMO_BANKS['US'])
    names = DEMO_NAMES.get(country_code, DEMO_NAMES['US'])
    cities = DEMO_CITIES.get(country_code, DEMO_CITIES['US'])

    # Select random bank and name
    bank = random.choice(banks)
    first_name, last_name = random.choice(names)
    city = random.choice(cities)

    # Generate email from MSISDN
    email_domain = {
        'US': 'example.com',
        'GB': 'example.co.uk',
        'ES': 'example.es',
        'IT': 'example.it',
        'FR': 'example.fr',
        'DE': 'example.de',
        'JP': 'example.jp',
        'CN': 'example.cn',
        'SA': 'example.sa',
        'IN': 'example.in',
    }.get(country_code, 'example.com')

    email = f"{first_name.lower()}.{last_name.lower()}@{email_domain}"

    # Generate address
    street_number = random.randint(1, 999)
    street_names = {
        'US': 'Main Street',
        'GB': 'High Street',
        'ES': 'Calle Mayor',
        'IT': 'Via Roma',
        'FR': 'Rue de la Paix',
        'DE': 'Hauptstrasse',
        'JP': 'Chuo-dori',
        'CN': 'Nanjing Road',
        'SA': 'King Fahd Road',
        'IN': 'MG Road',
    }.get(country_code, 'Main Road')

    address = f"{street_number} {street_names}"

    return {
        'first_name': first_name,
        'last_name': last_name,
        'address_line1': address,
        'city': city,
        'country': country_code,
        'phone': msisdn,
        'email': email,
        'bank_name': bank['name'],
        'bank_swift': bank['swift'],
        'bank_branch': bank['branch'],
    }


def check_existing_data(ops_conn):
    """Check if supplementary data already exists"""
    cursor = ops_conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM mastercard_cbs_supplementary_data")
    count = cursor.fetchone()[0]
    cursor.close()
    return count


def insert_supplementary_data(ops_conn, beneficiaries, regenerate=False):
    """Insert supplementary data for beneficiaries"""

    cursor = ops_conn.cursor()

    # Check if data exists
    existing_count = check_existing_data(ops_conn)

    if existing_count > 0:
        if not regenerate:
            print(f"Supplementary data already exists ({existing_count} records)")
            print("Use --regenerate to replace existing data")
            return 0
        else:
            print(f"Regenerating data (deleting {existing_count} existing records)...")
            cursor.execute("DELETE FROM mastercard_cbs_supplementary_data")
            ops_conn.commit()

    insert_sql = """
        INSERT INTO mastercard_cbs_supplementary_data (
            payee_msisdn,
            payee_account_number,
            recipient_first_name,
            recipient_last_name,
            recipient_address_line1,
            recipient_address_city,
            recipient_address_country,
            recipient_phone,
            recipient_email,
            bank_name,
            bank_swift_code,
            bank_branch_name,
            bank_country_code,
            purpose_of_payment,
            created_by
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
    """

    inserted = 0

    for msisdn, account, institution_code in beneficiaries:
        # Map institution to country
        country = map_institution_to_country(institution_code)

        # Generate recipient data
        recipient = generate_recipient_data(msisdn, country)

        try:
            cursor.execute(insert_sql, (
                msisdn,
                account,
                recipient['first_name'],
                recipient['last_name'],
                recipient['address_line1'],
                recipient['city'],
                recipient['country'],
                recipient['phone'],
                recipient['email'],
                recipient['bank_name'],
                recipient['bank_swift'],
                recipient['bank_branch'],
                recipient['country'],
                f"Government social grant payment to {recipient['first_name']} {recipient['last_name']}",
                'load-mastercard-supplementary-data.py'
            ))
            inserted += 1

            print(f"  ✓ {msisdn} → {recipient['first_name']} {recipient['last_name']} ({recipient['country']} - {recipient['bank_name']})")

        except mysql.connector.Error as err:
            print(f"  ✗ Error inserting {msisdn}: {err}")
            continue

    ops_conn.commit()
    cursor.close()

    return inserted


def main():
    parser = argparse.ArgumentParser(
        description='Load Mastercard CBS supplementary data from identity_account_mapper'
    )
    parser.add_argument('-c', '--config', required=True,
                        help='Path to config file (e.g., ~/tomconfig.ini)')
    parser.add_argument('--regenerate', action='store_true',
                        help='Regenerate data (delete and recreate)')

    args = parser.parse_args()

    print("=" * 70)
    print("Mastercard CBS Supplementary Data Loader")
    print("=" * 70)
    print()

    # Load config
    print(f"Loading config from: {args.config}")
    config = load_config(args.config)

    # Connect to databases
    print("Connecting to databases...")
    identity_conn = connect_identity_mapper_db(config)
    ops_conn = connect_operations_db(config)

    try:
        # Get beneficiaries from identity mapper
        print()
        print("Querying identity_account_mapper...")
        beneficiaries = get_existing_beneficiaries(identity_conn)

        if not beneficiaries:
            print()
            print("⚠️  No beneficiaries found in identity_account_mapper")
            print("Run generate-mifos-vnext-data.py first to populate identity mapper")
            return 1

        # Insert supplementary data
        print()
        print("Generating supplementary data...")
        inserted = insert_supplementary_data(ops_conn, beneficiaries, args.regenerate)

        print()
        print("=" * 70)
        print(f"✅ Successfully loaded {inserted} supplementary data records")
        print("=" * 70)
        print()
        print("Next steps:")
        print("  1. Generate Mastercard CBS batch CSV:")
        print("     ./generate-example-csv-files.py -c ~/tomconfig.ini --mastercard")
        print()
        print("  2. Submit batch:")
        print("     ./submit-batch.py -c ~/tomconfig.ini \\")
        print("       -f bulk-gazelle-mastercard-cbs-10.csv \\")
        print("       --tenant greenbank \\")
        print("       --payment-mode MASTERCARD_CBS")
        print()

        return 0

    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return 1

    finally:
        identity_conn.close()
        ops_conn.close()


if __name__ == '__main__':
    sys.exit(main())
