# Booking Consistency Model

## Invariants

SlotForge maintains these booking invariants:

```text
0 <= remaining_capacity <= total_capacity
one committed allocation per successful logical booking request
one initial transition per booking
capacity restored at most once when a booking is cancelled
```

PostgreSQL constraints and transactions are the final consistency boundary.

## Booking Creation

```text
authenticate user
→ check completed idempotency result
→ validate session
→ SELECT booking_slots ... FOR UPDATE
→ check idempotency again
→ verify remaining capacity
→ decrement capacity
→ insert PENDING_PAYMENT booking and item
→ insert NULL → PENDING_PAYMENT transition
→ insert idempotency result
→ commit and release lock
```

The capacity lock is held from the `FOR UPDATE` query until commit or rollback.
Requests for different sessions can proceed concurrently; requests for one
session serialize at its capacity row. A waiting request sees the latest value
after the preceding transaction commits.

## Idempotency

Clients generate an opaque `Idempotency-Key`. The database scopes it by user:

```text
UNIQUE (user_id, idempotency_key)
```

A lowercase SHA-256 fingerprint represents:

```text
booking-create:v1|sessionId={uuid}|quantity={integer}
```

An identical replay returns the original booking. Reuse for a different session
or quantity returns `409 Conflict`. If different-slot transactions race on the
unique constraint, the loser rolls back completely. A non-transactional
orchestrator then reads the winner in a fresh transaction because PostgreSQL
does not allow useful queries inside an aborted transaction.

## State Machine and History

`bookings.status` is the current snapshot. `booking_state_transitions` is the
append-only history explaining how the snapshot was reached.

Sprint 3 implements:

```text
NULL            → PENDING_PAYMENT
PENDING_PAYMENT → CANCELLED
CONFIRMED       → CANCELLED
```

Repeated cancellation and cancellation from terminal states are rejected.
Payment-related transitions are introduced in Sprint 4.

## Cancellation

Cancellation uses a hybrid locking strategy:

```text
read booking and version
→ verify owner and current state
→ set CANCELLED
→ flush version-checked booking update
→ lock booking_slots row
→ restore booking-item quantity
→ insert transition
→ commit
```

The early version check prevents two cancellations of the same booking from
releasing capacity twice. The capacity lock serializes valid cancellations of
different bookings sharing a session.

## Authorization

JWT authentication establishes the user UUID and roles. Creation derives the
customer from the JWT rather than accepting a user ID in the request. Booking
reads and transition history require the owner or an administrator. Cancellation
requires the owner and the `CUSTOMER` role.

## Tested Claims and Limits

The integration suite runs against PostgreSQL rather than an in-memory database
and proves zero overbooking with concurrent requests against one local API
instance. It also covers duplicate idempotency races and cancellation races.

Sprint 3 does not yet claim multi-replica validation, strict request fairness,
payment confirmation, expiry, or async event delivery. Multi-replica zero
overbooking is validated in later load-testing and deployment sprints.
