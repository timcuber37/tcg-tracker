import os
from urllib.parse import quote_plus
from dotenv import load_dotenv

load_dotenv()

# MySQL (Write Side)
MYSQL_HOST     = os.getenv("MYSQL_HOST",     "localhost")
MYSQL_PORT     = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER     = os.getenv("MYSQL_USER",     "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "")
MYSQL_DB       = os.getenv("MYSQL_DB",       "pokemon_tcg")

MYSQL_URI = (
    f"mysql+mysqlconnector://{MYSQL_USER}:{quote_plus(MYSQL_PASSWORD)}"
    f"@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DB}"
)

# Kafka Event Bus
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
KAFKA_TOPIC             = os.getenv("KAFKA_TOPIC",             "pokemon-tcg-events")

# Cassandra (Read Side)
CASSANDRA_HOSTS    = os.getenv("CASSANDRA_HOSTS", "127.0.0.1").split(",")
CASSANDRA_PORT     = int(os.getenv("CASSANDRA_PORT", "9042"))
CASSANDRA_KEYSPACE = os.getenv("CASSANDRA_KEYSPACE", "pokemon_tcg")

# PostgreSQL + pgvector (Supabase or local)
# POSTGRES_DSN may be set as a full connection string (e.g. Supabase URL).
# If not, it is built from the individual host/port/user/password/db vars.
_postgres_dsn_override = os.getenv("POSTGRES_DSN")
if _postgres_dsn_override:
    POSTGRES_DSN = _postgres_dsn_override
else:
    POSTGRES_HOST     = os.getenv("POSTGRES_HOST",     "localhost")
    POSTGRES_PORT     = int(os.getenv("POSTGRES_PORT", "5432"))
    POSTGRES_USER     = os.getenv("POSTGRES_USER",     "postgres")
    POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", "")
    POSTGRES_DB       = os.getenv("POSTGRES_DB",       "pokemon_tcg")
    POSTGRES_DSN = (
        f"host={POSTGRES_HOST} port={POSTGRES_PORT} "
        f"dbname={POSTGRES_DB} user={POSTGRES_USER} password={POSTGRES_PASSWORD}"
    )

# Supabase (Auth + Postgres)
SUPABASE_URL = os.getenv("SUPABASE_URL", "")
SUPABASE_KEY = os.getenv("SUPABASE_KEY", "")   # service role key — server-side only

# PokéWallet API
POKEWALLET_API_KEY = os.getenv("POKEWALLET_API_KEY", "")

# Flask
FLASK_SECRET_KEY = os.getenv("FLASK_SECRET_KEY", "dev-secret-change-in-prod")
FLASK_DEBUG      = os.getenv("FLASK_DEBUG", "true").lower() == "true"
