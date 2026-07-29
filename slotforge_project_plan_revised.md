# SlotForge Project Plan

## 1. Stack

### Project Name

**SlotForge**

### Project One-Liner

An API-first scarce-resource event booking backend that safely handles high-concurrency reservations, fake payments, waitlists, async workflows, observability, load testing, and cloud deployment.

### Primary Goal

Build a flagship backend engineering portfolio project that demonstrates production-style backend ownership: API design, security, concurrency control, database modeling, async processing, caching, observability, load testing, infrastructure as code, and CI/CD deployment.

### Selected Stack

| Area | Choice |
|---|---|
| Backend framework | Spring Boot |
| Architecture | Modular monolith plus worker service |
| Deployment | AWS ECS Fargate |
| Database | Supabase Postgres |
| Local database | Dockerized PostgreSQL |
| Cache | Docker Redis first, optional managed Redis later |
| Queue | AWS SQS plus DLQ |
| Auth | Spring Security |
| API style | REST plus OpenAPI |
| Frontend | None, API-first |
| Payment | Fake payment service |
| Observability | Prometheus plus Grafana |
| Infrastructure as Code | Terraform |
| CI/CD | Full CI/CD by final sprint |
| Local development | Docker Compose |
| Testing | Full backend test suite plus load testing, including multi-replica correctness validation |
| Security scanning | OWASP Dependency-Check, Trivy, and Checkov |
| Hosting strategy | Local-first development, cost-controlled Fargate deployment in final sprint |
| Product domain | High-demand event booking |
| Resume target | Backend internship plus distributed systems |

### Intended Backend Signals

This project is designed to demonstrate:

- Spring Boot production API development
- REST API contract design
- OpenAPI documentation
- Spring Security
- JWT authentication
- Role-based access control
- PostgreSQL schema design
- Flyway migrations
- Row-level locking
- Transaction boundaries
- Idempotency keys
- Booking state machines
- Fake payment workflow
- Transactional outbox
- AWS SQS
- Dead-letter queues
- Async worker processing
- Redis caching
- Redis-backed rate limiting
- Prometheus metrics
- Grafana dashboards
- Structured logging
- Correlation IDs
- Docker Compose
- Terraform
- AWS ECS Fargate
- GitHub Actions CI/CD
- Load testing with k6 or Locust
- Database indexing and query optimization
- Architecture documentation
- Multi-replica correctness testing
- Event schema versioning
- Worker graceful shutdown and SQS visibility-timeout handling
- Refresh token rotation and revocation
- CORS policy design
- Dependency, container image, and Terraform security scanning
- Correct monetary modeling with integer minor units or BigDecimal
- UTC-first timezone handling for event sessions
- Locking strategy tradeoff documentation

---

### Cost-Controlled Hosting Strategy

The project should be developed and validated locally first. Cloud hosting should be deferred until the deployment sprint unless there is a specific reason to deploy earlier.

Default approach:

- Use Docker Compose for most development.
- Use Supabase free-tier Postgres where practical.
- Use local Redis first.
- Use local or mocked SQS during early development where possible.
- Deploy to AWS ECS Fargate only in the final deployment sprint.
- Keep Fargate services stopped or scaled down when not actively testing.
- Run one short final multi-replica validation with the API service scaled to at least two ECS tasks behind the ALB.
- Document estimated monthly cost and teardown instructions.

The project should still include Terraform and Fargate deployment because it is a strong backend/platform signal, but always-on hosting is not required for the portfolio. A documented deployment, smoke test, load-test run, screenshots, and teardown guide are sufficient if cost is a concern.

---

## 2. Project Architecture

### High-Level Architecture

```text
Client / Postman / Swagger UI
        |
        v
Spring Boot API Service
        |
        |-- Auth Module
        |-- Event Module
        |-- Session / Availability Module
        |-- Booking Module
        |-- Fake Payment Module
        |-- Waitlist Module
        |-- Admin Module
        |-- Audit Module
        |-- Outbox Module
        |
        |---- Supabase Postgres
        |---- Redis
        |---- AWS SQS
                  |
                  v
        Spring Boot Worker Service
        |
        |-- Outbox Publisher
        |-- Notification Worker
        |-- Booking Expiry Worker
        |-- Waitlist Promotion Worker
        |-- Payment Event Worker
        |-- Retry / DLQ Handling
```

### Architecture Style

The system should be a **modular monolith plus worker service**.

The API service owns the core domain and synchronous user-facing APIs. The worker service processes asynchronous events from SQS and handles side effects such as notifications, booking expiry, waitlist promotion, and failed-event recovery.

This gives the project a real distributed-systems boundary without creating unnecessary microservice sprawl.

### Distributed-Systems Correctness Requirements

The project must avoid making distributed-systems claims that are not tested. In particular:

- At least one load-test run must use **2 or more API replicas** behind a load balancer.
- The final results document must state whether zero overbooking was preserved across multiple API replicas.
- Redis rate limiting must be safe across multiple API replicas because Redis is shared state.
- Cache invalidation must be validated when requests hit different API replicas.
- SQS worker processing must be idempotent because messages may be delivered more than once.
- Worker shutdown behavior must be documented for ECS deployments.

