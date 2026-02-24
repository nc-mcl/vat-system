\# Role Context Policy — VAT System



This document defines mandatory constraints that ALL agents must follow without exception. Every agent must read this file before starting work.



---



\## 1. Open Source Only — No Licensed Dependencies



\*\*All dependencies must be open source with permissive licenses.\*\*



Permitted licenses:

\- Apache 2.0 ✅

\- MIT ✅

\- BSD 2/3-Clause ✅

\- Eclipse Public License (EPL) ✅

\- GNU LGPL ✅ (with care — avoid LGPL in distributed artifacts)



Forbidden licenses:

\- Commercial / proprietary ❌

\- GPL (copyleft — contaminates the codebase) ❌

\- AGPL ❌

\- Any license requiring royalty payments ❌

\- Any license requiring source disclosure of proprietary code ❌



\*\*Before adding any dependency, verify its license.\*\* For Java/Gradle dependencies check https://mvnrepository.com. For npm packages check https://npmjs.com or run `license-checker`.



Approved core dependencies (pre-verified):

| Dependency | License | Purpose |

|---|---|---|

| Spring Boot 3.3 | Apache 2.0 | Application framework |

| JOOQ (open source edition) | Apache 2.0 | Type-safe SQL |

| Flyway Community | Apache 2.0 | Database migrations |

| PostgreSQL JDBC | BSD | Database driver |

| JUnit 5 | EPL 2.0 | Testing |

| Mockito | MIT | Mocking |

| Testcontainers | MIT | Integration testing |

| Jackson | Apache 2.0 | JSON |

| Micrometer | Apache 2.0 | Metrics |

| OpenTelemetry | Apache 2.0 | Tracing |

| Node.js MCP SDK | MIT | MCP server |



\*\*Note on JOOQ:\*\* Use the open source edition only (Apache 2.0). The commercial edition requires a paid license — do not use it.



---



\## 2. Containers and Kubernetes — Mandatory



\*\*Every runnable component must be containerized and Kubernetes-ready.\*\*



\### Docker Requirements

\- Every service must have a `Dockerfile` using a minimal base image

\- Use official OpenJDK images: `eclipse-temurin:21-jre-alpine` for Java services

\- Use `node:20-alpine` for the MCP server

\- Multi-stage builds are mandatory — never ship build tools in production images

\- Images must run as non-root user

\- No secrets in Dockerfiles or images — use environment variables or Kubernetes secrets



\### Kubernetes Requirements

Every service must have Kubernetes manifests in `/infrastructure/k8s/<service-name>/`:

\- `deployment.yaml` — with resource limits, liveness and readiness probes

\- `service.yaml` — ClusterIP by default

\- `configmap.yaml` — for non-sensitive configuration

\- `hpa.yaml` — Horizontal Pod Autoscaler



Cluster-level resources in `/infrastructure/k8s/cluster/`:

\- `namespace.yaml` — `vat-system` namespace

\- `ingress.yaml` — ingress controller configuration

\- `secrets-template.yaml` — template showing required secrets (never actual values)



\### Resource Limits (mandatory on all pods)

```yaml

resources:

&nbsp; requests:

&nbsp;   memory: "256Mi"

&nbsp;   cpu: "250m"

&nbsp; limits:

&nbsp;   memory: "512Mi"

&nbsp;   cpu: "500m"

```

Adjust per service but always set explicit limits — never leave them unbounded.



\### Health Checks (mandatory on all services)

Every Spring Boot service must expose:

\- `/actuator/health/liveness` — liveness probe

\- `/actuator/health/readiness` — readiness probe

\- `/actuator/metrics` — Prometheus metrics scraping



\### Local Development

Provide a `docker-compose.yml` in the project root for local development:

\- PostgreSQL

\- The API service

\- The MCP server

\- Adminer (PostgreSQL UI) for development convenience



---



\## 3. Agent Handoff Protocol



\*\*Every agent must leave the project in a state where the next agent can proceed without asking questions.\*\*



Before finishing, every agent must:



\### 3.1 Update CLAUDE.md

Add a section at the bottom:

```markdown

\## Last Agent Session

\- Agent: \[agent name]

\- Date: \[date]

\- Completed: \[list of what was done]

\- Next agent can proceed: \[yes/no]

\- Blockers for next agent: \[list or "none"]

```



\### 3.2 Update the Relevant README

Every package or infrastructure component touched must have an up-to-date README.md covering:

\- What this component does

\- How to build it

\- How to run it locally

\- How to run its tests

\- Environment variables it requires

\- How it fits into the overall system



\### 3.3 Verify Successor Readiness

Before finishing, explicitly check:

\- \[ ] All files the next agent needs exist and are complete

\- \[ ] All interfaces the next agent implements are defined

\- \[ ] All environment variables are documented in the relevant README

\- \[ ] Docker and Kubernetes manifests exist if this component is runnable

\- \[ ] Tests pass



\### 3.4 Produce a Handoff Summary

At the end of every session, print a handoff summary in this format:

```

\## Handoff Summary

\### What I completed

\- \[list]



\### What the next agent needs to know

\- \[list]



\### Files created or modified

\- \[list with brief description of each]



\### Blockers or open questions

\- \[list or "none"]



\### Recommended next agent

\- \[agent name and why]

```



---



\## 4. README Standards



Every directory that contains runnable or deployable code must have a README.md. Minimum content:



```markdown

\# \[Component Name]



\## What this is

\[One paragraph description]



\## Prerequisites

\[List of what must be installed/running]



\## How to build

\[Commands]



\## How to run locally

\[Commands including required environment variables]



\## How to run tests

\[Commands]



\## Environment variables

| Variable | Required | Default | Description |

|---|---|---|---|



\## Docker

\[How to build and run the Docker image]



\## Kubernetes

\[How to deploy to Kubernetes]

```



---



\## 5. General Constraints (All Agents)



\- \*\*No hardcoded secrets\*\* — use environment variables, document them in README

\- \*\*No hardcoded Danish-specific values in core\*\* — use the jurisdiction plugin

\- \*\*No money as float or double\*\* — `long` øre in Java, `bigint` øre in TypeScript

\- \*\*No unlicensed fonts, icons, or assets\*\* in any frontend work

\- \*\*All log output\*\* must be structured JSON (use Logback with logstash-logback-encoder)

\- \*\*All services\*\* must emit OpenTelemetry traces

