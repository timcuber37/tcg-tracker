# PokéCollect

A Pokémon Trading Card Game collection manager built around a CQRS architecture. Search the modern card catalog, build a personal collection, and track live TCGPlayer market prices.

Built as a CSC545 final project to demonstrate distributed-systems patterns: command/query separation, an event-driven write side, and multiple specialised read stores.

## Architecture

Writes and reads are separated through a Kafka event bus.

```
                        ┌──────────────┐
   user actions ──────► │    Flask     │ ──── publish ──┐
   (add, remove)        │   commands   │                ▼
                        └──────┬───────┘         ┌────────────┐
                               │ write            │   Kafka    │
                               ▼                  │   topic    │
                        ┌──────────────┐          └─────┬──────┘
                        │    MySQL     │                 │
                        │ (write side) │                 │ subscribe
                        └──────────────┘                 ▼
                                                 ┌────────────────┐
                                                 │   Cassandra    │
                                                 │  (read side)   │
                                                 │ collection_by_ │
                                                 │ user, cards_   │
                                                 │ by_set         │
                                                 └────────────────┘
                                                          ▲
                                                          │
                                                   Flask read queries

   PokéWallet API ──► sync service (every 24h) ──► Cassandra + Supabase Postgres
                      live price lookup on add  ──► Supabase Postgres
```

- **MySQL** is the authoritative write side — users, cards, and collections.
- **Kafka** carries `CardAddedToCollection` and `CardRemovedFromCollection` events to the read side.
- **Cassandra** holds denormalized read models (`collection_by_user`, `cards_by_set`) keyed for JOIN-free lookups.
- **Supabase PostgreSQL** stores the full card catalog with live TCGPlayer prices, used for search and collection pricing.
- **Supabase Auth** handles user registration and login — passwords are managed by Supabase; a shadow record is mirrored into MySQL for collection foreign keys.
- **PokéWallet API** is the source of card data and live pricing.

## Stack

- Python 3.11+, Flask 3, Gunicorn
- MySQL 8 (write side)
- Apache Cassandra 4 (read side)
- Supabase (PostgreSQL — catalog + prices, Auth — user accounts)
- Apache Kafka (event bus)
- PokéWallet REST API (card catalog + TCGPlayer prices)

## Project layout

```
app.py                    Flask entry point — registers blueprints, context processor
config.py                 Loads env vars; builds POSTGRES_DSN from POSTGRES_DSN override
                          or individual host/port/user/password vars

routes/
  query_routes.py         GET /               — home / card search (paginated, set-filtered)
                          GET /collection     — user collection view (set-filtered)
                          GET /market         — browse cards by set
                          GET /api/rare-cards — JSON list of high-rarity IDs for rain animation
  command_routes.py       POST /commands/add-from-search  — add card from catalog
                          POST /commands/add-copy         — increment copy count
                          POST /commands/remove-card      — decrement / remove
  auth_routes.py          POST /login         — validates via Supabase Auth
                          POST /register      — creates Supabase user + MySQL shadow row
                          GET  /logout
  image_routes.py         GET /card-image/<id> — proxies + disk-caches card images

commands/
  handlers.py             handle_add_from_search, handle_add_copy, handle_remove_card
  mysql_writer.py         SQLAlchemy writes; create_user mirrors Supabase UID into MySQL

queries/
  cassandra_queries.py    get_collection_by_user, get_cards_by_set, get_all_set_names
  postgres_search.py      search_catalog (paginated ILIKE), get_catalog_set_names,
                          get_current_prices, get_rare_card_ids

consumers/
  cassandra_consumer.py   Kafka consumer — writes collection_by_user read model

events/definitions.py     CardAddedToCollection, CardRemovedFromCollection dataclasses
event_bus/bus.py          KafkaProducer / KafkaConsumer factory

auth.py                   Session helpers; verify_login and create_supabase_user
                          delegate credential handling to Supabase Auth

api/pokewallet.py         PokéWallet REST client — card fetch, set fetch, image fetch,
                          TCGPlayer price extraction
sync/api_sync.py          Standalone sync process — pulls XY-era+ English sets from
                          PokéWallet once per day, upserts into Cassandra + Supabase Postgres

db/
  mysql_schema.sql        users, cards, collections tables (no seed data)
  cassandra_schema.cql    collection_by_user, cards_by_set tables
  postgres_schema.sql     catalog_embeddings table + indexes

templates/                Jinja2 templates
  base.html               Topbar (search + set dropdown slot), auth modal, profile menu
  home.html               Search results grid, pagination, high-rarity card rain animation
  collection.html         Collection grid with +/− controls and total value header
  market.html             Browse cards by set

static/
  cards/                  Disk cache for proxied card images (gitignored)

Dockerfile                Single image for all Python services; CMD defaults to gunicorn
docker-compose.yml        Full stack: mysql, zookeeper, kafka, cassandra, cassandra-init,
                          web, cassandra-consumer, sync
.env.example              Template for all required environment variables
```

