# TCGTracker

A Pokémon Trading Card Game collection manager built around a CQRS architecture. Search the modern card catalog, build a personal collection, and track live TCGPlayer market prices.

A **Java 23 + Spring Boot** backend (REST API) with a **React + TypeScript** single-page frontend, demonstrating distributed-systems patterns: command/query separation, an event-driven write side, and multiple specialised read stores.

> **Disclaimer:** This is an unofficial, non-commercial fan project for educational purposes. It is **not affiliated with, endorsed by, or sponsored by** Nintendo, The Pokémon Company, Game Freak, or Creatures Inc. *Pokémon* and all related names, logos, and the Poké Ball are trademarks and copyrights of their respective owners. Card images and pricing are retrieved at runtime from the third-party PokéWallet API and are not redistributed by this repository. The [MIT license](LICENSE) below covers **only the original source code in this repository** — it does not grant any rights to Pokémon intellectual property. If you fork or deploy this, supply your own API keys and do not use it commercially.

## Architecture

The browser authenticates directly with Supabase and calls a stateless Spring API; writes and reads are separated through a Kafka event bus.

```
  ┌─────────────────┐   JSON / Bearer JWT   ┌──────────────────────────────┐
  │   React SPA     │ ───────────────────►  │   Spring Boot (single JAR)    │
  │  (Vite + TS)    │                       │  profiles: web/consumer/sync  │
  │  supabase-js ───┼── login ─► Supabase   │  ┌──────────────────────────┐ │
  └─────────────────┘            Auth       │  │ @RestController (web)    │ │
          ▲                        │        │  │ @KafkaListener (consumer)│ │
          └──── validates JWT ◄────┘        │  │ @Scheduled (sync)        │ │
                 (JWKS, ES256)              │  └──────────────────────────┘ │
                                            └───────┬───────┬───────┬───────┘
                                                 MySQL  Cassandra  Kafka / Supabase

   user action ──► web: write to MySQL ──► publish event to Kafka
                                              └─► consumer: project into Cassandra read model

   PokéWallet API ──► sync (daily) ──► Cassandra cards_by_set + Supabase catalog
                      live price on add ──► Supabase catalog
```

- **MySQL** is the authoritative write side — users, cards, and collections.
- **Kafka** carries `CardAddedToCollection` / `CardRemovedFromCollection` events to the read side.
- **Cassandra** holds denormalized read models (`collection_by_user`, `cards_by_set`) keyed for JOIN-free lookups.
- **Supabase PostgreSQL** stores the full card catalog with live TCGPlayer prices, used for search and collection pricing.
- **Supabase Auth** issues ES256 JWTs; the SPA logs in via supabase-js and the Spring backend validates the token against Supabase's JWKS as an OAuth2 resource server. A shadow user row is mirrored into MySQL for collection foreign keys.
- **PokéWallet API** is the source of card data, images, and live pricing.

## Stack

- Java 23, Spring Boot 4 (Gradle), embedded Tomcat
- React 19 + TypeScript + Vite (SPA), nginx (production static serving + reverse proxy)
- MySQL 8 (write side) — Spring Data JPA
- Apache Cassandra 4 (read side) — Spring Data Cassandra
- Supabase — PostgreSQL (catalog + prices, via JdbcTemplate) and Auth (JWT resource server)
- Apache Kafka (event bus) — Spring for Apache Kafka
- PokéWallet REST API (card catalog + TCGPlayer prices)

## Project layout

```
backend/                              Spring Boot (Gradle Kotlin DSL, Java 23)
  src/main/java/com/pokecollect/
    config/      DataSourceConfig      MySQL (@Primary, JPA) + Supabase Postgres (JdbcTemplate)
                 SecurityConfig        OAuth2 resource server (Supabase ES256 JWT via JWKS)
                 SchedulingConfig      @EnableScheduling (sync profile only)
    web/         QueryController        GET /api/search, /api/sets, /api/rare-cards, /api/market[/sets]
                 CollectionController   GET /api/collection (authenticated)
                 CommandController      POST /api/commands/{add-from-search,add-copy,remove-card}
                 UserController         POST /api/users/sync, GET /api/users/me
                 ImageController        GET /card-image/{id} — proxy + disk cache
    query/       CatalogSearchService   Postgres catalog search / prices / rare ids
                 MarketService          Cassandra browse-by-set
                 CollectionService      Cassandra collection + grouping + price overlay
                 cassandra/             @Table read models + repositories
    command/     CommandHandler         JPA writes + event publish
                 UserAccountService     mirror Supabase user into MySQL
                 domain/ repo/          JPA entities + repositories
    events/      Card{Added,Removed}…   JSON events (snake_case) + EventPublisher (KafkaTemplate)
    consumer/    CassandraProjector     @KafkaListener → collection_by_user (consumer profile)
    external/    PokeWalletClient       RestClient + TCGPlayer price extraction
                 PriceEnrichmentService lazy live-price fetch on add
    sync/        CatalogSyncJob         @Scheduled daily catalog sync (sync profile)
  src/main/resources/application.yml

frontend/                             React + Vite + TypeScript
  src/lib/        supabase.ts api.ts auth.tsx     client, typed API (Bearer), auth context
  src/components/ TopBar AuthModal CardGrid CardRain Pagination
  src/pages/      Home Collection Market
  nginx.conf      serves the SPA + proxies /api and /card-image to the backend

db/
  mysql_schema.sql        users, cards, collections
  cassandra_schema.cql    collection_by_user, cards_by_set
  postgres_schema.sql     catalog_embeddings (Supabase)

docker-compose.yml        mysql, zookeeper, kafka, cassandra, cassandra-init,
                          web / consumer / sync (one backend image), frontend (nginx)
.env / .env.example       shared configuration
MIGRATION.md              the Python → Java/Spring migration record
```

