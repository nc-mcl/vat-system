# DevOps Agent — Operating Contract

> **Status: Complete** — This agent has run. See `docs/agent-sessions/session-log.md`
> for the completed session details.

## Role
You are the DevOps Agent for a multi-jurisdiction VAT system. Your responsibility covers the full DevOps lifecycle: local development environment, CI/CD pipelines, container builds, Kubernetes manifests, and observability. You do not write business logic or domain code.

## Mandatory First Step
Read `ROLE_CONTEXT_POLICY.md` and `CLAUDE.md` before starting any work.

## Definition of Done
Every item below must work before you are finished:
- [ ] Dev container starts with zero manual steps
- [ ] `java --version` shows Java 21 inside container
- [ ] `node --version` shows Node.js 20 inside container
- [ ] `git --version` shows git inside container
- [ ] `claude --version` shows Claude Code inside container
- [ ] `./gradlew :tax-engine:test --rerun-tasks` shows 68 tests passing inside container
- [ ] PostgreSQL reachable at `postgres:5432` inside container
- [ ] Adminer reachable at `http://localhost:8090` from host
- [ ] VS Code git integration works (no "git not found" warnings)
- [ ] GitHub Actions workflow runs on every push to main
- [ ] Docker image builds successfully for api module
- [ ] Kubernetes manifests are valid (pass `kubectl --dry-run`)
- [ ] Structured JSON logging configured

## Technology Constraints (from ROLE_CONTEXT_POLICY.md)
- All base images must be open source: `eclipse-temurin:21-jre-alpine`, `postgres:16-alpine`, `node:20-alpine`
- All containers run as non-root user
- All pods have explicit resource limits
- All images use multi-stage builds
- No secrets in Dockerfiles, docker-compose, or k8s manifests — use environment variables and k8s secrets

---

## Phase 1 — Fix Dev Container

### Task 1 — Fix .gitignore
Ensure `.gitignore` contains:
```
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!gradle/wrapper/gradle-wrapper.properties
**/build/

# IDE
.vscode/
.idea/
*.iml

# OS
.DS_Store
Thumbs.db
```

Remove already-tracked build files:
```bash
git rm -r --cached .gradle/ 2>/dev/null || true
git rm -r --cached core-domain/build/ tax-engine/build/ api/build/ \
  persistence/build/ skat-client/build/ 2>/dev/null || true
```

### Task 2 — Fix .gitattributes
Create `.gitattributes`:
```gitattributes
gradlew text eol=lf
*.sh text eol=lf
gradlew.bat text eol=crlf
*.bat text eol=crlf
* text=auto
```

### Task 3 — Fix gradlew
Fix DEFAULT_JVM_OPTS quoting:
```bash
sed -i "s/DEFAULT_JVM_OPTS='\".*\"'/DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m\"/" gradlew
```

Verify:
```bash
grep DEFAULT_JVM_OPTS gradlew
```
Must show: `DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"` with no nested quotes.

### Task 4 — Write .devcontainer/Dockerfile
```dockerfile
FROM eclipse-temurin:21-jdk-jammy

# Install base tools
RUN apt-get update -qq && \
    apt-get install -y -qq \
      git \
      curl \
      unzip \
      nano \
      ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 20 via official binary (reliable, no scripts)
RUN curl -fsSL https://nodejs.org/dist/v20.11.0/node-v20.11.0-linux-x64.tar.gz \
    | tar -xz -C /usr/local --strip-components=1

# Verify Node.js installed correctly
RUN node --version && npm --version

# Install Claude Code globally
RUN npm install -g @anthropic-ai/claude-code

# Verify Claude Code
RUN claude --version || echo "Claude Code installed"

WORKDIR /workspace
```

### Task 5 — Write .devcontainer/docker-compose.yml
```yaml
services:

  dev:
    build:
      context: .
      dockerfile: Dockerfile
    volumes:
      - ..:/workspace:cached
      - gradle-cache:/root/.gradle
    working_dir: /workspace
    command: sleep infinity
    environment:
      - GRADLE_USER_HOME=/root/.gradle
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=vatdb
      - DB_USER=vat
      - DB_PASSWORD=vat_dev_password
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - vat-network

  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: vatdb
      POSTGRES_USER: vat
      POSTGRES_PASSWORD: vat_dev_password
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U vat -d vatdb"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - vat-network

  adminer:
    image: adminer:latest
    restart: unless-stopped
    ports:
      - "8090:8080"
    depends_on:
      - postgres
    networks:
      - vat-network

volumes:
  postgres-data:
  gradle-cache:

networks:
  vat-network:
    driver: bridge
```

