# SlotForge

[![CI](https://github.com/Limrudolf/SlotForge/actions/workflows/ci.yml/badge.svg)](https://github.com/Limrudolf/SlotForge/actions/workflows/ci.yml)

SlotForge is an API-first event-booking backend designed to explore correctness under high-concurrency demand. The project focuses on scarce-capacity reservations, idempotency, asynchronous workflows, observability, and production-style deployment practices.

> Current status: Sprint 0 establishes the project foundation and reproducible local environment. Booking APIs and domain behavior will be introduced incrementally in later sprints.

## Architecture

SlotForge uses a modular monolith with a separate worker process:

- **API service:** Owns synchronous HTTP APIs and core domain operations.
- **Worker service:** Handles asynchronous workflows and side effects.
- **Shared module:** Contains stable contracts and utilities shared across process boundaries.
- **PostgreSQL:** Provides durable relational storage.
- **Redis:** Provides shared caching and rate-limiting infrastructure.
- **Prometheus:** Collects application and JVM metrics.
- **Grafana:** Queries and visualizes Prometheus metrics.

The API and worker are independently runnable and deployable. They share contracts but do not depend on each other's implementation.

See [System Architecture](docs/architecture/system-overview.md) and [Architecture Decisions](docs/adr/) for further details.

## Technology Stack

- Java 25
- Spring Boot 4.1
- Gradle 9.6.1
- PostgreSQL 17
- Redis 7.4
- Prometheus 3.13.1
- Grafana 13.0.3
- Docker and Docker Compose
- GitHub Actions

## Prerequisites

Install:

- JDK 25
- Docker Engine
- Docker Compose
- Git

A system-wide Gradle installation is not required because the repository includes the Gradle Wrapper.

Verify the main tools:

```bash
java -version
docker --version
docker compose version
```

## Quick Start

Clone the repository:

```bash
git clone https://github.com/Limrudolf/SlotForge.git
cd SlotForge
```

Create the local environment file:

```bash
cp .env.example .env
```

Build and start the complete local environment:

```bash
docker compose up -d --build
```

Check container status:

```bash
docker compose ps
```

Wait until the API, worker, PostgreSQL, and Redis health checks report healthy.

## Local Endpoints

| Component | URL |
|---|---|
| API health | http://localhost:8080/actuator/health |
| API information | http://localhost:8080/actuator/info |
| API metrics | http://localhost:8080/actuator/prometheus |
| Worker health | http://localhost:8081/actuator/health |
| Worker information | http://localhost:8081/actuator/info |
| Worker metrics | http://localhost:8081/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Prometheus targets | http://localhost:9090/targets |
| Grafana | http://localhost:3000 |

Default local Grafana credentials are documented in `.env.example`. They must not be used outside local development.

## Running Without Docker

Run the API:

```bash
./gradlew :api-service:bootRun
```

Run the worker in a second terminal:

```bash
./gradlew :worker-service:bootRun
```

The API defaults to port `8080`, and the worker defaults to port `8081`.

PostgreSQL and Redis are provisioned during Sprint 0 but are not yet consumed by application code. Database integration begins in Sprint 1.

## Building and Testing

Run the complete test suite:

```bash
./gradlew clean test
```

Build both executable Spring Boot JARs:

```bash
./gradlew :api-service:bootJar :worker-service:bootJar
```

Build and test everything:

```bash
./gradlew clean test build
```

GitHub Actions performs compilation, testing, and service packaging on pushes and pull requests targeting `main`.

## Observability

Spring Boot Actuator and Micrometer expose Prometheus-compatible metrics from both services.

Prometheus scrapes:

```text
http://api:8080/actuator/prometheus
http://worker:8081/actuator/prometheus
```

These addresses use Docker Compose service discovery and are reachable inside the Compose network.

Grafana automatically provisions Prometheus as its default datasource. In Grafana Explore, verify application availability with:

```promql
up{job=~"slotforge-.*"}
```

## Repository Structure

```text
SlotForge/
├── backend/
│   ├── api-service/
│   ├── worker-service/
│   └── shared/
├── monitoring/
│   ├── prometheus/
│   └── grafana/
├── docs/
│   ├── architecture/
│   └── adr/
├── .github/
│   └── workflows/
├── docker-compose.yml
├── settings.gradle
├── build.gradle
└── README.md
```

## Stopping the Environment

Stop and remove containers while preserving local data:

```bash
docker compose down
```

To also delete PostgreSQL, Redis, Prometheus, and Grafana volumes:

```bash
docker compose down -v
```

The second command permanently removes locally stored development data.

## Roadmap

Planned capabilities include:

- Event, venue, and session APIs
- JWT authentication and role-based authorization
- Concurrency-safe booking with PostgreSQL row locks
- Idempotent booking requests
- Fake payment workflows
- Transactional outbox and AWS SQS
- Waitlist promotion
- Redis caching and distributed rate limiting
- Structured logging and operational dashboards
- Multi-replica load testing
- Terraform and AWS ECS Fargate deployment

Detailed sprint planning is available in `slotforge_project_plan_revised.md`.