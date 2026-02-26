# Infrastructure

This directory contains all deployment and infrastructure resources for the VAT System.

## Directory Layout

```
infrastructure/
  k8s/
    cluster/        — Namespace, ingress, secrets template
    api/            — API Deployment, Service, ConfigMap, HPA
    mcp-server/     — MCP Server Deployment, Service, ConfigMap, HPA
    postgres/       — PostgreSQL StatefulSet and headless Service
  db/
    migrations/     — Flyway SQL migration scripts (also used by docker-compose)
```

---

## Dev Container (zero-step local setup)

The recommended way to develop is with the VS Code Dev Container:

1. Open this repository in VS Code
2. When prompted "Reopen in Container", click **Reopen in Container**
3. VS Code builds the container (first time ~3 min), then you are inside with:
   - Java 21, Node.js 20, git, Claude Code
   - PostgreSQL reachable at `postgres:5432`
   - Adminer at [http://localhost:8090](http://localhost:8090)

No manual steps required.

### Manually starting the dev container

```bash
cd .devcontainer
docker compose up -d
docker compose exec dev bash
```

---

## Local Development (without dev container)

Start the full stack (API + PostgreSQL + MCP server + Adminer) from the project root:

```bash
docker compose up -d
```

- VAT API: [http://localhost:8080](http://localhost:8080)
- MCP server: [http://localhost:3000](http://localhost:3000)
- Adminer: [http://localhost:8090](http://localhost:8090) — server: `postgres`, user: `vat`, password: `vat_dev_password`

### Environment variables for local development

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/vatdb` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `vat` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `vat_dev_password` | Database password (dev only) |
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile |

---

## Running CI locally with `act`

Install [`act`](https://github.com/nektos/act) then run:

```bash
act push -W .github/workflows/ci.yml
```

This runs the full GitHub Actions pipeline locally using Docker.

---

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (e.g. minikube, kind, or a cloud cluster)
- `kubectl` configured to point at your cluster
- Docker registry accessible from the cluster

### 1. Create namespace

```bash
kubectl apply -f infrastructure/k8s/cluster/namespace.yaml
kubectl apply -f infrastructure/k8s/cluster/ingress.yaml
```

### 2. Create secrets (never commit actual values)

```bash
kubectl create secret generic vat-secrets \
  --from-literal=db-password=<actual-password> \
  --from-literal=skat-api-key=<actual-key> \
  -n vat-system
```

### 3. Deploy PostgreSQL

```bash
kubectl apply -f infrastructure/k8s/postgres/service.yaml
kubectl apply -f infrastructure/k8s/postgres/statefulset.yaml
```

### 4. Deploy the API

```bash
kubectl apply -f infrastructure/k8s/api/configmap.yaml
kubectl apply -f infrastructure/k8s/api/deployment.yaml
kubectl apply -f infrastructure/k8s/api/service.yaml
kubectl apply -f infrastructure/k8s/api/hpa.yaml

### 5. Deploy the MCP server

```bash
kubectl apply -f infrastructure/k8s/mcp-server/configmap.yaml
kubectl apply -f infrastructure/k8s/mcp-server/deployment.yaml
kubectl apply -f infrastructure/k8s/mcp-server/service.yaml
kubectl apply -f infrastructure/k8s/mcp-server/hpa.yaml
```
```

### 6. Verify

```bash
kubectl get pods -n vat-system
kubectl logs -n vat-system deploy/vat-api
kubectl logs -n vat-system deploy/mcp-server
```

### Resource limits

All pods have explicit resource limits (256Mi–512Mi memory, 250m–500m CPU). Adjust in the respective YAML files for production sizing.

### Health probes

| Probe | Path | Notes |
|---|---|---|
| Liveness | `/actuator/health/liveness` | Restarts pod if JVM hangs |
| Readiness | `/actuator/health/readiness` | Removes pod from LB during startup/warmup |

---

## Adding a new service to Kubernetes

1. Create `infrastructure/k8s/<service-name>/` directory
2. Add `deployment.yaml`, `service.yaml`, `configmap.yaml`, `hpa.yaml`
3. Follow the resource limits and probe conventions above
4. Reference secrets from `vat-secrets` (never inline passwords)
5. Validate with `kubectl apply --dry-run=client -f infrastructure/k8s/<service-name>/`

---

## CI/CD (GitHub Actions)

The pipeline at `.github/workflows/ci.yml` runs on every push and pull request to `main`:

1. **test job** — spins up PostgreSQL as a service container, builds with Gradle, runs all tests
2. **build-image job** — builds the `api` Docker image (on `main` pushes only, after tests pass)

Test results are published as GitHub Actions annotations via `dorny/tests-reporter`.
