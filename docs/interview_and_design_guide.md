# Sprint 0: Project Foundation and Local Environment
## Topic: Reproducible Service Foundations and Modular Boundaries
### Key Design Considerations
- SlotForge uses a modular monolith plus a separate worker service. Synchronous domain operations remain cohesive inside the API service, while asynchronous workloads can eventually scale and fail independently.
- We avoided creating a microservice for every domain area because that would introduce premature network boundaries, distributed transactions, contract management, and operational overhead. Internal modules provide separation without those costs.
- The API and worker are independently runnable and deployable. Neither service depends on the other's implementation; both may depend only on a deliberately narrow shared module.
- The shared module is reserved for stable cross-process contracts and small utilities. Controllers, repositories, and service-specific business logic must remain in their owning service to avoid tight coupling.
- The Gradle Wrapper pins the build-tool version, allowing developer machines and CI to execute the same build without relying on a system-installed Gradle version.
- Multi-stage Docker builds keep compilers, build caches, and source code out of the final runtime images. The final containers use smaller JRE images and run as a non-root user to reduce attack surface.
- Docker Compose provides a reproducible local topology containing the API, worker, PostgreSQL, Redis, Prometheus, and Grafana. PostgreSQL and Redis are provisioned in Sprint 0 but are not yet consumed by application code.
- Compose service names provide internal DNS. Containers communicate through names such as `api`, `worker`, and `prometheus`; `localhost` inside a container refers only to that container.
- Container startup and service readiness are different states. Health checks verify that a process is operational, while `depends_on` with `service_healthy` controls local startup sequencing. It does not prove that Spring has established a database or Redis connection.
- Spring Boot Actuator exposes restricted health, information, and Prometheus endpoints. Operational endpoints are explicitly allowlisted to avoid exposing unnecessary internal details.
- Micrometer instruments the applications, Prometheus scrapes and stores time-series metrics, and Grafana queries Prometheus for visualization. Grafana does not collect or store the application metrics itself.
- Grafana's Prometheus datasource is provisioned from version-controlled YAML. This avoids manual configuration drift and gives future dashboards a stable datasource identifier.
- Fedora's SELinux policy required the `Z` bind-mount option. Traditional file permissions and mandatory access controls are independent security layers; a world-readable host file may still be inaccessible to a container.
- GitHub Actions performs compilation, testing, and executable JAR packaging on pushes and pull requests. Read-only workflow permissions apply least privilege, while concurrency cancellation prevents obsolete runs from wasting CI capacity.
- PostgreSQL will become the authoritative source of durable domain state. Redis will hold shared but non-authoritative state, so future correctness must not depend exclusively on cached data.
- The separate worker introduces a real distributed-systems boundary. Future event handlers must tolerate duplicate delivery, version event contracts, expose failures through metrics and logs, and shut down safely.

### Potential Interview Questions
- **Q:** Why did you choose a modular monolith plus worker instead of implementing SlotForge entirely as microservices?

  **A:** SlotForge's synchronous booking operations require strong transactional consistency and currently belong to one cohesive domain. Keeping them inside a modular monolith allows local PostgreSQL transactions and avoids premature network calls, distributed transactions, and deployment overhead. The worker is separated because asynchronous processing has genuinely different scaling, retry, and failure characteristics. This creates one meaningful distributed boundary without unnecessary service sprawl.

- **Q:** If the API and worker share one repository and a shared module, are they really independent services?

  **A:** Repository separation is not the definition of service independence. The API and worker produce separate executable JARs and container images and can be started, scaled, stopped, and deployed independently. They do not depend on each other's implementation. Their shared module is intentionally restricted to stable contracts and utilities. The main trade-off is that shared-contract changes still require compatibility discipline and coordinated testing.

- **Q:** What is the difference between a container running, a health check passing, and an application being ready?

  **A:** A running container only means its main process has not exited. A passing health check means a specific operational probe succeeds. Readiness means the application can perform the work for which it receives traffic, including access to required dependencies. In Sprint 0, the API health endpoint proves that the Spring HTTP and Actuator stacks are running, but it does not yet prove PostgreSQL or Redis connectivity because those integrations have not been added.

