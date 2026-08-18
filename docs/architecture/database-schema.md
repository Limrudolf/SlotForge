# SlotForge Database Schema

## Scope

Sprint 1 establishes the relational foundation for venues, events, sessions,
capacity, users, roles, and audit history. PostgreSQL is the authoritative
source of durable state. Flyway owns schema creation, while Hibernate validates
that Java mappings match the migrated schema.

## Relationships

```text
users ──< user_roles >── roles

events ──< event_sessions >── venues
                    |
                    └── 1 booking_slots

users ──< audit_logs
```

- An event describes a product such as a concert.
- An event session represents one scheduled occurrence of an event.
- A venue belongs to the session relationship, allowing one event to run at
  different venues.
- Each session has exactly one booking-slot row containing authoritative
  capacity state.
- User and role tables prepare for Sprint 2 authentication and RBAC.
- Audit logs preserve structured records of sensitive actions.

## Identifier Strategy

Tables use PostgreSQL `UUID` primary keys with `gen_random_uuid()` defaults.
Hibernate also generates UUIDs for JPA-created entities. Database defaults
protect direct SQL and future non-JPA writers, while application generation
allows identifiers to exist before inserts are flushed.

UUIDs are larger and have poorer index locality than sequential `BIGINT`
values, but they are difficult to enumerate publicly and transfer naturally
through future outbox events and distributed workflows.

## Event and Session State

Event status is restricted to:

```text
DRAFT, PUBLISHED, CANCELLED
```

Session status is restricted to:

```text
SCHEDULED, CANCELLED, COMPLETED
```

Java maps these values using `EnumType.STRING`. Numeric enum ordinals are not
used because reordering Java constants could silently change stored meaning.
Database `CHECK` constraints preserve integrity when writes bypass the API.

## UTC and Display Timezone Strategy

`event_sessions.start_time_utc` and `end_time_utc` use PostgreSQL
`TIMESTAMPTZ` and map to Java `Instant`.

An API request supplies offset-aware values such as:

```text
2026-10-10T19:00:00+02:00
```

The persisted instant is:

```text
2026-10-10T17:00:00Z
```

`TIMESTAMPTZ` preserves the instant, not the original regional timezone.
Therefore, `display_timezone` stores a separate IANA identifier such as
`Europe/Stockholm` or `Asia/Singapore`. IANA zones contain daylight-saving and
historical offset rules; a fixed offset such as `+02:00` does not.

The API validates timezone identifiers with `ZoneId.of`. Session end time must
be strictly later than start time when compared as instants. Hibernate is also
configured with `hibernate.jdbc.time_zone=UTC`.

## Capacity Model

`booking_slots` contains:

```text
event_session_id
total_capacity
remaining_capacity
version
```

The database enforces:

```text
total_capacity > 0
0 <= remaining_capacity <= total_capacity
one booking_slots row per event session
```

Session creation inserts the session and capacity row in one transaction, with
initial remaining capacity equal to total capacity. An explicit counter avoids
counting booking rows on every availability request and provides one natural
row-lock boundary for Sprint 3's scarce-capacity decision.

This is intentional denormalization. Future booking and cancellation workflows
must update booking state and capacity within the same transaction.

## Concurrency Metadata

Mutable domain tables contain a `version BIGINT` column mapped with JPA
`@Version`. Hibernate includes the previously read version in update predicates
and increments it automatically. If another transaction has already updated
the row, zero rows match and Hibernate raises an optimistic-lock conflict.

Optimistic locking is suitable for relatively rare administrative conflicts.
Sprint 3 will use pessimistic `SELECT ... FOR UPDATE` locking for final capacity
decisions because contention is expected during booking spikes.

## Deletion Policy

Domain history is normally preserved by changing status rather than deleting
rows. Foreign keys use restrictive deletion for events, venues, sessions, and
capacity. User-role join rows cascade when their user or role is removed.
Audit records survive user deletion by setting `actor_user_id` to null.

## Index Strategy

Primary keys and unique constraints create B-tree indexes automatically.
PostgreSQL does not automatically index child foreign-key columns, so explicit
indexes match the required query patterns:

| Index | Query supported |
|---|---|
| `events(created_at DESC, id DESC)` | Stable unfiltered event pagination |
| `events(status, created_at DESC, id DESC)` | Status-filtered event pagination |
| `event_sessions(event_id, start_time_utc, id)` | Chronological sessions for an event |
| `event_sessions(venue_id, start_time_utc, id)` | Chronological venue schedule and FK checks |
| `audit_logs(entity_type, entity_id, occurred_at DESC)` | Entity history |
| `audit_logs(correlation_id)` | Request/workflow tracing |
| Partial actor audit index | User action history without indexing null actors |

The final `id` column provides deterministic ordering when timestamps tie. The
unique constraint on `booking_slots.event_session_id` already creates the
index required for availability lookup and future row locking; a duplicate
index would add write cost without benefit.

Indexes are not added to every column. Each index consumes storage and adds
insert/update work. Sprint 9 will validate query plans using `EXPLAIN ANALYZE`
and load-test evidence.

## Migration Discipline

Migrations live in:

```text
backend/api-service/src/main/resources/db/migration
```

Applied migrations are immutable because Flyway records their checksums in
`flyway_schema_history`. Schema changes receive new versioned migrations rather
than edits to applied files.

Hibernate uses:

```yaml
ddl-auto: validate
```

This detects mapping drift but prevents Hibernate from silently altering the
schema. Testcontainers integration tests start empty PostgreSQL 17 databases,
apply every migration, validate the mappings, and exercise real HTTP behavior.
