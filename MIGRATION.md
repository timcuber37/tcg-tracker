# PokéCollect — Python → Java/Spring Migration Plan

Reference document for porting the app from **Python/Flask** to **Java 23 + Spring Boot 4.1**.
The Python app (repo root) shares the same infrastructure (MySQL, Cassandra, Supabase Postgres, Kafka).

> **Status: migration complete — all 6 phases done.** The Java/Spring backend (`backend/`) + React
> SPA (`frontend/`) fully replace the Python app and run as a containerized stack via `docker compose up`.
> The Python files remain in the repo (not yet removed).

## Locked-in decisions

| Decision | Choice | Rationale |
|---|---|---|
| UI layer | **REST API + new SPA** (React + Vite + TS) | Clean decoupling; Spring is a pure JSON backend |
| Build tool | **Gradle** (Kotlin DSL) | Concise build files, wrapper bundled (no install) |
| Process model | **Single app, Spring profiles** (`web` / `consumer` / `sync`) | One JAR, role chosen by `spring.profiles.active` |
| Auth | **Client-side Supabase + Spring resource server** | SPA logs in via supabase-js; Spring only validates the JWT — no passwords in Spring |
| Migration style | **Big-bang rewrite** (not strangler) | App is small; running both against shared infra is simpler than dual-write |

## Target architecture

```
┌─────────────────┐   JSON / Bearer JWT   ┌──────────────────────────────┐
│   React SPA     │ ───────────────────►  │   Spring Boot (single JAR)    │
│  (Vite + TS)    │                       │  profiles: web/consumer/sync  │
│  supabase-js ───┼── login ─► Supabase   │  ┌──────────────────────────┐ │
└─────────────────┘            Auth       │  │ @RestController (web)    │ │
        ▲                        │        │  │ @KafkaListener (consumer)│ │
        └──── validates JWT ◄────┘        │  │ @Scheduled (sync)        │ │
                (JWKS, ES256)             │  └──────────────────────────┘ │
                                          └───────┬───────┬───────┬───────┘
                                               MySQL  Cassandra  Postgres+Kafka
```

## Repository layout

```
backend/    Spring Boot (Gradle Kotlin DSL, Java 23)
  src/main/java/com/pokecollect/
    config/   DataSourceConfig, SecurityConfig
    web/      QueryController, CollectionController, UserController, HealthController, JwtSupport
    query/    CatalogSearchService, MarketService, CollectionService, dto/, cassandra/
    command/  UserAccountService, domain/ (JPA entities), repo/ (JPA repos)
  src/main/resources/application.yml
frontend/   React + Vite + TypeScript (supabase-js installed)
db/         shared schema files (unchanged)
```

---

## Phases

### Phase 0 — Scaffold ✅ DONE
Stand up both halves and prove connectivity.
- Spring Boot project via Initializr, Gradle wrapper bundled (no system Gradle).
- `application.yml` with all datasources + Kafka + profile structure.
- `HealthController` → `GET /api/health`; Phase-0 permit-all `SecurityConfig`.
- Vite React+TS frontend, dev proxy `/api` → `:8080`, supabase-js installed.
- **Verified:** SPA `:5173` → proxy → backend `:8080` health JSON.

### Phase 1 — Read path ✅ DONE
Search and browse against existing data.
- **Multi-datasource** (`DataSourceConfig`): MySQL `@Primary` (JPA), Supabase Postgres
  (`postgresJdbcTemplate`, parsed from libpq `POSTGRES_DSN`), Cassandra (Spring Data).
- **Postgres catalog search** (`CatalogSearchService` / `QueryController`):
  `GET /api/search` (paginated, set-filtered, Energy-excluded), `/api/sets` (query-aware),
  `/api/rare-cards`.
- **Cassandra browse-by-set** (`MarketService`, `cassandra/` read models + repos):
  `GET /api/market`, `/api/market/sets`.
- **JPA entities** (`User`, `Card`, `Collection`) + repos, validated vs live MySQL schema.
- **Verified:** 7,187 catalog cards searchable; 18,637 Cassandra cards / 143 sets.

### Phase 2 — Auth + collection ✅ DONE
- **OAuth2 resource server** (`SecurityConfig`) validating Supabase **ES256** JWTs via JWKS.
  Public read endpoints open; collection/user endpoints require a token.
- `POST /api/users/sync` (+ `GET /api/users/me`) — mirror Supabase user into MySQL shadow row.
- `GET /api/collection` — Cassandra `collection_by_user` for JWT `sub`, price overlay, grouping, totals.
- Backend loads project-root `.env` natively (`spring.config.import: optional:file:../.env`).
- **Verified:** 401/200 gating; user sync; collection grouping (totalValue 225.50 from seeded data).

### Phase 3 — Write path + events ✅ DONE
Command side and CQRS event flow.
- `CommandController`: `POST /api/commands/add-from-search`, `/add-copy`, `/remove-card`
  (all authenticated; user = JWT `sub`; remove enforces ownership).
