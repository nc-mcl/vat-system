# Kubernetes Deployment Diagram

**What this shows:** The Kubernetes deployment architecture for the VAT system — namespace, workloads, services, config, secrets, and autoscaling. Reflects the manifests under `/infrastructure/k8s/`.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

---

```mermaid
graph TD
    subgraph INTERNET ["External / Internet"]
        CLIENT["👤 Client\n(Browser / API consumer)"]
        SKAT_EXT["SKAT API\n(api.skat.dk)"]
        VIES_EXT["VIES\n(ec.europa.eu)"]
        PEPPOL_EXT["PEPPOL / NemHandel"]
    end

    subgraph CLUSTER ["Kubernetes Cluster"]
        subgraph NS ["Namespace: vat-system"]

            INGRESS["Ingress Controller\nNGINX / cloud LB\nTLS termination\n/api → api-service"]

            subgraph API_DEP ["API Deployment (2 replicas)"]
                direction TB
                API_POD1["Pod: vat-api-xxx\neclipse-temurin:21-jre-alpine\nPort 8080\nLiveness: /actuator/health/liveness\nReadiness: /actuator/health/readiness\nMetrics: /actuator/metrics"]
                API_POD2["Pod: vat-api-yyy\neclipse-temurin:21-jre-alpine\nPort 8080\nLiveness: /actuator/health/liveness\nReadiness: /actuator/health/readiness\nMetrics: /actuator/metrics"]
            end

            HPA["HorizontalPodAutoscaler\nTarget: vat-api deployment\nMin: 2 / Max: 10 replicas\nMetric: CPU 70%"]

            API_SVC["Service: api-service\nType: ClusterIP\nPort 80 → 8080"]

            subgraph PG_SS ["PostgreSQL StatefulSet (1 replica — Phase 1)"]
                PG_POD["Pod: postgres-0\npostgres:16-alpine\nPort 5432\nPersistentVolumeClaim: 20Gi"]
            end

            PG_SVC["Service: postgres-service\nType: ClusterIP\nPort 5432"]

            PV["PersistentVolume\n20Gi (ReadWriteOnce)\nStorageClass: standard"]

            subgraph MCP_DEP ["MCP Server Deployment (1 replica — dev/AI tooling)"]
                MCP_POD["Pod: mcp-server-xxx\nnode:20-alpine\nPort 3000\nDev/AI tooling only\nNot part of production SLA"]
            end

            MCP_SVC["Service: mcp-service\nType: ClusterIP\nPort 3000"]

            CM_API["ConfigMap: api-config\nSPRING_PROFILES_ACTIVE\nSKAT_API_BASE_URL\nVIES_BASE_URL\nPEPPOL_BASE_URL\nJDBC_URL (non-secret part)\nLOGGING_LEVEL"]

            CM_MCP["ConfigMap: mcp-config\nNODE_ENV\nDOCS_PATH"]

            SECRETS["Secret: vat-system-secrets\n(values never stored in Git)\nDB_PASSWORD\nSKAT_API_KEY\nPEPPOL_CERTIFICATE\nJWT_SECRET"]
        end

        PROMETHEUS["Prometheus\n(scrapes /actuator/metrics)"]
    end

    CLIENT -->|HTTPS| INGRESS
    INGRESS --> API_SVC
    API_SVC --> API_POD1
    API_SVC --> API_POD2

    API_POD1 -->|JDBC / JOOQ| PG_SVC
    API_POD2 -->|JDBC / JOOQ| PG_SVC
    PG_SVC --> PG_POD
    PG_POD --> PV

    API_POD1 -->|HTTPS| SKAT_EXT
    API_POD1 -->|HTTPS/SOAP| VIES_EXT
    API_POD1 -->|HTTPS/AS4| PEPPOL_EXT
    API_POD2 -->|HTTPS| SKAT_EXT
    API_POD2 -->|HTTPS/SOAP| VIES_EXT
    API_POD2 -->|HTTPS/AS4| PEPPOL_EXT

    HPA -.->|scales| API_DEP

    CM_API -.->|mounted env| API_POD1
    CM_API -.->|mounted env| API_POD2
    CM_MCP -.->|mounted env| MCP_POD
    SECRETS -.->|mounted env| API_POD1
    SECRETS -.->|mounted env| API_POD2

    PROMETHEUS -.->|scrape| API_POD1
    PROMETHEUS -.->|scrape| API_POD2

    style INTERNET fill:#f9f9f9,stroke:#999
    style CLUSTER fill:#e8f4fd,stroke:#2196F3
    style NS fill:#fff9e6,stroke:#FFA000
    style API_DEP fill:#e8f5e9,stroke:#4CAF50
    style PG_SS fill:#fce4ec,stroke:#E91E63
    style MCP_DEP fill:#f3e5f5,stroke:#9C27B0
    style SECRETS fill:#ffebee,stroke:#f44336
```

---

## Resource Limits (all pods)

| Pod | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---|---|---|---|---|
| `vat-api` | 250m | 500m | 256Mi | 512Mi |
| `postgres` | 250m | 500m | 512Mi | 1Gi |
| `mcp-server` | 100m | 200m | 128Mi | 256Mi |

## Manifest Locations

| Resource | Path |
|---|---|
| Namespace | `/infrastructure/k8s/cluster/namespace.yaml` |
| Ingress | `/infrastructure/k8s/cluster/ingress.yaml` |
| Secrets template | `/infrastructure/k8s/cluster/secrets-template.yaml` |
| API deployment | `/infrastructure/k8s/api/deployment.yaml` |
| API service | `/infrastructure/k8s/api/service.yaml` |
| API configmap | `/infrastructure/k8s/api/configmap.yaml` |
| API HPA | `/infrastructure/k8s/api/hpa.yaml` |
| PostgreSQL statefulset | `/infrastructure/k8s/persistence/deployment.yaml` |
| PostgreSQL service | `/infrastructure/k8s/persistence/service.yaml` |
| MCP server deployment | `/infrastructure/k8s/mcp-server/deployment.yaml` |
| MCP server service | `/infrastructure/k8s/mcp-server/service.yaml` |
| MCP server configmap | `/infrastructure/k8s/mcp-server/configmap.yaml` |

## Notes

- **Secrets** are never committed to Git. `secrets-template.yaml` shows required keys with placeholder values only.
- **PostgreSQL** runs as a single StatefulSet replica in Phase 1. A read replica or managed cloud DB (e.g. Cloud SQL, RDS) is recommended for Phase 2+.
- **MCP Server** is for developer/AI tooling only. It is not subject to production SLAs and should not be exposed outside the cluster.
- All pods run as **non-root** users per the ROLE_CONTEXT_POLICY container requirements.
- **OpenTelemetry** collector (not shown) should be deployed as a DaemonSet in production to receive traces from all pods.
