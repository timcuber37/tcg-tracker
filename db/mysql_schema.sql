-- Poke-Collect - MySQL Write Side Schema

CREATE DATABASE IF NOT EXISTS pokemon_tcg;
USE pokemon_tcg;

CREATE TABLE users (
    user_id       CHAR(36)     NOT NULL DEFAULT (UUID()),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL DEFAULT '',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    INDEX idx_username (username)
);

-- Master catalog of all Pokemon cards
-- card_id is VARCHAR(255) to accommodate PokéWallet IDs (e.g. pk_<hex>)
CREATE TABLE cards (
    card_id       VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    set_name      VARCHAR(100) NOT NULL,
    rarity        VARCHAR(50)  NOT NULL,
    card_type     VARCHAR(50)  NOT NULL,
    description   TEXT,
    image_url     VARCHAR(255),
    pokewallet_id VARCHAR(255) NULL UNIQUE,
    PRIMARY KEY (card_id),
    INDEX idx_set (set_name),
    INDEX idx_rarity (rarity)
);

-- User collections (owns a specific card)
CREATE TABLE collections (
    collection_id CHAR(36)     NOT NULL DEFAULT (UUID()),
    user_id       CHAR(36)     NOT NULL,
    card_id       VARCHAR(255) NOT NULL,
    `condition`   ENUM('Mint', 'Near Mint', 'Excellent', 'Good', 'Poor') NOT NULL DEFAULT 'Near Mint',
    acquired_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (card_id) REFERENCES cards(card_id),
    INDEX idx_user_collection (user_id)
);

-- Binder: a user's arranged cards, one row per filled pocket.
-- UNIQUE(user_id, page_number, slot_index) enforces one card per pocket.
CREATE TABLE binder_slots (
    slot_id     CHAR(36)     NOT NULL DEFAULT (UUID()),
    user_id     CHAR(36)     NOT NULL,
    page_number INT          NOT NULL,
    slot_index  INT          NOT NULL,
    card_id     VARCHAR(255) NOT NULL,
    placed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (slot_id),
    UNIQUE KEY uq_binder_slot (user_id, page_number, slot_index),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (card_id) REFERENCES cards(card_id),
    INDEX idx_binder_user (user_id)
);

-- No seed data. Users register via the app; cards are populated by sync/api_sync.py.
