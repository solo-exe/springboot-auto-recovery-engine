# 🛡️ Auto Recovery Engine (ARE)

[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Framework](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Compose%20v2-blue.svg)](https://www.docker.com/)
[![Observability](https://img.shields.io/badge/Observability-Prometheus%20%2F%20Loki%20%2F%20Grafana-orange.svg)](https://grafana.com/)
[![Messaging](https://img.shields.io/badge/RabbitMQ-4.x-red.svg)](https://www.rabbitmq.com/)

An event-driven, self-healing microservice ecosystem simulating a resilient financial transaction platform. The **Auto Recovery Engine (ARE)** provides built-in fault-injection capabilities to test and observe real-time automated recovery actions. It integrates a full observability stack (Prometheus, Loki, Alertmanager, and Grafana) with a closed-loop remediation handler that detects service anomalies and triggers automated healing runs.

---

## 🗺️ Architectural Topology

The system implements an autonomic closed-loop feedback design (MAPE-K loop) structured across three logical layers:

-   **Application Layer**: Houses the core microservices cluster (API Gateway, Account Service, Payment Service, and Notification Service) which handles transaction requests, internal communication, and notification triggers.
-   **Observability Layer**: Aggregates live system telemetry (health checks, performance metrics, and application logs) scraped and processed by the monitoring tier.
-   **Recovery Layer**: The **Auto-Recovery Engine** (ARE) which consumes telemetry alerts, evaluates them against the decision matrix, and dispatches automated remediation actions (such as service restarts) back to the application layer.

<p align="center">
  <img src="are_arch.png" alt="Auto-Recovery Engine (ARE) Architecture" width="700">
</p>

---

## 📂 Project Structure

```
auto-recovery-engine-pgd/
├── pom.xml                          # Parent POM (Dependency management)
├── Makefile                         # Developer workflow shorthand
├── docker-compose.yml               # Postgres, RabbitMQ, Observability, and Microservices
├── auto_recovery_engine.dbml        # Conceptual DB schema definition
├── config/                          # Configuration files for infrastructure
│   ├── prometheus.yml               # Scrape configurations
│   ├── prometheus/alert-rules.yml   # Threshold rules (ServiceDown, HighErrorRate, etc.)
│   ├── loki/loki-config.yml         # Log storage config
│   ├── promtail/promtail-config.yml # Docker log shipping config
│   ├── grafana/                     # Provisioned datasources and dashboards
│   └── alertmanager/                # Alerts routing to the Recovery Engine webhook
│
├── services/                        # Java Microservices
│   ├── common-core/                 # Shared JPA entities, DTOs, and Liquibase migrations
│   ├── api-gateway/                 # Gateway router (Spring Cloud Gateway, JWT filter, CB)
│   ├── account-service/             # User and ledger account management
│   ├── payment-service/             # Payment processing (WebClient, R4J Circuit Breaker)
│   ├── notification-worker/         # Event consumer (RabbitMQ to Mock Mail Sender)
│   ├── spring-boot-admin/           # Spring Boot Admin (Service registration and runtime details)
│   └── recovery-engine/             # Alert receiver, cooling rules, and execution handler
│
└── scripts/                         # Testing & experiment orchestration
    ├── launch_engine.sh             # Debug launch entrypoint for infra / observability / services
    ├── run.sh                       # Background launcher wrapper used by the project workflow
    ├── test-observability.sh        # Seeds auth flow, generates metrics, tests restart behavior
    ├── simulate_crash.sh            # Crash recovery latency and MTTR benchmark
    ├── simulate_error_rate.sh       # High error-rate detection and circuit-breaker benchmark
    ├── simulate_latency.sh          # High latency detection and circuit-breaker benchmark
    └── simulate_memory_leak.sh      # Memory-leak detection and restart benchmark
```

---

## 🗄️ Database Design & Schema

The ecosystem uses a **Shared Database Pattern** for simplicity. Services connect to PostgreSQL (`are_db`). DB migrations are managed centrally by **Liquibase** located in `common-core` to guarantee schema consistency.

Below is the relationship ERD (represented in DBML notation):

-   **`users`**: Master customer logins and profiles.
-   **`accounts`**: Financial ledger accounts (optimistic locking enabled via a `@Version` field to prevent race conditions).
-   **`transactions`**: Immutable transaction audit logs linked directly to an account.
-   **`payments`**: Decoupled transfers managed by the Payment Service, using logical account references instead of physical DB foreign keys to maintain modular boundaries.
-   **`otps`**: Temporary security codes used during onboarding registration steps.

```
┌──────────────┐          ┌──────────────┐          ┌─────────────────┐
│    users     │          │   accounts   │          │  transactions   │
├──────────────┤          ├──────────────┤          ├─────────────────┤
│ id (PK)      │◄───┐     │ id (PK)      │◄───┐     │ id (PK)         │
│ email (UQ)   │    └────  userId (FK)   │    └────  accountId (FK)  │
│ phoneNumber  │          │ accountNo(UQ)│          │ userId (FK) ────┼──┐
│ passwordHash │          │ balance      │          │ amount          │  │
│ status       │          │ status       │          │ type (cr/dr)    │  │
│ type (role)  │          │ version      │          │ correlationId   │  │
└──────────────┘          └──────────────┘          └─────────────────┘  │
       ▲                                                                 │
       │                                                                 │
       │                                                                 │
       │                  ┌──────────────┐                               │
       │                  │     otps     │                               │
       │                  ├──────────────┤                               │
       └─────────────────  userId (FK)   │                               │
                          │ otp          │                               │
                          │ expiresAt    │                               │
                          │ usedAt       │                               │
                          └──────────────┘                               │
                                                                         │
                          ┌──────────────┐                               │
                          │   payments   │                               │
                          ├──────────────┤                               │
                          │ id (PK)      │                               │
                          │ fromAccId ◄──┼───────────────────────────────┘
                          │ toAccId      │  (Cross-Service Logical Ref,
                          │ amount       │   No Database FK constraints)
                          │ status       │
                          │ failureReason│
                          └──────────────┘
```

---

## 🛠️ Microservice Breakdown

| Service                 | Port   | Primary Responsibilities                                      | Core Stack Components                           |
| ----------------------- | ------ | ------------------------------------------------------------- | ----------------------------------------------- |
| **API Gateway**         | `8080` | JWT validation, client routing, correlation ID injection      | WebFlux, Spring Cloud Gateway, JJWT             |
| **Account Service**     | `8082` | Signups, OTPs, balance ledgers, transaction histories         | WebMVC, JPA, HikariCP, PostgreSQL, RabbitMQ     |
| **Payment Service**     | `8081` | Safe debits, credits, refunds, and inter-service client logic | WebMVC, WebClient, Resilience4j Circuit Breaker |
| **Notification Worker** | `8085` | Consuming MQ notification jobs, rendering HTML emails         | RabbitMQ AMQP, JavaMailSender, Thymeleaf        |
| **Spring Boot Admin**   | `8086` | Health registration, dynamic metrics UI, thread states        | Spring Boot Admin Server                        |
| **Recovery Engine**     | `8087` | Alert webhook receiver, cooldown manager, action executor     | Spring Boot, Webhook Parser, Audit Logging      |

---

## ⚡ Fault Injection System

Each business service mounts a `FaultSimulationController` that is intercepted by a custom `FaultInterceptor` filter. This allows developers to test recovery scenarios by sending REST calls to the gateway.

### Fault Endpoints

| Endpoint (POST)                 | JSON Payload       | Simulated Behavior                                                                        |
| ------------------------------- | ------------------ | ----------------------------------------------------------------------------------------- |
| `/fault/{service}/unresponsive` | `{"enable": true}` | Intercepts business requests and sleeps for 60s (induces Gateway/Client timeouts).        |
| `/fault/{service}/error-rate`   | `{"rate": 50}`     | Randomly drops `rate`% of inbound business requests, returning an HTTP 500 error wrapper. |
| `/fault/{service}/memory-leak`  | `{"enable": true}` | Launches a virtual thread allocating `1MB` byte arrays every 500ms (OOM testing).         |
| `/fault/{service}/cpu-spike`    | `{"enable": true}` | Spawns background virtual threads running busy loops on all available cores.              |
| `/fault/{service}/crash`        | _None_             | Invokes a JVM shutdown (`System.exit(1)`).                                                |

> [!NOTE]
> Paths matching `/fault/**`, `/actuator/**`, `/internal/**`, `/swagger-ui/**`, and `/v3/api-docs/**` bypass the fault interceptor so that infrastructure control planes remain operational during outages.

---

## 🧠 The Self-Healing Matrix

The **Recovery Engine** parses Alertmanager webhook payloads, checks the target service status, respects a configurable cooldown timer, and runs actions mapped in its active matrix rules:

```yaml
# Matrix Rule Mapping inside recovery-engine application.yml
recovery:
    matrix:
        - fault: Service Crash
          signal: HTTP 5xx rate spike (ServiceDown alert)
          primary-action: RESTART
          threshold: 1
          window: 30s
        - fault: High Error Rate
          signal: HTTP 5xx rate > 10% (HighErrorRate alert)
          primary-action: CIRCUIT_BREAKER_OPEN
          secondary-action: REROUTE_TRAFFIC
          threshold: 0.1
          window: 120s
        - fault: Response Time Degradation
          signal: p95 latency > 2s (HighLatency alert)
          primary-action: CIRCUIT_BREAKER_HALF_OPEN
          threshold: 2000
          window: 180s
        - fault: Connection Pool Exhaustion
          signal: HikariCP pending threads (DbConnectionTimeout alert)
          primary-action: POOL_RESET
          threshold: 5
          window: 60s
        - fault: Cascading Failure
          signal: Multi-service alarms firing
          primary-action: CASCADING_RECOVERY
          threshold: 2
          window: 60s
```

---

## 🚀 Quick Start Guide

### Prerequisites

-   Docker & Docker Compose (v2+)
-   Java Development Kit (JDK 21+)
-   Maven 3.8+ (or use the provided `./mvnw` wrapper)

### Step 1: Clone the Repo and Build Binary Jars

Compile all microservices and package them into target jars:

```bash
make build
```

### Step 2: Start the System

The current preferred launch path is the debug launcher that starts infrastructure, observability, and Java services in a controlled sequential order with full log visibility:

```bash
make debug-launch
# or directly:
./scripts/launch_engine.sh
```

The launcher supports tiered startup and targeted service control:

```bash
./scripts/launch_engine.sh infra
./scripts/launch_engine.sh obs
./scripts/launch_engine.sh admin
./scripts/launch_engine.sh account
./scripts/launch_engine.sh payment
./scripts/launch_engine.sh notif
./scripts/launch_engine.sh gateway
./scripts/launch_engine.sh recovery
./scripts/launch_engine.sh status
./scripts/launch_engine.sh stop
```

If you prefer to start individual components manually:

-   Start infrastructure only: `make infra-compose-up`
-   Start the gateway locally: `make start-gateway`
-   Start the account service locally: `make start-account`
-   Start the payment service locally: `make start-payment`
-   Start the notification worker: `make start-notif`
-   Start the recovery engine: `make start-recovery`

---

## 🧪 Simulation, Load & Experiment Scripts

The repository contains scripts under `scripts/` to automate load testing and measure self-healing recovery times.

### 1. Auto-Seeding & Observability Verification (`scripts/test-observability.sh`)

This script checks infrastructure health, hooks into the API Gateway auth flow, and:

1.  **Registers a User** via `POST /api/accounts/auth/signup`
2.  **Verifies OTP** using the development master code `123456`
3.  **Sets a password** using `POST /api/accounts/auth/create-password` to activate the user account
4.  **Generates HTTP 4xx/5xx logs** to populate Loki and Prometheus
5.  **Triggers a service failure path** to verify that the recovery loop is engaged.

### 2. Controlled Fault Simulation Benchmarks

The project now ships dedicated shell-based performance experiments for the live recovery workflow. Each script measures detection latency, execution latency, and end-to-end MTTR, then writes JSON outputs into `logs/`.

-   `scripts/simulate_crash.sh` — service kill / restart benchmark
-   `scripts/simulate_error_rate.sh` — high error-rate detection and circuit-breaker benchmark
-   `scripts/simulate_latency.sh` — response-time degradation benchmark
-   `scripts/simulate_memory_leak.sh` — memory-pressure detection and restart benchmark

Typical usage:

```bash
./scripts/simulate_crash.sh
./scripts/simulate_error_rate.sh
./scripts/simulate_latency.sh
./scripts/simulate_memory_leak.sh
```

The resulting artifacts live in:

-   `logs/crash_scenario_results.json`
-   `logs/error_rate_results.json`
-   `logs/latency_results.json`
-   `logs/memory_leak_results.json`

---

## 📊 Observability Port Map & UIs

During runtime, you can inspect the health and telemetry details of all microservices:

-   **API Gateway entrypoint**: [http://localhost:8080](http://localhost:8080)
-   **Spring Boot Admin console**: [http://localhost:8086](http://localhost:8086) (view service nodes and trigger thread dumps)
-   **Prometheus metrics console**: [http://localhost:9090](http://localhost:9090)
-   **Grafana dashboards**: [http://localhost:3000](http://localhost:3000) (Default login: `admin / admin`)
    -   _Path: Dashboards -> ARE - Microservices Overview_
-   **Alertmanager console**: [http://localhost:9093](http://localhost:9093)
-   **RabbitMQ administration UI**: [http://localhost:15672](http://localhost:15672) (Default login: `guest / guest`)

---

## ⚠️ Known Specification Gaps & Architectural Insights (So Far)

Below are known implementation details and deviations from the initial specification for reference when contributing:

1.  **Shared Database**: Services share a PostgreSQL instance (`are_db`) and database schemas. In production, this would be isolated, but it is kept shared here to simplify local deployments.
2.  **Entity ID Formats**: The core specification requested UUIDs for primary keys. The database schema currently implements `bigint` (auto-increment) keys for simplicity.
3.  **BaseEntity Deletions**: The soft-delete timestamp column `deleted_at` is marked `NOT NULL` in the base DDL but initialized as null, which may require soft deletes to handle default values explicitly.
4.  **Notification MDC Tracing**: The `notification-worker` consumes events from RabbitMQ queues, but does not extract the `X-Correlation-ID` header into its thread-local logging MDC.
5.  **Gateway Auth Mocking**: Authentication tokens are issued directly by the Gateway (`api-gateway`) rather than the Account Service to simplify token routing.