### Event Compatibility Requirements

Outbox events and SQS messages must include an explicit event version.

Every event should include at minimum:

```json
{
  "eventId": "uuid",
  "eventType": "BookingConfirmed",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T00:00:00Z",
  "correlationId": "uuid",
  "aggregateType": "Booking",
  "aggregateId": "uuid",
  "payload": {}
}
```

The project should include an ADR explaining the compatibility strategy:

- Prefer additive schema changes.
- Do not rename or remove fields without introducing a new event version.
- Workers should reject unknown event versions safely.
- Failed incompatible events should be visible through logs, metrics, and DLQ inspection.

### Worker Shutdown Requirements

The worker service must handle graceful shutdown explicitly. ECS can stop tasks during deployment or scale-in, so worker behavior should be clear:

- On shutdown, stop polling for new SQS messages.
- Complete the current in-flight message if possible.
- Delete the SQS message only after successful processing.
- If the worker is terminated before completion, rely on SQS visibility timeout to make the message available again.
- Ensure handlers are idempotent because a message may be processed again.
- Configure ECS task stop timeout and document the tradeoff.

### Correctness Modeling Requirements

The system should explicitly model common backend correctness details:

- Store event session times in UTC.
- Store the event's intended display timezone separately.
- Never use floating point values for money.
- Store payment amounts as integer minor units, such as cents, or use `BigDecimal` for calculations.
- Document pessimistic versus optimistic locking tradeoffs.
- Use pessimistic row-level locking for final scarce-capacity booking decisions.
- Use version columns where useful for update detection and to support the locking-strategy discussion.

### Core Modules

| Module | Responsibility |
|---|---|
| Auth | Registration, login, JWT validation, roles, permissions |
| Event | Event and session management |
| Availability | Session capacity and slot availability |
| Booking | Booking creation, cancellation, state transitions, idempotency |
| Fake Payment | Payment-intent simulation, success/failure callbacks |
| Waitlist | Waitlist joins, promotions, expiry, acceptance |
| Outbox | Reliable event persistence before async publishing |
| Worker | SQS consumption, retries, DLQ handling, async workflows |
| Audit | Sensitive action logging and booking history |
| Observability | Metrics, structured logs, correlation IDs |

### Core Design Principles

- Keep the product surface small.
- Make backend behavior deep.
- Prefer correctness over feature count.
- Treat booking capacity as a scarce resource.
- Make duplicate requests safe.
- Make async workers idempotent.
- Document tradeoffs explicitly.
- Measure performance with load tests.
- Keep local development reproducible with Docker Compose.
- Keep deployment reproducible with Terraform.

---

## 3. Proposed Repository Structure

```text
slotforge/
  README.md
  docker-compose.yml
  .github/
    workflows/
      ci.yml
      deploy.yml

  backend/
    api-service/
      src/
      build.gradle
      Dockerfile

    worker-service/
      src/
      build.gradle
      Dockerfile

    shared/
      src/
      build.gradle

  infra/
    terraform/
      environments/
        dev/
        prod/
      modules/
        ecs/
        ecr/
        sqs/
        iam/
        alb/
        networking/
        observability/
      main.tf
      variables.tf
      outputs.tf

  docs/
    architecture/
      system-overview.md
      database-schema.md
      booking-consistency.md
      event-driven-workflows.md
      failure-modes.md
      deployment-architecture.md
      event-schema-versioning.md
      graceful-shutdown.md
      locking-strategy.md
      security.md
      cost-strategy.md

    adr/
      0001-modular-monolith-plus-worker.md
      0002-sqs-over-kafka.md
      0003-supabase-postgres.md
      0004-terraform-over-cdk.md
      0005-event-schema-versioning.md
      0006-pessimistic-vs-optimistic-locking.md
      0007-cost-controlled-hosting.md

    api/
      openapi.md
      example-requests.md

    load-tests/
      results.md
      multi-replica-results.md
      scenarios.md

    observability/
      metrics.md
      dashboards.md

  tests/
    load/
      booking-spike.js
      availability-browse.js
      payment-callbacks.js
      waitlist-promotion.js

  scripts/
    run-local.sh
    seed-data.sh
    smoke-test.sh
```

---

## 4. Sprint Roadmap

## Sprint 0: Project Foundation and Local Environment

### Goal

Create a professional project foundation with reproducible local development, separate API and worker services, Docker Compose, basic CI, and initial documentation.

### Learning Outcomes

By the end of this sprint, you should understand:

- How to structure a serious backend portfolio repository.
- How to split an API service and worker service.
- How to use Docker Compose for local infrastructure.
- How to expose basic Spring Boot health endpoints.
- How to create an initial CI workflow.
- How to document architecture decisions from the start.

### Technical Requirements

- Create GitHub repository.
- Set up Spring Boot API service.
- Set up Spring Boot worker service.
- Set up shared module for common DTOs, event contracts, and utilities.
- Add Dockerfiles for both services.
- Add Docker Compose for:
  - API service
  - Worker service
  - PostgreSQL
  - Redis
  - Prometheus
  - Grafana