### Task 6 — Write .devcontainer/devcontainer.json
```json
{
  "name": "VAT System Dev Environment",
  "dockerComposeFile": "docker-compose.yml",
  "service": "dev",
  "workspaceFolder": "/workspace",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "vscjava.vscode-gradle",
        "redhat.java",
        "vscjava.vscode-java-test",
        "bierner.markdown-mermaid",
        "eamodio.gitlens"
      ],
      "settings": {
        "java.configuration.updateBuildConfiguration": "automatic",
        "editor.formatOnSave": true,
        "editor.tabSize": 4,
        "git.path": "/usr/bin/git",
        "git.enabled": true,
        "terminal.integrated.defaultProfile.linux": "bash"
      }
    }
  },
  "forwardPorts": [8080, 5432, 8090],
  "portsAttributes": {
    "8080": { "label": "VAT API" },
    "5432": { "label": "PostgreSQL" },
    "8090": { "label": "Adminer" }
  },
  "postStartCommand": "chmod +x /workspace/gradlew",
  "remoteUser": "root"
}
```

### Task 7 — Test Docker Build
Run the Docker build directly to verify it works:
```bash
docker build -t vat-system-dev-test .devcontainer/
```

Must complete with no errors. If it fails, fix the Dockerfile and retry until it passes.

Clean up test image:
```bash
docker rmi vat-system-dev-test 2>/dev/null || true
```

---

## Phase 2 — Docker Image Builds

### Task 8 — API Dockerfile
Create `api/Dockerfile` (multi-stage, non-root, Alpine runtime):
```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY . .
RUN ./gradlew :api:bootJar --no-daemon -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create non-root user
RUN addgroup -S vatgroup && adduser -S vatuser -G vatgroup

WORKDIR /app
COPY --from=builder /build/api/build/libs/*.jar app.jar

USER vatuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.threads.virtual.enabled=true", \
  "-jar", "app.jar"]
```

### Task 9 — Root docker-compose.yml for Local Development
Create `docker-compose.yml` in the project root (separate from dev container):
```yaml
services:

  api:
    build:
      context: .
      dockerfile: api/Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/vatdb
      - SPRING_DATASOURCE_USERNAME=vat
      - SPRING_DATASOURCE_PASSWORD=vat_dev_password
      - SPRING_PROFILES_ACTIVE=local
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - vat-network

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: vatdb
      POSTGRES_USER: vat
      POSTGRES_PASSWORD: vat_dev_password
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infrastructure/db/migrations:/docker-entrypoint-initdb.d:ro
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U vat -d vatdb"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - vat-network

  adminer:
    image: adminer:latest
    ports:
      - "8090:8080"
    depends_on:
      - postgres
    networks:
      - vat-network

volumes:
  postgres-data:

networks:
  vat-network:
    driver: bridge
```

---

## Phase 3 — GitHub Actions CI/CD

### Task 10 — CI Pipeline
Create `.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    name: Build and Test
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: vatdb
          POSTGRES_USER: vat
          POSTGRES_PASSWORD: vat_test_password
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 5s
          --health-timeout 5s
          --health-retries 10

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Fix gradlew permissions
        run: chmod +x gradlew

      - name: Build and test
        run: ./gradlew build --no-daemon
        env:
          DB_HOST: localhost
          DB_PORT: 5432
          DB_NAME: vatdb
          DB_USER: vat
          DB_PASSWORD: vat_test_password

      - name: Publish test results
        uses: dorny/tests-reporter@v1
        if: always()
        with:
          name: JUnit Tests
          path: '**/build/test-results/test/*.xml'
          reporter: java-junit

  build-image:
    name: Build Docker Image
    runs-on: ubuntu-latest
    needs: test
    if: github.ref == 'refs/heads/main'

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Fix gradlew permissions
        run: chmod +x gradlew

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: api/Dockerfile
          push: false
          tags: vat-system/api:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

---

## Phase 4 — Kubernetes Manifests

### Task 11 — Namespace
Create `infrastructure/k8s/cluster/namespace.yaml`:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: vat-system
  labels:
    app.kubernetes.io/managed-by: kubectl
```

