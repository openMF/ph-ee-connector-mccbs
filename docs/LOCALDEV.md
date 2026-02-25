# Local Development Mode for Mastercard CBS Connector

## Overview

The Mastercard CBS connector supports local development mode through its Kubernetes operator. Unlike other Payment Hub EE components that use Helm charts and the `localdev.py` patcher, Mastercard CBS uses operator-based deployment with built-in localdev support in the Custom Resource spec.

## How It Works

When `spec.localdev.enabled: true` is set in the Custom Resource:

1. **Image Override**: Uses `eclipse-temurin:17` JDK image instead of the built connector image
2. **HostPath Mount**: Mounts your local `~/ph-ee-connector-mccbs` directory at `/app` in the container
3. **Command Override**: Runs `java -jar` on your locally-built JAR file
4. **Hot Reload**: Edit code → rebuild JAR → restart pod → instant changes

## Quick Start

### 1. Deploy with LocalDev Enabled

Apply the localdev Custom Resource:

```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-localdev.yaml
```

This CR has:
```yaml
spec:
  localdev:
    enabled: true
    hostPath: "/home/tdaly/ph-ee-connector-mccbs"
    jarPath: "/app/build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar"
```

### 2. Build Your JAR

```bash
cd ~/ph-ee-connector-mccbs
./gradlew bootJar
```

This creates: `build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar`

### 3. Verify Deployment

```bash
# Check pod is running
kubectl get pods -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs

# Check it's using the JDK image (not built connector image)
kubectl get pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -o jsonpath='{.items[0].spec.containers[0].image}'
# Should show: eclipse-temurin:17

# Check hostPath is mounted
kubectl describe pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs | grep -A5 "Volumes:"

# Check logs
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs --tail=50
```

## Development Workflow

### Make Code Changes

```bash
cd ~/ph-ee-connector-mccbs

# Edit Java files
vim src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java

# Rebuild JAR
./gradlew bootJar

# Restart pod to pick up changes
kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs

# Watch new pod start
kubectl get pods -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -w

# Check logs for your changes
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -f | grep "Registered worker"
```

## Switching Between LocalDev and Production

### To LocalDev:
```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-localdev.yaml
```

### To Production:
```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-default.yaml
```

### Or Edit In-Place:
```bash
kubectl edit mastercardcbsconnector mastercard-cbs -n mastercard-demo
```

Change:
```yaml
spec:
  localdev:
    enabled: false  # Disable localdev
```

The operator will automatically detect the change and redeploy with the production image.

## Custom Resource Spec Reference

### Production Mode

```yaml
apiVersion: paymenthub.mifos.io/v1alpha1
kind: MastercardCBSConnector
metadata:
  name: mastercard-cbs
  namespace: mastercard-demo
spec:
  enabled: true
  replicas: 1
  image:
    repository: ph-ee-connector-mastercard-cbs
    tag: "1.0.0"
  # No localdev section - uses built image
```

### LocalDev Mode

```yaml
apiVersion: paymenthub.mifos.io/v1alpha1
kind: MastercardCBSConnector
metadata:
  name: mastercard-cbs
  namespace: mastercard-demo
spec:
  enabled: true
  replicas: 1

  # Override image to JDK
  image:
    repository: eclipse-temurin
    tag: "17"

  # Enable localdev
  localdev:
    enabled: true
    hostPath: "/home/tdaly/ph-ee-connector-mccbs"
    jarPath: "/app/build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar"
```

## Operator Implementation

The operator's `reconcile.sh` (lines 233-320) handles localdev mode:

```bash
if [ "$localdev_enabled" == "true" ]; then
    # Use JDK image
    image_repo="eclipse-temurin"
    image_tag="17"

    # Add hostPath volume
    volumes_section="      volumes:
      - name: local-code
        hostPath:
          path: ${host_path}
          type: Directory"

    # Add volume mount
    volumemounts_section="        volumeMounts:
        - name: local-code
          mountPath: /app"

    # Override command
    command_section="        command: [\"java\"]
        args:
          - \"-jar\"
          - \"${jar_path}\""
fi
```

## Comparison with Other PHEE Components

| Component | Deployment Method | LocalDev Tool |
|-----------|-------------------|---------------|
| connector-channel | Helm chart | localdev.py patcher |
| bulk-processor | Helm chart | localdev.py patcher |
| operations-app | Helm chart | localdev.py patcher |
| **mastercard-cbs** | **Kubernetes Operator** | **Built-in CR spec** |

## Troubleshooting

### Pod Keeps Restarting

Check logs:
```bash
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs
```

Common issues:
- JAR file not found at specified path
- JAR not rebuilt after code changes
- HostPath doesn't exist or has wrong permissions

### JAR File Not Found

Verify hostPath and jarPath in CR match your actual paths:
```bash
# Check what's in the pod
kubectl exec -n mastercard-demo -it $(kubectl get pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -o name | head -1) -- ls -la /app/build/libs/
```

### Changes Not Appearing

Make sure to:
1. Rebuild JAR: `./gradlew bootJar`
2. Restart pod: `kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs`
3. Wait for new pod: `kubectl wait --for=condition=Ready pod -l app=ph-ee-connector-mastercard-cbs -n mastercard-demo`

### Operator Not Reacting to CR Changes

Check operator logs:
```bash
kubectl logs -n mastercard-demo -l app=mastercard-cbs-operator
```