## Running locally

**Prerequisites:** MySQL, Cassandra, and Kafka running locally with schemas applied. Supabase project with Auth enabled and a `POSTGRES_DSN` pointing at it.

```bash
# 1. Create and activate virtual environment
python -m venv venv
venv\Scripts\activate          # Windows
# source venv/bin/activate     # macOS / Linux

# 2. Install dependencies
pip install -r requirements.txt

# 3. Configure environment
cp .env.example .env
# Fill in MYSQL_PASSWORD, POSTGRES_DSN, SUPABASE_URL, SUPABASE_KEY,
# POKEWALLET_API_KEY, FLASK_SECRET_KEY

# 4. Apply schemas
#    MySQL:     mysql -u root -p < db/mysql_schema.sql
#    Cassandra: cqlsh -f db/cassandra_schema.cql
#    Postgres:  apply db/postgres_schema.sql via Supabase SQL Editor

# 5. Start the three processes (each in its own terminal)
python app.py                          # Flask on http://127.0.0.1:5000
python -m consumers.cassandra_consumer
python -m sync.api_sync                # syncs immediately, then every 24h
```

## Running with Docker

```bash
docker compose up --build
```

All services start in dependency order. Cassandra schema is applied automatically by the `cassandra-init` one-shot container. Supabase Postgres schema must be applied separately (see above). The `card_images` volume persists the proxy image cache across restarts.

## Features

- **Card search** — full-text search across ~10k XY-era+ English cards, paginated at 25/page. Set dropdown filters both the results and the available set list (only shows sets containing matches for the current query). Energy cards excluded.
- **Collection** — deduplication by card: duplicate copies collapse into one tile with a `×N` count and `+`/`−` controls. Set filter in the search bar scopes the view. Running total value shown in the header.
- **Live pricing** — TCGPlayer market prices fetched via `/cards/{id}` on first add, cached in Supabase Postgres. Prices persist across daily sync passes via `COALESCE` upserts.
- **Home page animation** — high-rarity cards (Special Illustration Rare, Hyper Rare, Secret Rare, Ultra Rare) rain down the home screen. A pool of 10 IDs is fetched from Postgres; images are browser-cached after first load to avoid burning API quota.
- **Auth** — register and sign in via username + password. Supabase Auth holds the credentials; a shadow row in MySQL stores the username for display and collection foreign key linkage.
- **Sync service** — pulls all English XY-era and newer sets (XY, Mega Evolution, Sun & Moon, Sword & Shield, Scarlet & Violet) from PokéWallet once per 24 hours. Respects the 100 req/hour free-tier rate limit via a 40-second delay between set requests.

## API budget

The PokéWallet free tier allows 100 req/hour and 1,000 req/day.

| Source | Requests | Notes |
|---|---|---|
| Daily sync | ~150 (1 per set) | 40s delay between sets keeps within hourly limit |
| Add to collection | 1 per unique card | Price cached in Postgres after first fetch |
| Image proxy | 1 per unique card | Cached to `static/cards/` on disk permanently |
| Rain animation | 0 ongoing | 10-card pool; images served from browser cache after first load |

## Deployment notes

The app requires persistent long-running processes (Kafka consumers, sync loop) and is not compatible with serverless platforms (Vercel, Netlify). Recommended deployment targets: **Railway**, **Render**, or **Fly.io** — all support Docker Compose and background workers.

For production, the self-hosted Cassandra and Kafka containers can be replaced with managed equivalents (DataStax AstraDB and Upstash Kafka respectively) to reduce infrastructure overhead.