- **Q:** Why use a multi-stage Dockerfile?

  **A:** The first stage contains the JDK, Gradle Wrapper, dependencies, and source code needed to compile the application. Only the executable Spring Boot JAR is copied into a smaller JRE-based runtime stage. This reduces image size and attack surface and prevents build tools and source files from reaching production. SlotForge also runs the final process as an unprivileged user to limit the impact of a container compromise.

- **Q:** Why are Prometheus and Grafana separate components?

  **A:** They solve different problems. Micrometer produces application metrics, Prometheus periodically scrapes and stores those time series, and Grafana queries Prometheus to visualize and explore them. This separation allows the storage and collection layer to operate independently from presentation. In SlotForge, Prometheus scrapes both the API and worker, while Grafana receives Prometheus as a provisioned datasource.

- **Q:** Why should Grafana datasources be provisioned through files instead of configured manually?

  **A:** Manual UI configuration is difficult to reproduce, review, and recreate. SlotForge stores datasource configuration in version control, so every local environment receives the same Prometheus URL, stable UID, and default-datasource behavior. This is configuration as code and reduces environment drift.

- **Q:** Why did Docker report permission denied for a readable Prometheus configuration file?

  **A:** Fedora uses SELinux mandatory access controls in addition to ordinary Unix permissions. The file mode allowed reading, but its SELinux label did not permit container access. Adding the `Z` bind-mount option gave the file a private container-compatible label. This demonstrates that discretionary file permissions and mandatory security policies are separate authorization layers.

- **Q:** What does Docker Compose `depends_on` guarantee?

  **A:** Basic `depends_on` controls startup order, while a `service_healthy` condition waits for the dependency's configured health check. It does not guarantee permanent availability, application-level connectivity, or correct transaction behavior. Services must still handle dependency failures after startup. In SlotForge, these conditions make local startup deterministic, but future database and Redis integrations will require their own connection handling and health indicators.

- **Q:** Why use the Gradle Wrapper in CI when GitHub Actions can install Gradle?

  **A:** The wrapper pins the Gradle version as part of the repository and makes local and CI builds consistent. The Gradle setup action configures caching and the runner environment, but the workflow still executes `./gradlew`. This prevents differences caused by developers or runners having unrelated Gradle versions installed.

- **Q:** What additional work is required before claiming SlotForge is production-ready?

  **A:** Sprint 0 proves reproducible builds, process startup, health endpoints, observability wiring, and CI. It does not yet prove domain correctness, database migrations, authentication, concurrency safety, idempotency, queue processing, multi-replica behavior, security scanning, or cloud deployment. Those claims must be implemented and validated in later sprints before they appear as completed capabilities.