- Add Spring Boot Actuator.
- Add basic GitHub Actions CI:
  - compile
  - run tests
  - build services
- Create initial README.
- Create initial architecture diagram.
- Create ADR folder.

### APIs

```http
GET /actuator/health
GET /actuator/info
```

### Definition of Done

- `docker compose up` starts local dependencies successfully.
- API service starts locally.
- Worker service starts locally.
- Health endpoint returns healthy status.
- GitHub Actions CI runs on push.
- README explains how to run the project locally.
- Initial architecture diagram exists.
- ADR folder exists with at least one architecture decision.

---

## Sprint 1: Domain Model, Database Schema, and Event APIs

### Goal

Model the core event-booking domain and expose basic APIs for events, sessions, venues, and availability.

### Learning Outcomes

By the end of this sprint, you should understand:

- Relational modeling for event booking systems.
- Flyway migration discipline.
- REST resource modeling.
- Request validation.
- Global error handling.
- OpenAPI documentation.
- Pagination and filtering basics.
- UTC-first event-time modeling.
- Separating stored timestamps from display timezones.
- Integration testing with a real database.

### Technical Requirements

Create core tables:

- `users`
- `roles`
- `venues`
- `events`
- `event_sessions`
- `booking_slots`
- `audit_logs`

Implement:

- Flyway migrations.
- JPA entities and repositories.
- Service layer for events and sessions.
- Request and response DTOs.
- Input validation.
- Global exception handler.
- Standard error response format.
- OpenAPI documentation.
- Integration tests for event and session APIs.
- Store `event_sessions.start_time_utc` and `event_sessions.end_time_utc` in UTC.
- Store `event_sessions.display_timezone` as an IANA timezone string.
- Document the timezone strategy in `docs/architecture/database-schema.md`.

### APIs

```http
POST   /api/v1/events
GET    /api/v1/events
GET    /api/v1/events/{eventId}
PATCH  /api/v1/events/{eventId}
POST   /api/v1/events/{eventId}/sessions
GET    /api/v1/events/{eventId}/sessions
GET    /api/v1/sessions/{sessionId}
GET    /api/v1/sessions/{sessionId}/availability
```

### Definition of Done

- Event creation works.
- Event listing supports pagination.
- Session creation works.
- Availability endpoint returns total and remaining capacity.
- Event session times are stored in UTC.
- Event session display timezone is stored separately.
- Invalid requests return consistent validation errors.
- Flyway migrations run successfully.
- OpenAPI docs expose all endpoints.
- Integration tests cover create, list, get, and validation cases.
- README includes sample API requests.

---

## Sprint 2: Spring Security, JWT Authentication, and RBAC

### Goal

Secure the platform with Spring Security, JWT authentication, role-based authorization, and ownership checks.

### Learning Outcomes

By the end of this sprint, you should understand:

- Spring Security fundamentals.
- JWT generation and validation.
- Password hashing.
- Authentication filters.
- Role-based access control.
- Ownership-based authorization.
- Security testing.
- Secure error handling.
- Audit logging for sensitive actions.
- Refresh token rotation and revocation.
- CORS policy for allowed origins and methods.
- Secure cookie versus bearer-token decision documented.
- Dependency vulnerability scanning with OWASP Dependency-Check or similar.

### Technical Requirements

Implement:

- User registration.
- Login endpoint.
- Password hashing.
- JWT access tokens.
- JWT validation filter.
- Role-based authorization.
- Roles:
  - `CUSTOMER`
  - `ORGANIZER`
  - `ADMIN`
- Ownership checks:
  - Customers can manage only their own bookings.
  - Organizers can manage only their own events.
  - Admins can inspect all resources.
- Audit logs for sensitive mutations.
- Refresh-token storage and revocation table.
- Refresh-token rotation on use.
- CORS configuration.
- Security integration tests.
- OWASP Dependency-Check report generated in CI.

### APIs

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/me

