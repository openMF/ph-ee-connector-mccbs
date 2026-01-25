#!/usr/bin/env python3
"""
Generate Mastercard CBS Batch CSV

Creates a CSV batch file for Mastercard CBS payments using data from
the mastercard_cbs_supplementary_data table.

Usage:
    ./generate-mastercard-batch.py -c ~/tomconfig.ini [--count 10]
"""

import argparse
import configparser
import csv
import mysql.connector
import os
import random
import sys


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


def get_mastercard_payees(conn, limit=10):
    """Get payees from mastercard_cbs_supplementary_data"""
    cursor = conn.cursor(dictionary=True)

    query = """
        SELECT
            payee_msisdn,
            payee_account_number,
            recipient_first_name,
            recipient_last_name,
            recipient_address_country,
            bank_name
        FROM mastercard_cbs_supplementary_data
        WHERE is_active = TRUE
        ORDER BY id
        LIMIT %s
    """

    cursor.execute(query, (limit,))
    payees = cursor.fetchall()
    cursor.close()

    return payees


def generate_batch_csv(payees, output_file):
    """Generate CSV batch file"""

    with open(output_file, 'w', newline='') as f:
        writer = csv.writer(f)

        # Write header
        writer.writerow([
            'id',
            'request_id',
            'payment_mode',
            'payee_identifier_type',
            'payee_identifier',
            'amount',
            'currency',
            'note'
        ])

        # Write rows
        for idx, payee in enumerate(payees):
            amount = round(random.uniform(100.00, 1000.00), 2)

            writer.writerow([
                idx,
                f"cbs-{idx+1:04d}",
                'MASTERCARD_CBS',
                'MSISDN',
                payee['payee_msisdn'],
                amount,
                'ZAR',  # South African Rand
                f"Social grant payment to {payee['recipient_first_name']} {payee['recipient_last_name']} in {payee['recipient_address_country']}"
            ])

    return len(payees)


def main():
    parser = argparse.ArgumentParser(
        description='Generate Mastercard CBS batch CSV file'
    )
    parser.add_argument('-c', '--config', required=True,
                        help='Path to config file (e.g., ~/tomconfig.ini)')
    parser.add_argument('--count', type=int, default=10,
                        help='Number of payments to generate (default: 10)')
    parser.add_argument('-o', '--output', default='bulk-mastercard-cbs.csv',
                        help='Output CSV file name')

    args = parser.parse_args()

    print("=" * 70)
    print("Mastercard CBS Batch CSV Generator")
    print("=" * 70)
    print()

    # Load config
    print(f"Loading config from: {args.config}")
    config = load_config(args.config)

    # Connect to database
    print("Connecting to operations database...")
    conn = connect_operations_db(config)

    try:
        # Get payees
        print(f"Querying {args.count} payees from supplementary data...")
        payees = get_mastercard_payees(conn, args.count)

        if not payees:
            print()
            print("⚠️  No payees found in mastercard_cbs_supplementary_data")
            print("Run load-mastercard-supplementary-data.py first")
            return 1

        if len(payees) < args.count:
            print(f"⚠️  Only {len(payees)} payees available (requested {args.count})")

        # Generate CSV
        print(f"Generating CSV: {args.output}")
        count = generate_batch_csv(payees, args.output)

        print()
        print("=" * 70)
        print(f"✅ Generated batch CSV with {count} payments")
        print(f"   File: {args.output}")
        print("=" * 70)
        print()
        print("Payees included:")
        for payee in payees:
            print(f"  • {payee['payee_msisdn']} - {payee['recipient_first_name']} {payee['recipient_last_name']} ({payee['recipient_address_country']}) - {payee['bank_name']}")
        print()
        print("Next step:")
        print(f"  ./submit-batch.py -c ~/tomconfig.ini \\")
        print(f"    -f {args.output} \\")
        print(f"    --tenant greenbank \\")
        print(f"    --payment-mode MASTERCARD_CBS")
        print()

        return 0

    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return 1

    finally:
        conn.close()


if __name__ == '__main__':
    sys.exit(main())
