#!/bin/bash
# Mastercard CBS Operator - Reconciliation Controller
# This is a shell-based operator controller that can be migrated to Go later

set -e

# Logging functions
log_info() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] INFO: $*"; }
log_warn() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] WARN: $*" >&2; }
log_error() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2; }

# Reconcile function - main controller logic
reconcile() {
    local cr_name="$1"
    local namespace="$2"

    log_info "Reconciling MastercardCBSConnector: $cr_name in namespace: $namespace"

    # Get the CR spec
    local cr_json
    cr_json=$(kubectl get mastercardcbsconnector "$cr_name" -n "$namespace" -o json 2>/dev/null || echo "{}")

    if [ "$cr_json" == "{}" ]; then
        log_warn "CR $cr_name not found in namespace $namespace"
        return 1
    fi

    # Extract spec fields
    local enabled
    enabled=$(echo "$cr_json" | jq -r '.spec.enabled // false')

    if [ "$enabled" != "true" ]; then
        log_info "Connector is disabled, ensuring resources are cleaned up..."
        cleanup_resources "$cr_name" "$namespace"
        update_status "$cr_name" "$namespace" "Disabled"
        return 0
    fi

    # Update status to Initializing
    update_status "$cr_name" "$namespace" "Initializing"

    # Phase 1: Ensure namespace exists
    ensure_namespace "$namespace"

    # Phase 2: Create database schema and load data
    if [ "$(echo "$cr_json" | jq -r '.spec.dataLoading.autoLoad // true')" == "true" ]; then
        load_database_data "$cr_name" "$namespace" "$cr_json"
    fi

    # Phase 3: Deploy mock simulator if enabled
    if [ "$(echo "$cr_json" | jq -r '.spec.simulator.enabled // true')" == "true" ]; then
        deploy_simulator "$cr_name" "$namespace" "$cr_json"
    fi

    # Phase 4: Deploy connector
    deploy_connector "$cr_name" "$namespace" "$cr_json"

    # Phase 5: Deploy BPMN workflow
    if [ "$(echo "$cr_json" | jq -r '.spec.workflow.autoDeploy // true')" == "true" ]; then
        deploy_workflow "$cr_name" "$namespace" "$cr_json"
    fi

    # Update status to Ready
    update_status "$cr_name" "$namespace" "Ready"

    log_info "Reconciliation complete for $cr_name"
}

# Ensure namespace exists
ensure_namespace() {
    local namespace="$1"

    if ! kubectl get namespace "$namespace" >/dev/null 2>&1; then
        log_info "Creating namespace: $namespace"
        kubectl create namespace "$namespace"
    else
        log_info "Namespace already exists: $namespace"
    fi
}

# Load database data
load_database_data() {
    local cr_name="$1"
    local namespace="$2"
    local cr_json="$3"

    log_info "Loading database schema and data..."

    # Get PaymentHub database config
    local db_host
    db_host=$(echo "$cr_json" | jq -r '.spec.paymenthub.operationsDb.host // "operationsmysql.paymenthub.svc.cluster.local"')
    local db_port
    db_port=$(echo "$cr_json" | jq -r '.spec.paymenthub.operationsDb.port // 3306')
    local db_name
    db_name=$(echo "$cr_json" | jq -r '.spec.paymenthub.operationsDb.database // "operations"')

    # Create a Kubernetes Job to load data
    cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: ${cr_name}-data-loader
  namespace: ${namespace}
  labels:
    app.kubernetes.io/name: mastercard-cbs-data-loader
    app.kubernetes.io/instance: ${cr_name}
spec:
  ttlSecondsAfterFinished: 300
  template:
    spec:
      restartPolicy: OnFailure
      containers:
      - name: data-loader
        image: python:3.11-slim
        command:
          - /bin/bash
          - -c
          - |
            set -e
            echo "Installing dependencies..."
            pip install mysql-connector-python --quiet

            echo "Loading schema..."
            mysql -h ${db_host} -P ${db_port} -u root -p\${MYSQL_ROOT_PASSWORD} ${db_name} < /scripts/mastercard-cbs-schema-v2.sql || true

            echo "Running data loader..."
            python3 /scripts/load-mastercard-supplementary-data.py -c /config/config.ini

            echo "Data loading complete"
        env:
        - name: MYSQL_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: password
        volumeMounts:
        - name: scripts
          mountPath: /scripts
        - name: config
          mountPath: /config
      volumes:
      - name: scripts
        hostPath:
          path: $HOME/ph-ee-connector-mccbs/src/utils/data-loading
          type: Directory
      - name: config
        hostPath:
          path: $HOME
          type: Directory
EOF

    # Wait for job to complete
    log_info "Waiting for data loading job to complete..."
    kubectl wait --for=condition=complete --timeout=300s job/${cr_name}-data-loader -n "$namespace" || {
        log_error "Data loading job failed"
        kubectl logs job/${cr_name}-data-loader -n "$namespace"
        return 1
    }

    log_info "Database data loaded successfully"
}