POST   /api/v1/events
PATCH  /api/v1/events/{eventId}
POST   /api/v1/events/{eventId}/sessions
GET    /api/v1/admin/audit-logs
```

### Definition of Done

- Unauthenticated users cannot access protected endpoints.
- Users can register and log in.
- JWT access tokens and refresh tokens are issued on login.
- Refresh tokens rotate on use.
- Logout revokes active refresh tokens.
- JWT-protected endpoints work.
- Customers cannot access organizer or admin endpoints.
- Organizers cannot mutate events they do not own.
- Admins can access admin endpoints.
- Security tests cover authorized and unauthorized cases.
- OpenAPI docs explain authentication.
- Audit logs are written for event/session mutations.
- CORS behavior is explicitly configured and documented.
- CI produces a dependency vulnerability scan report.

---

## Sprint 3: Core Booking Engine and Concurrency Safety

### Goal

Build the core scarce-resource booking engine and ensure the system cannot overbook under concurrent demand.

### Learning Outcomes

By the end of this sprint, you should understand:

- Database transactions.
- Row-level locking.
- Pessimistic locking.
- Idempotency keys.
- Duplicate request handling.
- Booking state machines.
- Race-condition prevention.
- Concurrency testing.
- Capacity restoration on cancellation.
- Pessimistic versus optimistic locking tradeoffs.
- How row-level locks behave across multiple API instances.

### Technical Requirements

Create tables:

- `bookings`
- `booking_items`
- `idempotency_keys`
- `booking_state_transitions`

Booking states:

- `PENDING_PAYMENT`
- `CONFIRMED`
- `CANCELLED`
- `EXPIRED`
- `FAILED`

Implement:

- Booking creation.
- Booking retrieval.
- User booking listing.
- Booking cancellation.
- Row-level locking on slot capacity.
- `version` column on scarce-capacity rows for update detection and documentation of optimistic-locking alternatives.
- Idempotency-key support.
- Explicit booking state transitions.
- Capacity decrement on booking hold.
- Capacity restoration on cancellation.
- Concurrency tests.

Booking creation flow:

```text
1. Validate user and session.
2. Check idempotency key.
3. Start database transaction.
4. Lock booking slot row.
5. Check remaining capacity.
6. Decrement remaining capacity.
7. Create booking in PENDING_PAYMENT state.
8. Persist idempotency result.
9. Commit transaction.
```

### APIs

```http
POST /api/v1/sessions/{sessionId}/bookings
GET  /api/v1/bookings/{bookingId}
GET  /api/v1/me/bookings
POST /api/v1/bookings/{bookingId}/cancel
GET  /api/v1/bookings/{bookingId}/state-transitions
```

Required headers for booking creation:

```http
Idempotency-Key: <client-generated-key>
```

### Definition of Done

- A user can create a booking.
- A user can view their own bookings.
- A user cannot view another user's booking.
- A user can cancel their own booking.
- Duplicate booking requests with the same idempotency key return the same result.
- Same idempotency key with different payload fails safely.
- Two users cannot book the last remaining slot at the same time.
- Concurrency tests prove zero overbooking against one API instance locally.
- Locking-strategy ADR explains why pessimistic locking is used for final booking confirmation and why optimistic locking is not the primary mechanism for scarce-capacity writes.
- Booking state transitions are persisted.
- README explains the booking consistency model.

---

## Sprint 4: Fake Payment Service and Reservation Expiry

### Goal

Add a realistic payment-like workflow with booking holds, fake authorization, payment failure, duplicate callbacks, and booking expiry.

### Learning Outcomes

By the end of this sprint, you should understand:

- Multi-step backend workflows.
- Payment-like state transitions.
- Correct money representation without floats.
- Idempotent callbacks.
- Timeout handling.
- Reservation holds.
- Expiry workflows.
- Failure-path testing.
- State reconciliation basics.

### Technical Requirements

Create tables:

- `payment_intents`
- `payment_events`

Payment amount rules:

- Store payment amounts as integer minor units, such as cents.
- Store currency code separately, such as `SGD` or `USD`.
- Do not use `double` or `float` for money.
- Use `BigDecimal` only for calculations if needed, then persist minor units.

Extend booking states:

- `PENDING_PAYMENT`
- `PAYMENT_AUTHORIZED`
- `CONFIRMED`
- `PAYMENT_FAILED`
- `EXPIRED`
- `CANCELLED`

Implement:

- Fake payment intent creation.
- Fake payment authorization.
- Fake payment failure.
- Duplicate payment callback handling.
- Booking confirmation after successful payment.
- Capacity release after failed payment.
- Booking expiry after timeout.
- Scheduled expiry job or worker-triggered expiry.
- Tests for success, failure, duplicate callback, and expiry.
- Tests proving payment amounts are not represented using floating point values.

### APIs

```http
POST /api/v1/bookings/{bookingId}/payment-intent
GET  /api/v1/payment-intents/{paymentIntentId}