### Task 12 — Secrets Template
Create `infrastructure/k8s/cluster/secrets-template.yaml`:
```yaml
# TEMPLATE ONLY — never commit actual values
# Create actual secret with:
# kubectl create secret generic vat-secrets \
#   --from-literal=db-password=<actual-password> \
#   --from-literal=skat-api-key=<actual-key> \
#   -n vat-system
apiVersion: v1
kind: Secret
metadata:
  name: vat-secrets
  namespace: vat-system
type: Opaque
stringData:
  db-password: "REPLACE_ME"
  skat-api-key: "REPLACE_ME"
```

### Task 13 — API Deployment
Create `infrastructure/k8s/api/deployment.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vat-api
  namespace: vat-system
  labels:
    app: vat-api
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vat-api
  template:
    metadata:
      labels:
        app: vat-api
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: vat-api
          image: vat-system/api:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: vat-secrets
                  key: db-password
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                configMapKeyRef:
                  name: vat-config
                  key: db-url
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

Create `infrastructure/k8s/api/service.yaml`:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: vat-api
  namespace: vat-system
spec:
  selector:
    app: vat-api
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
```

Create `infrastructure/k8s/api/configmap.yaml`:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: vat-config
  namespace: vat-system
data:
  db-url: "jdbc:postgresql://postgres:5432/vatdb"
  db-username: "vat"
  spring-profile: "production"
```

Create `infrastructure/k8s/api/hpa.yaml`:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: vat-api-hpa
  namespace: vat-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: vat-api
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### Task 14 — PostgreSQL StatefulSet
Create `infrastructure/k8s/postgres/statefulset.yaml`:
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: vat-system
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          env:
            - name: POSTGRES_DB
              value: vatdb
            - name: POSTGRES_USER
              value: vat
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: vat-secrets
                  key: db-password
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "vat", "-d", "vatdb"]
            initialDelaySeconds: 30
            periodSeconds: 10
          volumeMounts:
            - name: postgres-data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: postgres-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
```

Create `infrastructure/k8s/postgres/service.yaml`:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: vat-system
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
  type: ClusterIP
  clusterIP: None
```

---

## Phase 5 — Observability

### Task 15 — Structured Logging
Create `api/src/main/resources/logback-spring.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <springProfile name="!local">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>jurisdictionCode</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON"/>
    </root>
  </springProfile>

  <springProfile name="local">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>
</configuration>
```

### Task 16 — README for DevOps
Create `infrastructure/README.md` covering:
- How to start the dev container
- How to run CI locally with `act`
- How to deploy to Kubernetes
- How to access Adminer
- Environment variables reference
- How to add a new service to the k8s setup

---

## Output Checklist
- [ ] `.gitignore` updated, build folders removed from tracking
- [ ] `.gitattributes` created
- [ ] `gradlew` DEFAULT_JVM_OPTS fixed
- [ ] `.devcontainer/Dockerfile` — Java 21 + Node.js 20 + Claude Code
- [ ] `.devcontainer/docker-compose.yml` — uses `build:` for dev service
- [ ] `.devcontainer/devcontainer.json` — git.path set
- [ ] Docker build test passed (`docker build -t vat-system-dev-test .devcontainer/`)
- [ ] `api/Dockerfile` — multi-stage, non-root, Alpine runtime
- [ ] `docker-compose.yml` in project root
- [ ] `.github/workflows/ci.yml` — build, test, Docker image
- [ ] `infrastructure/k8s/cluster/namespace.yaml`
- [ ] `infrastructure/k8s/cluster/secrets-template.yaml`
- [ ] `infrastructure/k8s/api/deployment.yaml`
- [ ] `infrastructure/k8s/api/service.yaml`
- [ ] `infrastructure/k8s/api/configmap.yaml`
- [ ] `infrastructure/k8s/api/hpa.yaml`
- [ ] `infrastructure/k8s/postgres/statefulset.yaml`
- [ ] `infrastructure/k8s/postgres/service.yaml`
- [ ] `api/src/main/resources/logback-spring.xml`
- [ ] `infrastructure/README.md`
- [ ] All files committed and pushed
- [ ] Handoff Summary printed

## Handoff Protocol
Before finishing:
- Update `CLAUDE.md` Last Agent Session.
- Update the root `README.md` status table (Dev Container row).
- Append to `docs/agent-sessions/session-log.md`.
- Print a structured Handoff Summary.

## Constraints
- No secrets or passwords in any committed file — templates only
- All containers run as non-root in production Dockerfiles
- All base images must be open source — verify licenses
- Do not modify Java source files
- Do not modify agent operating contracts
- Test the Docker build before finishing
- Follow handoff protocol from ROLE_CONTEXT_POLICY.md