# Deploy mock simulator
deploy_simulator() {
    local cr_name="$1"
    local namespace="$2"
    local cr_json="$3"

    log_info "Deploying mock Mastercard API simulator..."

    local image_repo
    image_repo=$(echo "$cr_json" | jq -r '.spec.simulator.image.repository // "mastercard-cbs-simulator"')
    local image_tag
    image_tag=$(echo "$cr_json" | jq -r '.spec.simulator.image.tag // "1.0.0"')

    kubectl apply -n "$namespace" -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mastercard-cbs-simulator
  namespace: ${namespace}
  labels:
    app.kubernetes.io/name: mastercard-cbs-simulator
    app.kubernetes.io/instance: ${cr_name}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mastercard-cbs-simulator
  template:
    metadata:
      labels:
        app: mastercard-cbs-simulator
    spec:
      containers:
      - name: simulator
        image: ${image_repo}:${image_tag}
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SERVER_PORT
          value: "8080"
        - name: OAUTH_ISSUER
          value: "mastercard-simulator"
        resources:
          limits:
            cpu: "200m"
            memory: "256Mi"
          requests:
            cpu: "100m"
            memory: "128Mi"
---
apiVersion: v1
kind: Service
metadata:
  name: mastercard-simulator
  namespace: ${namespace}
spec:
  selector:
    app: mastercard-cbs-simulator
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  type: ClusterIP
EOF

    log_info "Simulator deployed successfully"
}

# Deploy connector
deploy_connector() {
    local cr_name="$1"
    local namespace="$2"
    local cr_json="$3"

    log_info "Deploying CBS connector..."

    # Check if localdev mode is enabled
    local localdev_enabled
    localdev_enabled=$(echo "$cr_json" | jq -r '.spec.localdev.enabled // false')

    local image_repo
    local image_tag
    local command_section=""
    local volumes_section=""
    local volumemounts_section=""

    if [ "$localdev_enabled" == "true" ]; then
        log_info "Local development mode ENABLED"

        # Use JDK image for local dev
        image_repo=$(echo "$cr_json" | jq -r '.spec.localdev.image // "eclipse-temurin"')
        image_tag=$(echo "$cr_json" | jq -r '.spec.localdev.imageTag // "17"')

        local host_path
        host_path=$(echo "$cr_json" | jq -r '.spec.localdev.hostPath // env.HOME + "/ph-ee-connector-mccbs"')

        local jar_path
        jar_path=$(echo "$cr_json" | jq -r '.spec.localdev.jarPath // "/app/build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar"')

        log_info "  Host path: $host_path"
        log_info "  JAR path: $jar_path"
        log_info "  Image: $image_repo:$image_tag"

        # Add command override to run JAR
        command_section="        command: [\"java\"]
        args:
          - \"-jar\"
          - \"${jar_path}\"
          - \"--spring.profiles.active=default\""

        # Add volume mount
        volumemounts_section="        volumeMounts:
        - name: local-code
          mountPath: /app"

        # Add volume definition
        volumes_section="      volumes:
      - name: local-code
        hostPath:
          path: ${host_path}
          type: Directory"
    else
        # Use built image for production
        image_repo=$(echo "$cr_json" | jq -r '.spec.image.repository // "ph-ee-connector-mastercard-cbs"')
        image_tag=$(echo "$cr_json" | jq -r '.spec.image.tag // "1.0.0"')
    fi

    local replicas
    replicas=$(echo "$cr_json" | jq -r '.spec.replicas // 1')
    local mastercard_api_url
    mastercard_api_url=$(echo "$cr_json" | jq -r '.spec.mastercard.apiUrl // "http://mastercard-simulator:8080"')
    local zeebe_gateway
    zeebe_gateway=$(echo "$cr_json" | jq -r '.spec.paymenthub.zeebeGateway // "zeebe-gateway.paymenthub.svc.cluster.local:26500"')
    local db_host
    db_host=$(echo "$cr_json" | jq -r '.spec.paymenthub.operationsDb.host // "operationsmysql.paymenthub.svc.cluster.local"')

    kubectl apply -n "$namespace" -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ph-ee-connector-mastercard-cbs
  namespace: ${namespace}
  labels:
    app.kubernetes.io/name: ph-ee-connector-mastercard-cbs
    app.kubernetes.io/instance: ${cr_name}
spec:
  replicas: ${replicas}
  selector:
    matchLabels:
      app: ph-ee-connector-mastercard-cbs
  template:
    metadata:
      labels:
        app: ph-ee-connector-mastercard-cbs
    spec:
      containers:
      - name: connector
        image: ${image_repo}:${image_tag}
${command_section}
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: ZEEBE_BROKER_CONTACTPOINT
          value: "${zeebe_gateway}"
        - name: MASTERCARD_API_URL
          value: "${mastercard_api_url}"
        - name: MASTERCARD_AUTH_URL
          value: "${mastercard_api_url}/oauth/token"
        - name: DATASOURCE_URL
          value: "jdbc:mysql://${db_host}:3306/operations"
        - name: DATASOURCE_USERNAME
          value: "root"
        - name: DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: password
${volumemounts_section}
        resources:
          limits:
            cpu: "500m"
            memory: "512Mi"
          requests:
            cpu: "250m"
            memory: "256Mi"
${volumes_section}
---
apiVersion: v1
kind: Service
metadata:
  name: ph-ee-connector-mastercard-cbs
  namespace: ${namespace}
spec:
  selector:
    app: ph-ee-connector-mastercard-cbs
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  type: ClusterIP
EOF

    log_info "Connector deployed successfully"
}