POST /api/v1/fake-payments/{paymentIntentId}/authorize
POST /api/v1/fake-payments/{paymentIntentId}/fail
POST /api/v1/fake-payments/{paymentIntentId}/timeout
```

### Definition of Done

- Booking starts in `PENDING_PAYMENT`.
- Payment intent can be created for a pending booking.
- Successful fake payment confirms the booking.
- Failed fake payment releases held capacity.
- Timed-out fake payment expires the booking.
- Duplicate fake payment callbacks are safe.
- Invalid state transitions are rejected.
- Tests cover all payment outcomes.
- Payment amounts are stored as integer minor units or handled with `BigDecimal`, never floating point.
- Booking state transition history is complete.

---

## Sprint 5: SQS, Transactional Outbox, Worker Service, and DLQ

### Goal

Add a production-style asynchronous processing layer using transactional outbox, AWS SQS, worker processing, retry handling, and dead-letter queues.

### Learning Outcomes

By the end of this sprint, you should understand:

- Event-driven backend architecture.
- Transactional outbox pattern.
- Event schema versioning.
- At-least-once delivery.
- SQS producers and consumers.
- Dead-letter queues.
- Idempotent consumers.
- Retry handling.
- Async side effects.
- Failure isolation.
- Graceful worker shutdown.
- SQS visibility-timeout semantics.

### Technical Requirements

Create table:

- `outbox_events`

Suggested columns:

- `id`
- `aggregate_type`
- `aggregate_id`
- `event_type`
- `payload`
- `status`
- `created_at`
- `published_at`
- `retry_count`
- `last_error`
- `event_version`

Outbox event types:

- `BookingCreated`
- `PaymentAuthorized`
- `PaymentFailed`
- `BookingConfirmed`
- `BookingCancelled`
- `BookingExpired`
- `NotificationRequested`

Implement:

- Write outbox events inside database transactions.
- Publish outbox events to SQS after commit.
- Worker service consumes SQS messages.
- SQS message envelope includes `eventVersion`.
- Worker handles notification simulation.
- Worker records processing status.
- Failed messages are retried.
- Repeated failures go to DLQ.
- Consumers are idempotent.
- Unknown event versions are rejected safely and routed to failure handling.
- Worker handles graceful shutdown by stopping new polling and completing or safely abandoning in-flight messages.
- Integration tests for publishing and consuming.

### APIs

No new public user-facing APIs are required.

Optional admin APIs:

```http
GET  /api/v1/admin/outbox-events
GET  /api/v1/admin/outbox-events/{eventId}
POST /api/v1/admin/outbox-events/{eventId}/retry
GET  /api/v1/admin/worker-events
```

### Definition of Done

- Booking lifecycle changes write outbox events.
- Outbox publisher sends events to SQS.
- Worker service consumes SQS messages.
- SQS message envelope includes `eventVersion`.
- Notification simulation runs asynchronously.
- Failed messages retry.
- Poison messages reach DLQ.
- Duplicate SQS messages do not create duplicate side effects.
- Admin can inspect outbox event status.
- Tests cover success, retry, and failure paths.
- Documentation explains outbox plus SQS flow.
- ADR explains event schema versioning and compatibility strategy.
- Documentation explains worker graceful shutdown, ECS stop timeout, and SQS visibility-timeout behavior.

---

## Sprint 6: Waitlist and Async Promotion Workflow

### Goal

Build an event-driven waitlist workflow that promotes users when capacity opens after cancellations or expiries.

### Learning Outcomes

By the end of this sprint, you should understand:

- Eventual consistency.
- Async business workflows.
- Waitlist ordering.
- Promotion expiry.
- Duplicate event handling.
- Worker coordination.
- Fairness rules.
- State-machine modeling.
- Sequence diagram documentation.

### Technical Requirements

Create tables:

- `waitlist_entries`
- `waitlist_promotions`

Waitlist states:

- `WAITING`
- `PROMOTED`
- `ACCEPTED`
- `EXPIRED`
- `CANCELLED`

Implement:

- Join waitlist when session is full.
- Prevent duplicate active waitlist entries.
- Trigger waitlist promotion after booking cancellation.
- Trigger waitlist promotion after booking expiry.
- Promote first eligible user.
- Promotion acceptance creates booking hold.
- Promotion expiry promotes next eligible user.
- Duplicate worker execution is safe.
- Waitlist order is preserved.
- Tests for cancellation to promotion to acceptance.

### APIs

```http
POST /api/v1/sessions/{sessionId}/waitlist
GET  /api/v1/me/waitlist
GET  /api/v1/waitlist-promotions/{promotionId}
POST /api/v1/waitlist-promotions/{promotionId}/accept
POST /api/v1/waitlist-promotions/{promotionId}/decline
POST /api/v1/waitlist-promotions/{promotionId}/expire
```

Optional admin APIs:

```http
GET /api/v1/admin/sessions/{sessionId}/waitlist
GET /api/v1/admin/waitlist-promotions
```

### Definition of Done

- User can join waitlist for a full session.
- User cannot join same waitlist twice.
- Booking cancellation triggers async waitlist promotion.
- Booking expiry triggers async waitlist promotion.
- First eligible waitlisted user is promoted.
- Promotion acceptance creates a booking hold.
- Promotion decline or expiry promotes the next user.
- Duplicate worker messages do not double-promote users.
- Tests cover normal and duplicate-processing paths.
- Docs include waitlist sequence diagram.

---

## Sprint 7: Redis Caching and Rate Limiting

### Goal

Use Redis for real backend needs: caching read-heavy availability data and rate-limiting hot endpoints.

### Learning Outcomes

By the end of this sprint, you should understand:

- Redis caching.
- Cache key design.
- TTL selection.
- Cache invalidation.
- Cache hit and miss tracking.
- Read-heavy endpoint optimization.
- Redis-backed rate limiting.
- Hot endpoint protection.
- Shared Redis behavior across multiple API replicas.
- Stale-read tradeoffs.

### Technical Requirements

Use Redis for:

1. Availability caching.
2. Event/session listing cache.
3. Rate limiting.

Cache keys:

```text
availability:session:{sessionId}
event-sessions:event:{eventId}
```

Invalidate cache on:

- `BookingConfirmed`
- `BookingCancelled`
- `BookingExpired`
- `WaitlistPromotionAccepted`
- `SlotCapacityUpdated`

Rate-limit endpoints:

- booking creation
- login
- fake payment authorization
- waitlist join

Expose metrics:

- cache hit count
- cache miss count
- rate-limit rejection count

### APIs

No new public APIs are required.

Affected APIs:

```http
GET  /api/v1/sessions/{sessionId}/availability
GET  /api/v1/events/{eventId}/sessions
POST /api/v1/sessions/{sessionId}/bookings
POST /api/v1/auth/login
POST /api/v1/fake-payments/{paymentIntentId}/authorize
POST /api/v1/sessions/{sessionId}/waitlist
```

Optional admin/debug APIs:

```http
GET    /api/v1/admin/cache/stats
DELETE /api/v1/admin/cache/sessions/{sessionId}
```

### Definition of Done

- Availability endpoint uses Redis cache.
- Cache invalidates after booking lifecycle changes.
- Event/session listings are cacheable.
- Booking endpoint has Redis-backed rate limiting.
- Login endpoint has Redis-backed rate limiting.
- Tests cover cache hits, misses, and invalidation.
- Tests cover rate-limit rejection.
- Metrics expose cache and rate-limit behavior.
- Load test compares cached and uncached availability reads.

---

## Sprint 8: Observability with Prometheus and Grafana

### Goal

Make the system observable with metrics, dashboards, structured logs, and correlation IDs.

### Learning Outcomes

By the end of this sprint, you should understand:

- Spring Boot Actuator metrics.
- Prometheus scraping.
- Grafana dashboard design.
- Application metrics.
- Business metrics.
- Worker metrics.
- Structured logging.
- Correlation IDs.
- Request tracing across API and worker logs.
- Operational debugging.

### Technical Requirements

Implement:

- Prometheus metrics endpoint.
- Grafana dashboards.
- Structured JSON logs.
- Request correlation ID middleware/filter.
- Correlation ID propagation into outbox events.
- Correlation ID propagation into SQS messages.
- Worker logs include correlation ID.
- API latency metrics.
- Booking business metrics.
- Worker processing metrics.
- Cache metrics.
- Error metrics.

Recommended metrics:

```text
http_server_requests_seconds
booking_create_latency_seconds
booking_cancel_latency_seconds
bookings_created_total
bookings_confirmed_total
bookings_cancelled_total
bookings_expired_total
payment_failures_total
waitlist_promotions_total
redis_cache_hits_total
redis_cache_misses_total
rate_limit_rejections_total
sqs_messages_processed_total
sqs_messages_failed_total
worker_processing_duration_seconds
outbox_events_published_total
outbox_events_failed_total
```

### APIs

No new public APIs are required.

Operational endpoints:

```http
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