# Sprint 1: Domain Model, Database Schema, and Event APIs
## Topic: Relational Modeling, UTC Time, and Persistence Boundaries
### Key Design Considerations
- PostgreSQL is the authoritative source of durable domain state, and Flyway owns all schema changes. Hibernate uses `ddl-auto: validate` so entity mappings are checked without allowing the ORM to mutate production schemas.
- Events are separated from event sessions because one event may occur multiple times and at different venues. Venue ownership therefore belongs to the session relationship rather than the event itself.
- Session moments are persisted as UTC `Instant` values in `TIMESTAMPTZ` columns, while the intended IANA display timezone is stored separately. An offset identifies one instant; an IANA zone preserves regional daylight-saving rules.
- Each event session receives exactly one `booking_slots` row in the same transaction. Explicit total and remaining counters make availability reads inexpensive and provide the row-lock boundary for Sprint 3's scarce-capacity decisions.
- Database checks enforce positive capacity, remaining-capacity bounds, valid status values, normalized emails, valid session time ranges, and structured audit metadata. DTO and service validation improve client feedback, while constraints remain the final integrity boundary.
- UUID primary keys support public identifiers and future distributed event contracts at the cost of larger indexes and poorer locality than sequential integers.
- Mutable entities use JPA `@Version` for optimistic conflict detection. This suits low-contention administrative edits, while high-contention booking capacity will use pessimistic row locking.
- Entities remain internal persistence models. Request and response DTOs keep the REST contract independent from lazy relationships, ORM annotations, and future schema changes.
- Spring Data repositories provide typed persistence access, while services own transactions and business coordination. Session and capacity inserts either commit together or roll back together.
- Index column order follows concrete filters and sorts. Equality keys lead composite indexes, chronological fields follow, and UUIDs provide deterministic pagination tie-breakers.
- A global exception handler distinguishes invalid input (`400`), absent resources (`404`), state or concurrency conflicts (`409`), and unexpected server failures. Correct classification prevents unsafe retries and misleading reliability metrics.
- OpenAPI is generated from Spring MVC mappings, DTO types, validation constraints, and explicit operation/response annotations. Testcontainers verifies the API against clean PostgreSQL databases rather than relying on an in-memory approximation.
### Potential Interview Questions
- **Q:** Why store both a UTC session timestamp and a display timezone?

  **A:** A timestamp with an offset identifies an instant, but it does not preserve regional rules. PostgreSQL `TIMESTAMPTZ` and Java `Instant` store the unambiguous moment, while an IANA identifier such as `Europe/Stockholm` preserves how that moment should be displayed across daylight-saving changes. SlotForge validates offset-aware input, converts it to UTC, and stores the display zone separately.

- **Q:** Why use Flyway instead of Hibernate schema generation?

  **A:** Flyway makes schema evolution explicit, ordered, reviewable, and reproducible across local, CI, and production environments. Applied migrations have recorded checksums and are treated as immutable. Hibernate runs in validation mode, catching mapping drift without silently changing tables or constraints.

- **Q:** How does SlotForge prevent a session from existing without capacity state?

  **A:** The session service inserts `event_sessions` and its one-to-one `booking_slots` row inside one PostgreSQL transaction. If capacity persistence fails, the transaction rolls back the session insert. A unique constraint guarantees one authoritative capacity row per session, and an integration test forces the second write to fail and verifies that no session remains.

- **Q:** Why store remaining capacity instead of calculating it from bookings?

  **A:** Counting bookings for every availability read becomes expensive and does not itself provide a simple concurrency boundary. SlotForge stores total and remaining capacity in one row, making reads cheap and providing a row that Sprint 3 can lock before the final booking decision. The trade-off is denormalization, so booking state and the counter must be updated atomically.

- **Q:** How does JPA optimistic locking work in this design?

  **A:** Hibernate maps the `version` column with `@Version`. Updates include the previously read version in the `WHERE` clause and increment it in the same statement. If another transaction updated the row first, zero rows match and Hibernate raises an optimistic-lock exception, which SlotForge exposes as `409 Conflict`. This detects lost updates without locking readers.

- **Q:** Why will booking capacity use pessimistic locking if version columns already exist?

  **A:** Optimistic locking works well when conflicts are rare, but a booking spike creates frequent conflicts and potentially expensive retry storms. For the final scarce-capacity decision, SlotForge will use PostgreSQL `SELECT ... FOR UPDATE` so contenders wait, then evaluate the latest capacity. The database lock works across multiple API replicas, unlike an in-process Java lock.

- **Q:** Why test against PostgreSQL with Testcontainers instead of H2?

  **A:** H2 differs from PostgreSQL in timestamp behavior, constraints, SQL features, and locking semantics. Testcontainers starts a clean PostgreSQL 17 instance, applies every Flyway migration, validates Hibernate mappings, and exercises real HTTP transactions. This proves behavior against the same database engine used by the application.

- **Q:** Why return DTOs rather than JPA entities from controllers?

  **A:** Returning entities couples the public contract to persistence, risks exposing internal fields, and can trigger lazy-loading or recursive-serialization failures. SlotForge maps entities to explicit response records inside service transactions, keeping HTTP schemas stable while the database model evolves.
