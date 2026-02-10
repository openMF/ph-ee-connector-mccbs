# Mastercard CBS Connector - Local Development

## Overview

The Mastercard CBS connector uses a **Kubernetes Operator** deployment pattern, which differs from other Payment Hub EE components that use Helm charts. This means local development setup is different.

## Differences from Other PHEE Components

| Aspect | Other PHEE Components | Mastercard CBS |
|--------|----------------------|----------------|
| Deployment | Helm charts | Kubernetes Operator |
| LocalDev Tool | `localdev.py` patcher | Custom Resource spec |
| Config Location | `localdev.ini` | `mastercard-cbs-localdev.yaml` |
| How It Works | Patches `deployment.yaml` in Helm chart | Operator reads CR and generates deployment |

## Quick Start

### 1. Apply LocalDev Custom Resource

```bash
kubectl apply -f ~/ph-ee-connector-mccbs/operator/config/samples/mastercard-cbs-localdev.yaml
```

This enables:
- HostPath mounting of `~/ph-ee-connector-mccbs` → `/app` in container
- JDK image (`eclipse-temurin:17`) instead of built connector image
- Command override to run your local JAR

### 2. Build JAR

```bash
cd ~/ph-ee-connector-mccbs
./gradlew bootJar
```

### 3. Development Loop

```bash
# Edit code
vim src/main/java/org/mifos/connector/mastercard/zeebe/MastercardCbsWorkers.java

# Rebuild
./gradlew bootJar

# Restart pod
kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs

# Watch logs
kubectl logs -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs -f
```

## Simulator LocalDev Support

The Mastercard CBS mock simulator also supports localdev mode for development:

### Enable Simulator LocalDev via config.ini

Edit `config/config.ini`:

```ini
[mastercard-demo]
# Enable simulator localdev mode
MASTERCARD_SIMULATOR_LOCALDEV_ENABLED = true
```

### Enable Simulator LocalDev via run.sh flag

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

# Rebuild JAR (Gradle-based project)
cd ~/mastercard-cbs-simulator
./gradlew clean bootJar

# Restart simulator pod
kubectl delete pod -n mastercard-demo -l app=mastercard-cbs-simulator

# Watch logs
kubectl logs -n mastercard-demo -l app=mastercard-cbs-simulator -f
```

### Simulator JAR Path

The simulator localdev expects the JAR at:
```
/app/build/libs/mastercard-cbs-simulator-1.0.0-SNAPSHOT.jar
```

This matches the standard Gradle output directory.

## Integration with mifos-gazelle run.sh

When deploying via `run.sh`, the operator uses the **default** Custom Resource (not localdev):

```bash
cd ~/mifos-gazelle
sudo ./run.sh -a mastercard-demo
```

This deploys with the built connector image.

To enable connector localdev mode via config.ini:

```ini
[mastercard-demo]
# Enable connector localdev mode
MASTERCARD_LOCALDEV_ENABLED = true
```

Or apply the localdev Custom Resource manually after deployment:

```bash
kubectl apply -f ~/ph-ee-connector-mccbs/operator/config/samples/mastercard-cbs-localdev.yaml
```

## Custom Resource Spec

The localdev mode is configured in the Custom Resource:

### Connector LocalDev

```yaml
spec:
  localdev:
    enabled: true
    hostPath: "/home/tdaly/ph-ee-connector-mccbs"
    jarPath: "/app/build/libs/ph-ee-connector-mastercard-cbs-1.0.0-SNAPSHOT.jar"
```

### Simulator LocalDev

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

**Note:** The connector and simulator can each independently enable/disable localdev mode.

## Operator Implementation

The operator's `reconcile.sh` script checks `spec.localdev.enabled` and modifies the deployment accordingly:

**File:** `~/ph-ee-connector-mccbs/operator/controllers/reconcile.sh` (lines 233-320)

```bash
if [ "$localdev_enabled" == "true" ]; then
    # Use JDK image
    image_repo="eclipse-temurin"
    image_tag="17"

    # Add hostPath volume
    # Add volume mount at /app
    # Override command to run JAR
fi
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

## Comparison: Helm vs Operator LocalDev

### Helm-based Component (e.g., connector-channel)

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

### Operator-based Component (Mastercard CBS)

**Setup:**
```bash
kubectl apply -f ~/ph-ee-connector-mccbs/operator/config/samples/mastercard-cbs-localdev.yaml
```

**Result:**
- Operator reads `spec.localdev.enabled: true` from CR
- Operator generates deployment with hostPath, volume mount, image override, and command override
- No files modified on disk
- Changes applied dynamically

**Revert:**
```bash
kubectl apply -f ~/ph-ee-connector-mccbs/operator/config/samples/mastercard-cbs-default.yaml
```

## Advantages of Operator-Based LocalDev

1. **No File Patching**: No need to modify and track deployment YAML files
2. **Git Clean**: No risk of accidentally committing local dev changes
3. **Declarative**: All config in Custom Resource (infrastructure as code)
4. **Easy Toggle**: Switch modes by applying different CR
5. **Automatic Reconciliation**: Operator ensures deployment matches desired state

## Documentation

For detailed documentation on Mastercard CBS local development, see:

**File:** `~/ph-ee-connector-mccbs/docs/LOCALDEV.md`

Key sections:
- Quick Start
- Development Workflow
- Troubleshooting
- Custom Resource Spec Reference

## Summary

**For Helm-based PHEE components:**
```bash
cd ~/mifos-gazelle/src/utils/localdev
./localdev.py <component>
```

**For Mastercard CBS Connector (operator-based):**
```bash
# Via config.ini
echo "MASTERCARD_LOCALDEV_ENABLED = true" >> config/config.ini
sudo ./run.sh -a mastercard-demo

# Or via kubectl
kubectl apply -f ~/ph-ee-connector-mccbs/operator/config/samples/mastercard-cbs-localdev.yaml

# Development loop
cd ~/ph-ee-connector-mccbs
./gradlew bootJar
kubectl delete pod -n mastercard-demo -l app=ph-ee-connector-mastercard-cbs
```

**For Mastercard CBS Simulator (operator-based):**
```bash
# Via config.ini
echo "MASTERCARD_SIMULATOR_LOCALDEV_ENABLED = true" >> config/config.ini
sudo ./run.sh -a mastercard-demo

# Development loop
cd ~/mastercard-cbs-simulator
./gradlew clean bootJar
kubectl delete pod -n mastercard-demo -l app=mastercard-cbs-simulator
```

**Key Difference:**
- Helm components: Patch `deployment.yaml` files
- Operator components: Configure via Custom Resource spec or config.ini

---

**Document Created:** January 26, 2026
**Integration Status:** Complete - Operator supports localdev natively
**See Also:**
- [docs/DEV-TEST-TIPS.md](DEV-TEST-TIPS.md) - Other PHEE components localdev
- [~/ph-ee-connector-mccbs/docs/LOCALDEV.md](../ph-ee-connector-mccbs/docs/LOCALDEV.md) - Mastercard CBS detailed guide
