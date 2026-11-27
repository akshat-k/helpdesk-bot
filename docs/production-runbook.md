# Production deployment and operations

## Database schema and safe rollout

Flyway owns schema changes; Hibernate runs with `ddl-auto=validate` and must
not modify production tables.

- A new empty MySQL database applies `src/main/resources/db/migration/V1__create_current_schema.sql` on startup.
- An existing installation must be backed up and checked against the current
  schema before being baselined. This release intentionally keeps
  `baseline-on-migrate=false` so an unexpected non-empty database cannot be
  silently accepted.
- For the existing application schema, run Flyway's `baseline` command at
  version `1` exactly once using deployment secrets, then deploy the new
  version. The baseline does not alter application rows. Confirm the
  `flyway_schema_history` row and run a restore rehearsal before rollout.
- New changes must use monotonically increasing `V2__...sql` migrations. Never
  edit a migration already applied in an environment.

Before upgrading, take a point-in-time database snapshot and confirm the
application image can start with `ddl-auto=validate`. If validation fails,
stop the rollout; do not switch DDL back to `update`.

## Outbox and dead-letter operations

The outbox stores the event payload until Kafka acknowledges the send. Monitor
`helpdesk_outbox_events{status="PENDING|PROCESSING|DEAD"}` and
`helpdesk_outbox_oldest_pending_age_seconds` in Prometheus. Alerts are defined
in `monitoring/alerts.yml`.

Configure `OPS_ADMIN_SUBJECTS` as a comma-separated allowlist of stable Google
`sub` identifiers. Operators can inspect paged dead events using
`GET /v1/admin/outbox/dead` and requeue a verified dead event using
`POST /v1/admin/outbox/dead/{eventId}/replay`. The endpoint resets retry state
and does not expose the serialized event payload. It is safe only after
checking the cause in logs and downstream state.

Outbox and processed-event rows older than `OUTBOX_RETENTION_DAYS` (90 days by
default) are pruned daily. Dead outbox rows are retained until an operator
replays or removes them; alert on them rather than allowing unnoticed growth.
Kafka consumer failures go to `<source-topic>.DLT`. Inspect DLT records with
Kafka's console consumer or the organization's Kafka UI, identify the root
cause, and use the platform's approved offset/replay procedure. Replaying a
DLT record can resend an email; successful event-ID deduplication reduces
duplicates but cannot make SMTP exactly-once.

## Production topology

`docker-compose.yml` remains a local-development stack with one MySQL instance
and one Kafka broker. Do not treat it as a production cluster.

`docker-compose.prod.yml` runs a hardened, non-root application container and
a rate-limiting Nginx ingress. Production MySQL and Kafka are external
services. Supply managed endpoints, credentials, and TLS/SASL configuration
through the deployment platform's secret manager. Use:

- MySQL with automated backups, point-in-time recovery, TLS, and a dedicated
  least-privilege application user.
- Kafka with at least three brokers, replication factor 3, TLS/SASL, durable
  storage, and retention policies for primary and DLT topics.
- An external load balancer that terminates TLS and only forwards to the
  ingress. Do not publish the app container's 9191 port directly.
- A protected OTLP collector and Prometheus scrape path. The ingress blocks
  `/actuator/**`; allow Prometheus access only on a private monitoring network.
- A distributed/API-gateway rate limiter for multi-replica deployments. The
  included Nginx limiter is per proxy process and is a useful local/edge
  safeguard, not a shared quota across multiple ingress replicas.

The production Spring profile requires external database, Kafka, OAuth,
company-domain, mail, OTLP, and explicit CORS settings. Replica counts should
be sized against database pool limits and Kafka partitions. Use rolling
deployment with graceful shutdown enabled; validate readiness before shifting
traffic.

## Observability and response

- `/actuator/health/liveness` is for restart decisions. `/actuator/health/readiness`
  includes dependency readiness and is for traffic routing.
- `/actuator/prometheus` exports JVM, HTTP, Kafka-client, and outbox metrics.
  Keep it off public ingress.
- Traces export to `OTEL_EXPORTER_OTLP_ENDPOINT`; begin with a low
  `TRACING_SAMPLING_PROBABILITY` and increase only when collector capacity and
  data-handling policy allow it.
- Logs use structured JSON in the production profile. Include trace identifiers
  when investigating requests across the API, database, Kafka, and mail worker.
- Alert on dead events, pending backlog, oldest event age, database health,
  Kafka availability, error rates, and saturation. A green process health
  check alone is not evidence that notifications are flowing.

## Backup, restore, and incident checklist

1. Use managed MySQL snapshots/PITR; encrypt backups and test restore in an
   isolated environment. Never test restore against production.
2. Keep Kafka topic replication and retention aligned with the required
   recovery-point objective. The outbox is the source for unpublished events;
   published and processed-event rows have finite retention.
3. During an incident, pause repeated replay attempts, inspect pending/dead
   metrics and DLTs, verify downstream email state, fix the failure, then
   replay a small sample and observe results before bulk recovery.
4. Record event IDs and ticket IDs for any manual replay. The delivery
   guarantee is at-least-once; an SMTP provider acceptance followed by a
   process crash can cause a duplicate email.
5. After recovery, verify backlog age trends down, DLT volume stops increasing,
   and readiness/consumer lag return to normal.

## Validation commands

Run unit and slice tests:

```sh
./mvnw test
```

Run the MySQL 8 + Kafka Testcontainers flow (requires a working Docker engine):

```sh
./mvnw -Pintegration-tests verify
```

Validate production Compose settings without starting services:

```sh
docker compose -f docker-compose.prod.yml config
```