- `CommandHandler` + JPA writes (find-or-create card, insert/delete collection rows in MySQL).
- `EventPublisher` via `KafkaTemplate<String,String>` — publishes JSON `CardAddedToCollection` /
  `CardRemovedFromCollection` (snake_case via Jackson `@JsonNaming`, wire-compatible with Python).
- `CassandraProjector` `@KafkaListener` (profile `consumer`, headless) projects events into
  Cassandra `collection_by_user` via `CqlSession`.
- Kafka uses String (de)serializers; profile-specific `web-application-type: none` for consumer/sync.
- **Verified end-to-end:** add → MySQL + Kafka → consumer → Cassandra → `/api/collection`
  (Mew added then removed; add-copy 2→3; ownership guard 404). Run web + consumer as two instances.

### Phase 4 — External API + sync ✅ DONE
- `PokeWalletClient` (`RestClient`) — card/set/image fetch + TCGPlayer price extraction.
  Responses are read as `String` then `ObjectMapper.readTree` (the plain RestClient builder
  has no JsonNode converter); 404/429 swallowed to null.
- Image proxy `GET /card-image/{id}` (`ImageController`) with disk cache (`static/cards/`,
  configurable via `image.cache-dir`; Docker volume in prod). Verified: fetch → cache → guard(400).
- Lazy price enrichment (`PriceEnrichmentService`) wired into `CommandHandler.addFromSearch` when
  no price is supplied. Verified: Charizard with null price → fetched + cached 209.18.
- `CatalogSyncJob` (`@Scheduled`, profile `sync`, headless) + `SchedulingConfig` (`@EnableScheduling`,
  profile `sync`). Ports the XY-era filter + Cassandra/Postgres upserts. Boot verified; a full
  sync pass is intentionally not run in dev (API quota).

### Phase 5 — Frontend SPA ✅ DONE
React + Vite + TS SPA, React Router.
- `lib/supabase.ts` (supabase-js, anon key), `lib/api.ts` (typed client, injects session JWT as
  `Authorization: Bearer`), `lib/auth.tsx` (`AuthProvider`; calls `/api/users/sync` on session).
- Components: `TopBar` (search + query-aware set dropdown + profile menu), `AuthModal`
  (login/register), `CardGrid` (+ add-to-collection), `CardRain` (rare-card rain), `Pagination`.
- Pages: **Home** (hero + rain, or search results + pagination), **Collection** (grouped, +/−
  controls, total value, refetch-after-mutation for eventual consistency), **Market** (browse by set).
- Vite dev proxy forwards `/api` **and** `/card-image` to `:8080`. Frontend env: `VITE_SUPABASE_URL`,
  `VITE_SUPABASE_ANON_KEY` in `frontend/.env`.
- **Verified:** clean TS build; public browse + image proxy; anon-key sign-in → ES256 token →
  backend auth; landing (hero + rain) and search grid render correctly (headless screenshots).

### Phase 6 — Docker ✅ DONE
- `backend/Dockerfile`: multi-stage Gradle (temurin:23-jdk) → executable boot jar → temurin:23-jre
  runtime. `jar` task disabled so the runtime COPYs a single jar. One image, three services via
  `SPRING_PROFILES_ACTIVE=web|consumer|sync`.
- `frontend/Dockerfile`: multi-stage Vite build → nginx. `nginx.conf` serves the SPA (history
  fallback) and reverse-proxies `/api` + `/card-image` to `web:8080` (replaces the dev proxy).
  Supabase URL/anon key passed as build args (anon key is public).
- `docker-compose.yml` rewritten for the Java/React stack: mysql, zookeeper, kafka, cassandra,
  cassandra-init, web, consumer, sync, frontend. Postgres stays on Supabase. `card_images` volume.
- **Verified:** both images build; full `docker compose up` brings all 8 containers healthy;
  nginx → web → Supabase search works, image proxy works, consumer subscribes, sync runs.
- Note: the legacy Python root `Dockerfile` is now orphaned (compose no longer references it).

---

## Key technical notes

- **Supabase JWT:** ES256 via JWKS (`${SUPABASE_URL}/auth/v1/.well-known/jwks.json`, public).
  Must set `spring.security.oauth2.resourceserver.jwt.jws-algorithms: ES256` — Spring defaults to
  RS256 and otherwise rejects tokens. Claims: `sub` = user UUID, `user_metadata.username`.
- **Multi-datasource:** both JDBC datasources are declared explicitly so Spring Boot's
  auto-config backs off cleanly; MySQL is `@Primary` for JPA, Postgres is JdbcTemplate-only.
- **Env loading:** `spring.config.import: optional:file:../.env[.properties]` reads the project-root
  `.env` in dev; `optional:` lets Docker fall back to container env.
- **Running locally:** `cd backend && .\gradlew.bat bootRun` (PowerShell needs the `.\` prefix).
  Wrapper auto-downloads Gradle; only a JDK is required. Frontend: `cd frontend && npm run dev`.

## Test account
A dedicated Supabase test account (with a mirrored MySQL shadow row and a few seeded
`collection_by_user` rows) was used to verify the auth + collection flows during the
migration. Its credentials are kept out of the repo — create your own account via the
app's sign-up to exercise the same paths.