# Deploy BPMN workflow
deploy_workflow() {
    local cr_name="$1"
    local namespace="$2"
    local cr_json="$3"

    log_info "Deploying BPMN workflow to Zeebe..."

    local zeebe_gateway
    zeebe_gateway=$(echo "$cr_json" | jq -r '.spec.paymenthub.zeebeGateway // "zeebe-gateway.paymenthub.svc.cluster.local:26500"')
    local workflow_path="$HOME/ph-ee-connector-mccbs/orchestration/bulk_connector_mastercard_cbs-DFSPID.bpmn"

    # Deploy using zbctl or kubectl exec into zeebe pod
    if command -v zbctl >/dev/null 2>&1; then
        zbctl deploy "$workflow_path" --address "$zeebe_gateway" || {
            log_warn "Failed to deploy workflow with zbctl, trying alternative method..."
            # Alternative: copy to a zeebe pod and deploy from there
            local zeebe_pod
            zeebe_pod=$(kubectl get pods -n paymenthub -l app.kubernetes.io/component=gateway -o name | head -1)
            if [ -n "$zeebe_pod" ]; then
                kubectl cp "$workflow_path" -n paymenthub "$zeebe_pod:/tmp/workflow.bpmn"
                kubectl exec -n paymenthub "$zeebe_pod" -- zbctl deploy /tmp/workflow.bpmn
            fi
        }
    else
        log_warn "zbctl not found, skipping workflow deployment"
        log_warn "Deploy manually: zbctl deploy $workflow_path"
    fi

    log_info "Workflow deployed successfully"
}

# Cleanup resources
cleanup_resources() {
    local cr_name="$1"
    local namespace="$2"

    log_info "Cleaning up resources for $cr_name in $namespace..."

    kubectl delete deployment ph-ee-connector-mastercard-cbs -n "$namespace" --ignore-not-found=true
    kubectl delete deployment mastercard-cbs-simulator -n "$namespace" --ignore-not-found=true
    kubectl delete service ph-ee-connector-mastercard-cbs -n "$namespace" --ignore-not-found=true
    kubectl delete service mastercard-simulator -n "$namespace" --ignore-not-found=true
    kubectl delete job ${cr_name}-data-loader -n "$namespace" --ignore-not-found=true

    log_info "Cleanup complete"
}

# Update CR status
update_status() {
    local cr_name="$1"
    local namespace="$2"
    local phase="$3"

    log_info "Updating status to: $phase"

    kubectl patch mastercardcbsconnector "$cr_name" -n "$namespace" --type=merge --subresource=status -p "{\"status\":{\"phase\":\"$phase\"}}" || true
}

# Watch for CR changes (simple polling for now)
watch_resources() {
    log_info "Starting operator controller..."

    while true; do
        # Get all MastercardCBSConnector CRs
        local crs
        crs=$(kubectl get mastercardcbsconnector --all-namespaces -o json 2>/dev/null || echo '{"items":[]}')

        # Reconcile each CR
        echo "$crs" | jq -r '.items[] | "\(.metadata.name)|\(.metadata.namespace)"' | while IFS='|' read -r name ns; do
            reconcile "$name" "$ns" || log_error "Failed to reconcile $name in $ns"
        done

        # Sleep before next reconciliation loop
        sleep 30
    done
}

# Main entry point
main() {
    log_info "Mastercard CBS Operator starting..."

    # Check prerequisites
    if ! command -v kubectl >/dev/null 2>&1; then
        log_error "kubectl not found"
        exit 1
    fi

    if ! command -v jq >/dev/null 2>&1; then
        log_error "jq not found"
        exit 1
    fi

    # Start watching
    watch_resources
}

# Run main if executed directly
if [ "${BASH_SOURCE[0]}" -ef "$0" ]; then
    main "$@"
fi