Operator reconciles every 30 seconds. Force immediate reconciliation by restarting operator:
```bash
kubectl delete pod -n mastercard-demo -l app=mastercard-cbs-operator
```

## Advanced: Using Different JAR Names

If you change the JAR name in `build.gradle`:

1. Update `jarPath` in Custom Resource:
```yaml
spec:
  localdev:
    jarPath: "/app/build/libs/my-custom-name.jar"
```

2. Apply updated CR:
```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-localdev.yaml
```

## Simulator LocalDev Support

The Mastercard CBS mock simulator also supports localdev mode for development.

### Enable Simulator LocalDev via config.ini

Edit `~/mifos-gazelle/config/config.ini`:

```ini
[mastercard-demo]
# Enable simulator localdev mode
MASTERCARD_SIMULATOR_LOCALDEV_ENABLED = true
```

Then deploy:
```bash
cd ~/mifos-gazelle
sudo ./run.sh -a mastercard-demo
```

When `MASTERCARD_SIMULATOR_LOCALDEV_ENABLED = true`, the deployment will:
- Mount `~/mastercard-cbs-simulator` as hostPath → `/app` in simulator container
- Use JDK image (`eclipse-temurin:17`) instead of built simulator image
- Run the JAR from your local directory

### Simulator Development Loop

```bash
# Edit simulator code
vim ~/mastercard-cbs-simulator/src/main/java/com/mifos/simulator/controller/SimulatorController.java

# Rebuild JAR
cd ~/mastercard-cbs-simulator
./gradlew clean bootJar

# Restart simulator pod
kubectl delete pod -n mastercard-demo -l app=mastercard-cbs-simulator

# Watch logs
kubectl logs -n mastercard-demo -l app=mastercard-cbs-simulator -f
```

### Simulator Custom Resource Spec

```yaml
spec:
  simulator:
    enabled: true
    image:
      repository: eclipse-temurin
      tag: "17"
    localdev:
      enabled: true
      hostPath: "/home/tdaly/mastercard-cbs-simulator"
      jarPath: "/app/build/libs/mastercard-cbs-simulator-1.0.0-SNAPSHOT.jar"
```

## Integration with mifos-gazelle

### Deploying via config.ini

When deploying via `run.sh`, you can enable connector localdev mode via config.ini:

```ini
[mastercard-demo]
# Enable connector localdev mode
MASTERCARD_LOCALDEV_ENABLED = true
```

Then deploy:
```bash
cd ~/mifos-gazelle
sudo ./run.sh -a mastercard-demo
```

### Manual Custom Resource Application

Or apply the localdev Custom Resource manually after deployment:

```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-localdev.yaml
```

## Why Not Use localdev.py?

The `localdev.py` script is designed for **Helm chart deployments** where it:
1. Reads Helm `templates/deployment.yaml`
2. Creates backup (`_deployment.yaml.backup`)
3. Patches the YAML to add hostPath volumes
4. Marks file with `git skip-worktree` to prevent accidental commits

**Mastercard CBS uses an operator**, which means:
- No Helm `templates/deployment.yaml` to patch
- Deployment is generated dynamically by operator
- Configuration is in the **Custom Resource**, not Helm values

Therefore, localdev support is **built into the operator itself** via the CR spec.

### Helm vs Operator LocalDev Comparison

#### Helm-based Component (e.g., connector-channel)

**Setup:**
```bash
cd ~/mifos-gazelle/src/utils/localdev
./localdev.py channel  # Patches Helm deployment.yaml
```

**Result:**
- Modifies `repos/ph_template/helm/ph-ee-engine/connector-channel/templates/deployment.yaml`
- Adds hostPath volume and volume mount
- Overrides image and command
- Creates backup file

**Revert:**
```bash
./localdev.py --restore channel
```

#### Operator-based Component (Mastercard CBS)

**Setup:**
```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-localdev.yaml
```

**Result:**
- Operator reads `spec.localdev.enabled: true` from CR
- Operator generates deployment with hostPath, volume mount, image override, and command override
- No files modified on disk
- Changes applied dynamically

**Revert:**
```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-default.yaml
```

### Advantages of Operator-Based LocalDev

1. **No File Patching**: No need to modify and track deployment YAML files
2. **Git Clean**: No risk of accidentally committing local dev changes
3. **Declarative**: All config in Custom Resource (infrastructure as code)
4. **Easy Toggle**: Switch modes by applying different CR
5. **Automatic Reconciliation**: Operator ensures deployment matches desired state

## Summary

**For Mastercard CBS Local Development:**
1. ✅ Use Custom Resource with `localdev.enabled: true`
2. ✅ Rebuild JAR when code changes
3. ✅ Restart pod to pick up changes
4. ❌ Do NOT use `localdev.py` (it's for Helm charts only)

**Workflow:**
```bash
# One-time setup
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-localdev.yaml

# Development loop
edit code → ./gradlew bootJar → kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs
```

**Return to Production:**
```bash
kubectl apply -f ~/mifos-gazelle/src/operators/mastercard/config/samples/mastercard-cbs-default.yaml
```

---

**Document Created:** January 26, 2026
**Status:** Complete - Operator supports localdev via CR spec
**See Also:** [docs/OPERATOR_DEPLOYMENT_GUIDE.md](OPERATOR_DEPLOYMENT_GUIDE.md)
