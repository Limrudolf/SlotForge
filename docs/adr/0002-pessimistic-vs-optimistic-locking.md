# ADR 0002: Pessimistic Capacity Locking and Optimistic Booking-State Locking

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

SlotForge must prevent overselling a session when many customers compete for
the same capacity. It must also restore capacity exactly once when a booking is
cancelled. The contention characteristics differ: the capacity row is a hot,
shared counter, while concurrent changes to one booking are comparatively rare.

Both `booking_slots` and `bookings` contain version columns. The presence of a
version column does not require the same locking strategy for every workflow.

## Decision

Booking creation uses a PostgreSQL pessimistic write lock on the session's
`booking_slots` row. The final capacity check and decrement execute after
`SELECT ... FOR UPDATE` and inside the transaction that persists the booking,
item, initial transition, and idempotency result.

Cancellation uses optimistic locking on the `bookings` row. Hibernate flushes
the version-checked state update before shared capacity is touched. It then
obtains a pessimistic write lock on `booking_slots`, restores the booking-item
quantity, and persists the cancellation transition in the same transaction.

## Rationale

Optimistic capacity updates would work by detecting a version conflict and
retrying, but booking spikes make conflicts expected rather than exceptional.
Retries would repeat application and database work and could amplify load.
Pessimistic locking makes contenders wait and then evaluate current capacity.

Booking-state conflicts are uncommon, so optimistic locking avoids taking a
read-time row lock for every cancellation. An early flush ensures a stale
cancellation loses before it can restore capacity. The shared capacity row
remains pessimistically locked because valid cancellations of different
bookings may update it concurrently and should serialize rather than fail.

Database locks work across API threads and replicas. JVM synchronization was
rejected because it protects only one process.

## Alternatives Considered

### Optimistic locking for all writes

This avoids blocking but creates retry pressure on the hottest row during a
booking spike. Independent cancellations against the same slot would also
conflict unnecessarily.

### Pessimistic locking for all writes

This gives simple sequential reasoning but makes low-contention booking-state
reads wait unnecessarily and increases the number and duration of explicit
locks.

### In-process locks

Java locks are simple locally but do not coordinate separate API processes or
containers and therefore cannot support future multi-replica correctness.

### Serializable isolation

Serializable transactions could detect unsafe schedules but would broaden the
scope of serialization failures and retries. Row-level locking expresses the
specific capacity invariant more directly.

## Consequences

### Positive

- A capacity decision uses the latest committed value.
- Zero-overbooking correctness is enforced by PostgreSQL across API processes.
- Cancellation restores an allocation at most once.
- Independent cancellations serialize safely on shared capacity.
- Transactions roll back capacity, booking, item, transition, and idempotency
  changes together.

### Negative

- Requests for one popular session may wait behind the same capacity row.
- Transaction duration must remain short; no network calls may occur while the
  capacity lock is held.
- Optimistic cancellation conflicts surface as `409 Conflict` rather than being
  retried automatically.
- Future workflows that acquire both booking and capacity resources must use a
  documented, consistent order to reduce deadlock risk.

## Validation

PostgreSQL Testcontainers integration tests launch independent HTTP requests
and prove:

- two customers cannot both reserve the last slot;
- simultaneous identical idempotent requests create one booking;
- a losing different-session idempotency transaction rolls back capacity;
- duplicate cancellation restores capacity once; and
- independent cancellations restore shared capacity correctly.

Multi-replica validation remains a Sprint 9 and Sprint 10 requirement.
