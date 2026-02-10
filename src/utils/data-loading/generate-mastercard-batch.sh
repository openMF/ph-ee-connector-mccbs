#!/bin/bash
#
# Generate Mastercard CBS Batch CSV
#
# Creates a CSV batch file for Mastercard CBS payments using data from
# the mastercard_cbs_supplementary_data table.
#
# Usage:
#     ./generate-mastercard-batch.sh -c ~/tomconfig.ini [--count 10]
#

set -e

# Source helpers from mifos-gazelle
HELPERS_PATH="$HOME/mifos-gazelle/src/utils/helpers.sh"
if [[ -f "$HELPERS_PATH" ]]; then
    source "$HELPERS_PATH"
else
    echo "Error: helpers.sh not found at $HELPERS_PATH"
    exit 1
fi

CONFIG_FILE=""
COUNT=10
OUTPUT="bulk-mastercard-cbs.csv"
NAMESPACE="paymenthub"

usage() {
    echo "Usage: $0 -c <config_file> [--count N] [-o output.csv] [-n namespace]"
    echo ""
    echo "Options:"
    echo "  -c, --config     Path to config file (e.g., ~/tomconfig.ini)"
    echo "  --count          Number of payments to generate (default: 10)"
    echo "  -o, --output     Output CSV file name (default: bulk-mastercard-cbs.csv)"
    echo "  -n, --namespace  Kubernetes namespace (default: paymenthub)"
    exit 1
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        --count)
            COUNT="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT="$2"
            shift 2
            ;;
        -n|--namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

if [[ -z "$CONFIG_FILE" ]]; then
    echo "Error: Config file required"
    usage
fi

# Expand tilde
CONFIG_FILE="${CONFIG_FILE/#\~/$HOME}"

if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "Error: Config file not found: $CONFIG_FILE"
    exit 1
fi

# Parse INI file
get_ini_value() {
    local section=$1
    local key=$2
    local file=$3

    awk -F ' *= *' -v section="[$section]" -v key="$key" '
        $0 == section { in_section = 1; next }
        /^\[/ { in_section = 0 }
        in_section && $1 == key { print $2; exit }
    ' "$file"
}

# Override run_as_user if we're already the k8s user
run_as_k8s_user() {
    local command="$1"
    if [[ "$(whoami)" == "$k8s_user" ]]; then
        KUBECONFIG="$kubeconfig_path" eval "$command"
    else
        run_as_user "$command"
    fi
}

echo "======================================================================"
echo "Mastercard CBS Batch CSV Generator"
echo "======================================================================"
echo ""

echo "Loading config from: $CONFIG_FILE"
echo "Namespace: $NAMESPACE"

# Load k8s config from INI
k8s_user=$(get_ini_value "kubernetes" "k8s_user" "$CONFIG_FILE")
kubeconfig_path=$(get_ini_value "kubernetes" "kubeconfig_path" "$CONFIG_FILE")

# Expand $USER if present
k8s_user="${k8s_user//\$USER/$USER}"
kubeconfig_path="${kubeconfig_path/#\~/$HOME}"

# Get MySQL password from Kubernetes secret
echo "Getting MySQL credentials from Kubernetes..."
DB_PASS_B64=$(run_as_k8s_user "kubectl get secret operationsmysql -n $NAMESPACE -o jsonpath={.data.mysql-root-password}")
DB_PASS=$(echo "$DB_PASS_B64" | base64 -d)

if [[ -z "$DB_PASS" ]]; then
    echo "Error: Could not get MySQL password"
    exit 1
fi

# Find MySQL pod
MYSQL_POD=$(run_as_k8s_user "kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=operationsmysql -o jsonpath={.items[0].metadata.name}" 2>/dev/null)

if [[ -z "$MYSQL_POD" ]]; then
    echo "Error: Could not find MySQL pod"
    exit 1
fi

echo "Using MySQL pod: $MYSQL_POD"

# MySQL command helper
mysql_cmd() {
    local db=$1
    shift
    run_as_k8s_user "kubectl exec -n $NAMESPACE $MYSQL_POD -- mysql -u root -p'$DB_PASS' $db -N -s -e \"$*\"" 2>/dev/null
}

# Query payees
echo "Querying $COUNT payees from supplementary data..."

payees=$(mysql_cmd "operations" "SELECT payee_msisdn, payee_account_number, recipient_first_name, recipient_last_name, recipient_address_country, bank_name FROM mastercard_cbs_supplementary_data ORDER BY id LIMIT $COUNT")

if [[ -z "$payees" ]]; then
    echo ""
    echo "Warning: No payees found in mastercard_cbs_supplementary_data"
    echo "Run load-mastercard-supplementary-data.sh first"
    exit 1
fi

actual_count=$(echo "$payees" | wc -l)
if [[ "$actual_count" -lt "$COUNT" ]]; then
    echo "Warning: Only $actual_count payees available (requested $COUNT)"
fi

# Generate CSV
echo "Generating CSV: $OUTPUT"

# Write header
echo "id,request_id,payment_mode,payer_identifier_type,payer_identifier,payee_identifier_type,payee_identifier,amount,currency,note" > "$OUTPUT"

# Write rows
idx=0
while IFS=$'\t' read -r msisdn account first_name last_name country bank_name; do
    [[ -z "$msisdn" ]] && continue

    # Generate random amount between 100 and 1000
    amount=$(awk -v min=100 -v max=1000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')

    # Format request_id with leading zeros
    request_id=$(printf "cbs-%04d" $((idx + 1)))

    # Get payer MSISDN from greenbank tenant (government disbursement account)
    payer_msisdn="0413509790"

    # Write CSV row
    echo "$idx,$request_id,MASTERCARD_CBS,MSISDN,$payer_msisdn,MSISDN,$msisdn,$amount,ZAR,\"Social grant payment to $first_name $last_name in $country\"" >> "$OUTPUT"

    idx=$((idx + 1))
done <<< "$payees"

echo ""
echo "======================================================================"
echo "Generated batch CSV with $idx payments"
echo "   File: $OUTPUT"
echo "======================================================================"
echo ""
echo "Payees included:"

# Display payees
while IFS=$'\t' read -r msisdn account first_name last_name country bank_name; do
    [[ -z "$msisdn" ]] && continue
    echo "  - $msisdn - $first_name $last_name ($country) - $bank_name"
done <<< "$payees"

echo ""
echo "Next step:"
echo "  ./submit-batch.py -c ~/tomconfig.ini \\"
echo "    -f $OUTPUT \\"
echo "    --tenant greenbank \\"
echo "    --payment-mode MASTERCARD_CBS"
echo ""