Optional admin APIs:

```http
GET /api/v1/admin/system/health
GET /api/v1/admin/system/metrics-summary
```

### Definition of Done

- Prometheus scrapes API service.
- Prometheus scrapes worker service.
- Grafana dashboard shows API latency and error rate.
- Grafana dashboard shows booking business metrics.
- Grafana dashboard shows worker throughput and failures.
- Grafana dashboard shows Redis cache behavior.
- Logs include correlation IDs.
- A failed async event can be traced through API logs, outbox event, SQS message, and worker logs.
- README includes dashboard screenshots.
- Observability docs explain key metrics.

---

## Sprint 9: Load Testing, Database Optimization, and Performance Report

### Goal

Generate concrete backend performance evidence by load-testing the system, identifying bottlenecks, optimizing queries/indexes/cache paths, and documenting results.

### Learning Outcomes

By the end of this sprint, you should understand:

- Load testing methodology.
- p95 and p99 latency.
- Throughput measurement.
- High-concurrency booking behavior.
- Database lock contention.
- Query-plan analysis.
- Index design.
- Cache impact measurement.
- Connection-pool tuning.
- Performance-report writing.

### Technical Requirements

Use k6 or Locust.

Create load test scenarios:

1. Availability browsing.
2. High-concurrency booking.
3. Fake payment callbacks.
4. Waitlist promotion.
5. Mixed realistic traffic.
6. Multi-replica booking correctness with at least two API instances.

Measure:

- p50 latency
- p95 latency
- p99 latency
- request throughput
- error rate
- successful bookings
- rejected bookings
- overbooking count
- overbooking count across two or more API replicas
- cache hit rate
- worker processing delay
- queue backlog
- database CPU or query time if available

Optimization tasks:

- Run `EXPLAIN ANALYZE` on slow queries.
- Add justified indexes.
- Remove obvious N+1 queries.
- Tune pagination queries.
- Reduce transaction duration.
- Compare cached vs uncached availability reads.
- Document before and after metrics.

### APIs

Load-tested APIs:

