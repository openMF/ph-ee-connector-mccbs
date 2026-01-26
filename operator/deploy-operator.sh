#!/bin/bash
# Deploy Mastercard CBS Operator

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPERATOR_DIR="$SCRIPT_DIR"

echo "======================================"
echo "Deploying Mastercard CBS Operator"
echo "======================================"

# Function to deploy CRD
deploy_crd() {
    echo "Installing Custom Resource Definition..."
    kubectl apply --validate=false -f "$OPERATOR_DIR/config/crd/mastercard-cbs-connector.yaml"

    echo "Waiting for CRD to be established..."
    kubectl wait --for condition=established --timeout=60s crd/mastercardcbsconnectors.paymenthub.mifos.io
    echo "✓ CRD installed"
}

# Function to deploy RBAC
deploy_rbac() {
    echo "Creating RBAC resources..."

    # Create namespace if it doesn't exist
    kubectl create namespace mastercard-demo --dry-run=client -o yaml | kubectl apply -f -

    # Deploy service account, role, and role binding
    kubectl apply -f "$OPERATOR_DIR/config/rbac/service_account.yaml"
    kubectl apply -f "$OPERATOR_DIR/config/rbac/role.yaml"
    kubectl apply -f "$OPERATOR_DIR/config/rbac/role_binding.yaml"

    echo "✓ RBAC configured"
}

# Function to deploy operator controller
deploy_controller() {
    echo "Deploying operator controller..."

    # Create ConfigMap with reconcile script
    kubectl create configmap mastercard-operator-scripts \
        --from-file="$OPERATOR_DIR/controllers/reconcile.sh" \
        -n mastercard-demo \
        --dry-run=client -o yaml | kubectl apply -f -

    # Deploy controller as a Deployment
    cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mastercard-cbs-operator
  namespace: mastercard-demo
  labels:
    app.kubernetes.io/name: mastercard-cbs-operator
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mastercard-cbs-operator
  template:
    metadata:
      labels:
        app: mastercard-cbs-operator
    spec:
      serviceAccountName: mastercard-cbs-operator
      containers:
      - name: operator
        image: bitnami/kubectl:latest
        command:
          - /bin/bash
          - /scripts/reconcile.sh
        env:
        - name: HOME
          value: /tmp
        volumeMounts:
        - name: scripts
          mountPath: /scripts
        - name: mastercard-data
          mountPath: /opt/mastercard
        resources:
          limits:
            cpu: "200m"
            memory: "256Mi"
          requests:
            cpu: "100m"
            memory: "128Mi"
      volumes:
      - name: scripts
        configMap:
          name: mastercard-operator-scripts
          defaultMode: 0755
      - name: mastercard-data
        hostPath:
          path: $HOME/ph-ee-connector-mccbs
          type: Directory
EOF

    echo "✓ Controller deployed"
}

# Function to verify deployment
verify_deployment() {
    echo ""
    echo "Verifying deployment..."

    echo "Checking CRD..."
    kubectl get crd mastercardcbsconnectors.paymenthub.mifos.io || {
        echo "✗ CRD not found"
        return 1
    }

    echo "Checking operator pod..."
    kubectl wait --for=condition=ready --timeout=60s pod -l app=mastercard-cbs-operator -n mastercard-demo || {
        echo "✗ Operator pod not ready"
        kubectl logs -l app=mastercard-cbs-operator -n mastercard-demo --tail=50
        return 1
    }

    echo "✓ Operator is running"
}

# Main deployment flow
main() {
    deploy_crd
    deploy_rbac
    deploy_controller
    verify_deployment

    echo ""
    echo "======================================"
    echo "✅ Operator deployed successfully"
    echo "======================================"
    echo ""
    echo "Next steps:"
    echo "  1. Create a MastercardCBSConnector resource:"
    echo "     kubectl apply -f $OPERATOR_DIR/config/samples/mastercard-cbs-default.yaml"
    echo ""
    echo "  2. Check status:"
    echo "     kubectl get mastercardcbsconnectors -n mastercard-demo"
    echo ""
    echo "  3. View operator logs:"
    echo "     kubectl logs -l app=mastercard-cbs-operator -n mastercard-demo -f"
    echo ""
}

# Handle command line arguments
case "${1:-deploy}" in
    deploy)
        main
        ;;
    undeploy)
        echo "Undeploying operator..."
        kubectl delete deployment mastercard-cbs-operator -n mastercard-demo --ignore-not-found=true
        kubectl delete configmap mastercard-operator-scripts -n mastercard-demo --ignore-not-found=true
        kubectl delete -f "$OPERATOR_DIR/config/rbac/role_binding.yaml" --ignore-not-found=true
        kubectl delete -f "$OPERATOR_DIR/config/rbac/role.yaml" --ignore-not-found=true
        kubectl delete -f "$OPERATOR_DIR/config/rbac/service_account.yaml" --ignore-not-found=true
        echo "Note: CRD and CRs are preserved. To remove:"
        echo "  kubectl delete mastercardcbsconnectors --all --all-namespaces"
        echo "  kubectl delete crd mastercardcbsconnectors.paymenthub.mifos.io"
        ;;
    status)
        echo "Operator status:"
        kubectl get deployment mastercard-cbs-operator -n mastercard-demo || echo "Operator not deployed"
        echo ""
        echo "MastercardCBSConnector resources:"
        kubectl get mastercardcbsconnectors --all-namespaces
        ;;
    *)
        echo "Usage: $0 {deploy|undeploy|status}"
        exit 1
        ;;
esac
