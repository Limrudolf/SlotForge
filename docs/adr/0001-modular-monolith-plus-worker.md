# ADR 0001: Modular Monolith Plus Worker

- **Status:** Accepted
- **Date:** 2026-07-29

## Context

SlotForge must demonstrate production-style backend engineering, including transactional booking behavior, asynchronous workflows, retries, idempotent message handling, and independent worker scaling.

A single Spring Boot process would be operationally simple, but background processing inside the API process would couple HTTP traffic and asynchronous workloads. A large collection of microservices would introduce network, deployment, testing, and data-consistency complexity before the domain justifies it.

The architecture needs a meaningful distributed-systems boundary without unnecessary service sprawl.

## Decision

SlotForge will use a modular monolith for synchronous domain behavior and a separate Spring Boot worker service for asynchronous processing.

The API service will contain internal modules for authentication, events, availability, bookings, fake payments, waitlists, auditing, and the transactional outbox.

The worker service will consume asynchronous events and handle booking expiry, notifications, waitlist promotion, payment events, retries, and dead-letter queue behavior.

Both applications may depend on a deliberately narrow shared module containing stable event contracts and common utilities. Neither application may depend on the other application's implementation.

## Alternatives Considered

### Single Spring Boot process

The API and background jobs could run in the same process.

This would reduce deployment complexity, but HTTP workloads and background jobs would compete for the same resources. A failure or scaling decision would affect both responsibilities, and the project would not exercise a genuine asynchronous process boundary.

### Microservice per domain

Authentication, events, bookings, payments, and waitlists could each be separate services.

This would allow independent deployments but would add significant operational and consistency complexity. The domain is not yet large enough to justify multiple databases, network contracts, distributed tracing requirements, and cross-service transaction design.

### Modular monolith plus worker

This approach keeps transactional domain behavior cohesive while separating workloads that naturally operate asynchronously. It creates one meaningful distributed boundary without multiplying services unnecessarily.

## Consequences

### Positive

- Domain transactions remain local to the API service and PostgreSQL.
- Internal module boundaries can be enforced without network overhead.
- Asynchronous workloads can scale and fail independently from HTTP traffic.
- Worker retry and idempotency behavior can be tested realistically.
- Local development remains manageable with Docker Compose.
- The architecture can evolve later if a module develops a genuine need for independent deployment.

### Negative

- API and worker deployments must maintain compatible event contracts.
- The shared module can create coupling if its scope is not controlled.
- Running two applications increases local and deployment complexity.
- Queue delivery introduces eventual consistency and duplicate-processing concerns.
- Observability must correlate work across process boundaries.

## Guardrails

- The shared module must not become a general-purpose dumping ground.
- Outbox events must include explicit schema versions.
- Worker handlers must be idempotent.
- Unknown event versions must fail visibly and safely.
- The API must not wait synchronously for worker side effects.
- Service extraction requires demonstrated operational or scaling value, not architectural fashion.