```http
GET  /api/v1/events
GET  /api/v1/events/{eventId}/sessions
GET  /api/v1/sessions/{sessionId}/availability
POST /api/v1/sessions/{sessionId}/bookings
POST /api/v1/bookings/{bookingId}/payment-intent
POST /api/v1/fake-payments/{paymentIntentId}/authorize
POST /api/v1/bookings/{bookingId}/cancel
POST /api/v1/sessions/{sessionId}/waitlist
POST /api/v1/waitlist-promotions/{promotionId}/accept
```

### Definition of Done

- Load test scripts are committed.
- Load tests can run locally.
- Performance report exists in `docs/load-tests/results.md`.
- Report includes methodology.
- Report includes p95 and p99 latency.
- Report includes concurrency results.
- Report proves zero overbooking under high-concurrency test.
- Report includes a multi-replica correctness run, either locally with multiple API containers or in ECS with the API service scaled to at least two tasks.
- Report verifies Redis rate limiting behaves globally across replicas, not per-process.
- Report verifies cache invalidation remains correct when requests hit different API replicas.
- Report includes before and after optimization numbers.
- At least three indexes are justified.
- Grafana dashboard captures load-test behavior.
- Final numbers are suitable for resume bullets.

---

## Sprint 10: Terraform, AWS ECS Fargate Deployment, and Full CI/CD

### Goal

Deploy the API and worker services to AWS ECS Fargate using Terraform and GitHub Actions CI/CD.

### Learning Outcomes

By the end of this sprint, you should understand:

- Terraform project structure.
- AWS ECS Fargate deployment.
- ECR repositories.
- Task definitions.
- Application Load Balancer basics.
- IAM roles and policies.
- SQS and DLQ provisioning.
- CloudWatch logs.
- Secret management.
- Cost-controlled deployment and teardown.
- ECS graceful shutdown settings.
- CI/CD deployment flow.
- Smoke testing deployed services.

### Technical Requirements

Provision with Terraform:

- ECS cluster.
- Fargate service for API.
- Fargate service for worker.
- ECR repository for API image.
- ECR repository for worker image.
- Application Load Balancer.
- Target group.
- Security groups.
- IAM task execution role.
- IAM task role.
- SQS queue.
- SQS dead-letter queue.
- CloudWatch log groups.
- Secrets Manager or SSM Parameter Store for secrets.
- Environment variables for Supabase Postgres and Redis configuration.

CI/CD pipeline:

On pull request:

- compile
- unit tests
- integration tests
- OWASP Dependency-Check for Java dependencies
- Trivy image scan
- Checkov or tfsec scan for Terraform
- Docker build check

On merge to main:

- run full test suite
- build API image
- build worker image
- push images to ECR
- run Terraform plan
- deploy/update ECS services
- run smoke tests

### APIs

Deployment smoke-test APIs:

```http
GET /actuator/health
GET /api/v1/events
GET /api/v1/sessions/{sessionId}/availability
```

Optional deployment verification APIs:

```http
GET /api/v1/admin/system/health
GET /api/v1/admin/system/metrics-summary
```

### Definition of Done

- Terraform provisions required AWS infrastructure.
- Terraform plan is scanned with Checkov or tfsec.
- API container runs on ECS Fargate.
- Worker container runs on ECS Fargate.
- API service is reachable through load balancer.
- Worker can consume SQS messages.
- SQS and DLQ are provisioned.
- Supabase Postgres connection works from deployed service.
- Logs are visible in CloudWatch.
- GitHub Actions builds and pushes Docker images.
- Docker images are scanned with Trivy before deployment.
- GitHub Actions deploys services after merge to main.
- Smoke tests pass after deployment.
- At least one cost-controlled validation run scales the API service to two ECS tasks behind the ALB and verifies zero overbooking.
- Worker task definition includes graceful shutdown configuration and documented stop timeout.
- Teardown or scale-down instructions are documented to control cost.
- Deployment architecture is documented.

---

## Sprint 11: Final Portfolio Packaging

### Goal

Turn the project into a polished flagship portfolio artifact that recruiters and interviewers can understand quickly.

### Learning Outcomes

By the end of this sprint, you should understand:

- Technical storytelling.
- Architecture documentation.
- API documentation.
- System design communication.
- Resume-oriented project packaging.
- How to present backend tradeoffs.
- How to explain failure modes.
- How to make a project inspectable without a frontend.

### Technical Requirements

Create polished documentation:

```text
README.md
docs/architecture/system-overview.md
docs/architecture/database-schema.md
docs/architecture/booking-consistency.md
docs/architecture/event-driven-workflows.md
docs/architecture/failure-modes.md
docs/architecture/deployment-architecture.md
docs/architecture/event-schema-versioning.md
docs/architecture/graceful-shutdown.md
docs/architecture/locking-strategy.md
docs/architecture/security.md
docs/architecture/cost-strategy.md
docs/api/openapi.md
docs/api/example-requests.md
docs/load-tests/results.md
docs/observability/dashboards.md
```

Include diagrams:

- System architecture diagram.
- Database ERD.
- Booking state machine.
- Booking sequence diagram.
- Payment workflow sequence diagram.
- Outbox/SQS/worker sequence diagram.
- Waitlist promotion sequence diagram.
- Deployment diagram.

README should include:

