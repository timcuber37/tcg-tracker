import logging
from flask import session
from supabase import create_client, Client
import config
from commands.mysql_writer import get_user_by_id, get_user_by_username

logger = logging.getLogger(__name__)

_client: Client | None = None


def _supabase() -> Client:
    global _client
    if _client is None:
        if not config.SUPABASE_URL or not config.SUPABASE_KEY or config.SUPABASE_KEY.startswith("<"):
            raise RuntimeError(
                "SUPABASE_URL and SUPABASE_KEY must be set in .env. "
                "Get the service_role JWT from Supabase dashboard → Settings → API."
            )
        _client = create_client(config.SUPABASE_URL, config.SUPABASE_KEY)
    return _client


def current_user_id() -> str | None:
    return session.get("user_id")


def current_user() -> dict | None:
    uid = current_user_id()
    if not uid:
        return None
    return get_user_by_id(uid)


def verify_login(username: str, password: str) -> dict | None:
    """
    Look up the user's email via MySQL, then validate the password through
    Supabase Auth. Returns the MySQL user dict on success, None on failure.
    """
    local_user = get_user_by_username(username)
    if not local_user:
        return None
    try:
        resp = _supabase().auth.sign_in_with_password({
            "email": local_user["email"],
            "password": password,
        })
        if resp.user:
            return local_user
    except Exception as exc:
        logger.warning("Supabase sign-in failed for %s: %s", username, exc)
    return None


def create_supabase_user(email: str, password: str, username: str) -> str:
    """
    Create a user in Supabase Auth (pre-confirmed, no email verification step)
    and return the Supabase-assigned UUID.
    Raises on duplicate email or weak password.
    """
    resp = _supabase().auth.admin.create_user({
        "email": email,
        "password": password,
        "email_confirm": True,
        "user_metadata": {"username": username},
    })
    return resp.user.id


def login(user_id: str) -> None:
    session["user_id"] = user_id


def logout() -> None:
    session.pop("user_id", None)
