"""
PokéWallet catalog sync service.
Run as a standalone process: python -m sync.api_sync

Fetches card + pricing data from the PokéWallet API and writes it to the
Cassandra read model (cards_by_set) and the Postgres catalog table
(catalog_embeddings — name retained for compatibility with search queries).

Rate limit awareness (Free plan: 100 req/hour):
  - Sleeps SYNC_DELAY_SECONDS between set fetches to stay within limits.
  - Filters to English sets from the XY era onward (November 2013+).
"""
import logging
import re
import time
from datetime import date

import psycopg2
from cassandra.cluster import Cluster
from cassandra.policies import DCAwareRoundRobinPolicy

import config
from api.pokewallet import get_all_sets, get_set_cards, extract_tcgplayer_price

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

SYNC_DELAY_SECONDS    = 40
SYNC_INTERVAL_SECONDS = 86400
XY_ERA_START          = date(2013, 11, 1)
ENG                   = "eng"

MONTHS = {
    "January": 1, "February": 2, "March": 3,  "April": 4,
    "May": 5,     "June": 6,     "July": 7,    "August": 8,
    "September": 9, "October": 10, "November": 11, "December": 12,
}
DATE_RE = re.compile(r"(\d{1,2})(?:st|nd|rd|th)?\s+(\w+),?\s+(\d{4})")


def parse_release_date(raw: str | None) -> date | None:
    if not raw:
        return None
    m = DATE_RE.match(raw.strip())
    if not m:
        return None
    day, month_name, year = int(m.group(1)), m.group(2), int(m.group(3))
    month = MONTHS.get(month_name)
    if not month:
        return None
    try:
        return date(year, month, day)
    except ValueError:
        return None


def is_xy_era_or_newer(set_info: dict) -> bool:
    if (set_info.get("language") or "").lower() != ENG:
        return False
    release = parse_release_date(set_info.get("release_date"))
    return release is not None and release >= XY_ERA_START


def get_cassandra_session():
    cluster = Cluster(
        contact_points=config.CASSANDRA_HOSTS,
        port=config.CASSANDRA_PORT,
        load_balancing_policy=DCAwareRoundRobinPolicy(local_dc="datacenter1"),
    )
    return cluster.connect(config.CASSANDRA_KEYSPACE)


def get_pg_conn():
    return psycopg2.connect(config.POSTGRES_DSN)


def sync_set(cass_session, pg_conn, set_info: dict) -> int:
    set_id   = set_info.get("set_id")
    set_name = set_info.get("name") or str(set_id)
    if not set_id:
        return 0

    page         = 1
    total_synced = 0

    while True:
        data  = get_set_cards(str(set_id), page=page, limit=200)
        cards = data.get("cards") or []
        if not cards:
            break

        for card in cards:
            info      = card.get("card_info") or {}
            card_id   = card.get("id", "")
            card_name = info.get("name") or info.get("clean_name", "")
            rarity    = info.get("rarity") or "Unknown"
            card_type = info.get("card_type") or "Unknown"
            price_usd = extract_tcgplayer_price(card)

            if not card_id or not card_name:
                continue

            if price_usd is not None:
                cass_session.execute(
                    """
                    INSERT INTO cards_by_set
                      (set_name, card_id, card_name, rarity, card_type, market_price_usd, pokewallet_id)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (set_name, card_id, card_name, rarity, card_type, price_usd, card_id),
                )
            else:
                cass_session.execute(
                    """
                    INSERT INTO cards_by_set
                      (set_name, card_id, card_name, rarity, card_type, pokewallet_id)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    (set_name, card_id, card_name, rarity, card_type, card_id),
                )

            with pg_conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO catalog_embeddings
                      (pokewallet_id, card_name, set_name, rarity, card_type, market_price_usd)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON CONFLICT (pokewallet_id) DO UPDATE
                      SET market_price_usd = COALESCE(EXCLUDED.market_price_usd, catalog_embeddings.market_price_usd),
                          updated_at       = NOW()
                    """,
                    (card_id, card_name, set_name, rarity, card_type, price_usd),
                )
            pg_conn.commit()
            total_synced += 1

        pagination = data.get("pagination") or {}
        if page >= pagination.get("total_pages", 1):
            break
        page += 1

    logger.info("Synced %d cards from set '%s'", total_synced, set_name)
    return total_synced


def run_sync_pass():
    logger.info("Starting catalog sync pass...")
    cass_session = get_cassandra_session()
    pg_conn      = get_pg_conn()

    all_sets    = get_all_sets()
    target_sets = [s for s in all_sets if is_xy_era_or_newer(s)]
    logger.info(
        "Found %d total sets — syncing %d English XY-era+ sets",
        len(all_sets), len(target_sets),
    )

    total = 0
    for i, set_info in enumerate(target_sets):
        synced = sync_set(cass_session, pg_conn, set_info)
        total += synced
        if i < len(target_sets) - 1:
            logger.info("Waiting %ds before next set...", SYNC_DELAY_SECONDS)
            time.sleep(SYNC_DELAY_SECONDS)

    logger.info("Sync pass complete. Total cards synced: %d", total)


def run():
    logger.info("PokéWallet sync service started.")
    while True:
        try:
            run_sync_pass()
        except Exception as exc:
            logger.error("Sync pass failed: %s", exc)
        logger.info("Next sync in %d seconds.", SYNC_INTERVAL_SECONDS)
        time.sleep(SYNC_INTERVAL_SECONDS)


if __name__ == "__main__":
    run()