- What the project is.
- Why it exists.
- Backend problems solved.
- Tech stack.
- Architecture overview.
- API examples.
- Booking consistency model.
- Event-driven workflow.
- Observability screenshots.
- Load-test results.
- Deployment notes.
- How to run locally.
- What could be improved next.

### APIs

No new APIs are required.

Documentation should include examples for:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/events
POST /api/v1/events/{eventId}/sessions
GET  /api/v1/sessions/{sessionId}/availability
POST /api/v1/sessions/{sessionId}/bookings
POST /api/v1/bookings/{bookingId}/payment-intent
POST /api/v1/fake-payments/{paymentIntentId}/authorize
POST /api/v1/bookings/{bookingId}/cancel
POST /api/v1/sessions/{sessionId}/waitlist
```

### Definition of Done

- README is polished.
- Architecture diagrams are complete.
- OpenAPI docs are generated and linked.
- Load-test results are documented, including multi-replica correctness results.
- Grafana screenshots are included.
- Deployment documentation exists.
- Failure modes are documented.
- Event schema versioning strategy is documented.
- Worker graceful shutdown behavior is documented.
- Security scanning reports are linked or summarized.
- Cost-control and teardown instructions are documented.
- Local setup works from README instructions.
- Resume bullets are drafted.
- LinkedIn/GitHub project summary is drafted.
- Project is ready to show in interviews.

---

## 5. Minimum Viable Flagship Scope

If time becomes tight, the minimum version should include:

- Spring Boot API service.
- PostgreSQL schema with Flyway.
- Spring Security with JWT and RBAC.
- Booking creation and cancellation.
- Concurrency-safe capacity control.
- Idempotency keys.
- Fake payment workflow.
- Transactional outbox.
- SQS worker.
- DLQ.
- Redis caching.
- Docker Compose.
- OpenAPI docs.
- Integration tests.
- Concurrency tests.
- k6 or Locust load-test report.
- Event schema versioning.
- Worker graceful shutdown documentation.
- Security scans in CI.
- Money stored without floating point values.
- UTC-first timezone handling.
- Polished README.

This is enough to be a strong backend internship portfolio project.

---

## 6. Stretch Goals

Only add these after the full core system works:

- Managed Redis.
- Stripe test-mode integration.
- Real email provider.
- API gateway.
- Kubernetes deployment.
- OpenTelemetry tracing.
- Multi-region read replica discussion.
- Admin dashboard UI.
- Terraform staging and production environments.
- Blue-green deployment.
- Canary deployment.
- Chaos testing.
- Contract testing between API and worker.
- Webhook support for organizers.
- Public status page.

---

## 7. Hosting Deferral Guidance

It is reasonable to defer always-on AWS hosting until the final sprint. The project should be designed so local development proves most backend behavior first, then Fargate deployment proves production-style release and multi-replica behavior near the end.

Deferring hosting is acceptable if you still produce:

- Terraform code.
- A successful deployment run.
- CloudWatch log screenshots or notes.
- A deployed smoke-test result.
- One short multi-replica load test with at least two API tasks.
- Teardown or scale-down instructions.

The consequence is that you should not claim continuous production operation or uptime. Instead, claim cost-controlled deployment, reproducible infrastructure, and validated multi-replica correctness.

---

## 8. Features to Avoid Early

Avoid these until the backend is strong:

- Full frontend.
- Mobile app.
- Seat map UI.
- Real payment integration.
- Complex pricing engine.
- Recommendation system.
- Search engine.
- GraphQL.
- Too many microservices.
- Multi-region deployment.
- Complex analytics dashboard.
- Social features.

These are not necessary for the backend signal.

---

## 9. Final Resume Bullets After Completion

Potential project entry:

```text
SlotForge - Production-Grade Event Booking Backend | Spring Boot, PostgreSQL, Redis, SQS, Docker, Terraform, AWS ECS Fargate

- Built an API-first event booking backend for scarce-capacity sessions, supporting booking, cancellation, fake payment authorization, waitlists, admin workflows, JWT RBAC, and OpenAPI-documented REST APIs.
- Implemented a concurrency-safe reservation engine using PostgreSQL transactions, row-level locks, idempotency keys, and explicit booking state transitions to prevent overbooking under concurrent demand.
- Designed an event-driven worker pipeline with transactional outbox, AWS SQS, retry handling, and DLQ isolation for booking lifecycle events, payment outcomes, notifications, and waitlist promotion.
- Added Redis caching and rate limiting for high-demand availability and booking endpoints, with Prometheus/Grafana dashboards tracking p95 latency, cache hit rate, error rate, worker throughput, and failed events.
- Deployed API and worker containers to AWS ECS Fargate using Terraform and GitHub Actions CI/CD, with Supabase Postgres, ECR, IAM, SQS/DLQ, CloudWatch logs, automated smoke tests, security scans, and a multi-replica load test proving zero overbooking across API replicas.
```

---

## 10. Core Principle

Do not build this as an event booking product.

Build it as a backend systems project disguised as an event booking product.

The product is intentionally simple. The backend engineering is the flagship.