The backend is one image run as three roles, selected by `SPRING_PROFILES_ACTIVE`:
**web** (REST API), **consumer** (`@KafkaListener` → Cassandra), **sync** (`@Scheduled` catalog sync).

## Running with Docker (recommended)

```bash
cp .env.example .env        # fill in MYSQL_PASSWORD, SUPABASE_*, POSTGRES_DSN, POKEWALLET_API_KEY
docker compose up --build
```

Then open **http://localhost:3000**. All services start in dependency order; the Cassandra schema is
applied automatically by the `cassandra-init` container. The Supabase Postgres schema
(`db/postgres_schema.sql`) must be applied once via the Supabase SQL Editor. The Dockerized Cassandra
starts empty, so the catalog data appears once the `sync` service has run.

Stop with `docker compose down`. If Kafka ever fails to start after an unclean shutdown, run
`docker compose down -v` then `up` to reset the transient broker state.

## Running locally (dev)

**Prerequisites:** local MySQL, Cassandra, and Kafka running with schemas applied; a Supabase project
with Auth enabled and `POSTGRES_DSN` set. A JDK is required; the Gradle wrapper is bundled (no install).
The backend auto-loads the project-root `.env`.

```powershell
# Terminal 1 — backend web API (http://localhost:8080)
cd backend; .\gradlew.bat bootRun

# Terminal 2 — Kafka consumer (projects events into Cassandra)
cd backend; .\gradlew.bat bootRun --args='--spring.profiles.active=consumer'

# Terminal 3 — frontend dev server (http://localhost:5173)
cd frontend; npm install; npm run dev

# Terminal 4 (optional) — catalog sync (uses PokéWallet API quota; runs on start)
cd backend; .\gradlew.bat bootRun --args='--spring.profiles.active=sync'
```

`frontend/.env` needs `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` (the public anon key).
Minimum to use the app is Terminals 1 + 3; add the consumer to see add/remove reflected in your collection.

## Features

- **Card search** — full-text search across XY-era+ English cards, paginated at 25/page. The set dropdown filters both the results and the available set list (only sets containing matches for the current query). Energy cards excluded.
- **Collection** — deduplication by card: duplicate copies collapse into one tile with a `×N` count and `+`/`−` controls. Running total value in the header.
- **Live pricing** — TCGPlayer market prices fetched via `/cards/{id}` on first add, cached in Supabase Postgres and preserved across daily sync passes via `COALESCE` upserts.
- **Home animation** — high-rarity cards (Special Illustration Rare, Hyper Rare, Secret Rare, Ultra Rare) rain down the landing page; a small pooled set of IDs keeps images browser-cached.
- **Auth** — register / sign in via Supabase (supabase-js in the browser). The backend never sees passwords — it validates the Supabase JWT and mirrors a shadow user row into MySQL.
- **Sync service** — pulls English XY-era and newer sets (XY, Mega Evolution, Sun & Moon, Sword & Shield, Scarlet & Violet) from PokéWallet daily, respecting the free-tier rate limit (40s between set requests).

## API budget

The PokéWallet free tier allows 100 req/hour and 1,000 req/day.

| Source | Requests | Notes |
|---|---|---|
| Daily sync | ~150 (1 per set) | 40s delay between sets stays within the hourly limit |
| Add to collection | 1 per unique card | Price cached in Postgres after first fetch |
| Image proxy | 1 per unique card | Cached to `backend/static/cards/` (a Docker volume in prod) |
| Rain animation | 0 ongoing | Small pool; images served from browser cache after first load |

## Deployment notes

The app needs persistent long-running processes (Kafka consumer, scheduled sync) and is not compatible
with serverless platforms (Vercel, Netlify). Suitable targets: **Railway**, **Render**, or **Fly.io** —
all support Docker and background workers. For production, the self-hosted Cassandra and Kafka can be
replaced with managed equivalents (e.g. DataStax AstraDB, Upstash Kafka) to reduce ops overhead.
