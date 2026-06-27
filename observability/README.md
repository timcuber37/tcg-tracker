# Observability (Phase 2)

Micrometer → InfluxDB → Grafana. The backend pushes metrics over the Influx v1 line
protocol; Grafana boots pre-provisioned with the datasource and a dashboard.

## Run

```bash
docker compose up --build
```

New services:
- **influxdb** (`influxdb:1.8`) — `localhost:8086`, database `tcgtracker`, auth off (dev).
- **grafana** (`grafana/grafana:11.3.0`) — `http://localhost:3001` (anonymous viewing on;
  admin login `admin`/`admin` or `GRAFANA_USER`/`GRAFANA_PASSWORD`). Opens on the
  **TCGTracker — Service Metrics** dashboard.

Metrics export is gated by `INFLUX_ENABLED` (true in compose, false by default so local
`gradlew bootRun` and tests don't push to a missing server).

## What's instrumented

| Metric (measurement)      | Type    | Source                                   |
|---------------------------|---------|------------------------------------------|
| `tcg.commands`            | counter | `CommandHandler` — tag `type` add/remove |
| `tcg.projections`         | counter | `CassandraProjector` — tag `event_type`  |
| `pokewallet.price.fetch`  | timer   | `PriceEnrichmentService.fetchAndCache`   |
| `catalog.search`          | timer   | `CatalogSearchService.search`            |
| `jvm.*`, `process.*` …    | gauge   | Actuator/Micrometer defaults             |

Every series is tagged `application` + `role` (web/consumer/sync) via `ObservabilityConfig`.

**Naming/units (Micrometer → Influx):** metric names are written with **underscores**, not dots
(`catalog.search` → measurement `catalog_search`). Timers expose fields `count`/`mean`/`sum`/`upper`
in **seconds** (the Influx registry's base time unit); `publishPercentiles(0.5,0.95,0.99)` lands as a
companion measurement `<name>_percentile` carrying a `phi` tag and a `value` field (used by the p95
series on the latency panels).

## Verify metrics are landing

The Actuator endpoints are behind the JWT filter (they return 401 unauthenticated), so verify from
the InfluxDB side:

```bash
docker compose exec influxdb influx -database tcgtracker -execute 'SHOW MEASUREMENTS'
# custom timer (generate traffic first: curl "localhost:8080/api/search?query=pikachu")
docker compose exec influxdb influx -database tcgtracker \
  -execute "SELECT sum(\"count\") FROM catalog_search WHERE role='web' AND time > now() - 15m"
# custom counters (need an authenticated add/remove command)
docker compose exec influxdb influx -database tcgtracker \
  -execute 'SELECT sum("value") FROM tcg_commands GROUP BY "type"'
```

Then open Grafana (`localhost:3001`) and watch the **TCGTracker — Service Metrics** dashboard fill in.
