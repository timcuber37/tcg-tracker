-- Poke-Collect - Postgres Schema
-- Runs on Supabase or a local Postgres instance.

-- Populated by sync/api_sync.py from the PokéWallet API.
-- Used for card search, set filtering, and price display.
CREATE TABLE IF NOT EXISTS catalog_embeddings (
    id               SERIAL PRIMARY KEY,
    pokewallet_id    TEXT        NOT NULL UNIQUE,
    card_name        TEXT        NOT NULL,
    set_name         TEXT        NOT NULL,
    rarity           TEXT        NOT NULL,
    card_type        TEXT        NOT NULL,
    market_price_usd DECIMAL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS catalog_embeddings_card_name_idx ON catalog_embeddings (card_name);
CREATE INDEX IF NOT EXISTS catalog_embeddings_set_name_idx  ON catalog_embeddings (set_name);
CREATE INDEX IF NOT EXISTS catalog_embeddings_rarity_idx    ON catalog_embeddings (rarity);
