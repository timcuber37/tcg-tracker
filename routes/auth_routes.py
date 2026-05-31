import re
import logging
from flask import Blueprint, request, redirect, url_for
from sqlalchemy.exc import IntegrityError
import auth
from commands.mysql_writer import create_user

logger = logging.getLogger(__name__)
auth_bp = Blueprint("auth", __name__)

_USERNAME_RE = re.compile(r'^[A-Za-z0-9_]{3,30}$')


def _redirect_with_error(error: str, tab: str, next_url: str):
    base = next_url if next_url.startswith("/") else "/"
    sep = "&" if "?" in base else "?"
    return redirect(f"{base}{sep}auth_error={error}&auth_tab={tab}")


@auth_bp.route("/login", methods=["POST"])
def login():
    username = request.form.get("username", "").strip()
    password = request.form.get("password", "")
    next_url = request.form.get("next", "/")

    user = auth.verify_login(username, password)
    if not user:
        return _redirect_with_error("Invalid+username+or+password", "signin", next_url)

    auth.login(user["user_id"])
    return redirect(next_url if next_url.startswith("/") else "/")


@auth_bp.route("/register", methods=["POST"])
def register():
    username = request.form.get("username", "").strip()
    email    = request.form.get("email", "").strip()
    password = request.form.get("password", "")
    confirm  = request.form.get("confirm_password", "")
    next_url = request.form.get("next", "/")

    if not _USERNAME_RE.match(username):
        return _redirect_with_error("Username+must+be+3-30+alphanumeric+characters", "register", next_url)
    if not re.match(r'^[^@\s]+@[^@\s]+\.[^@\s]+$', email):
        return _redirect_with_error("Invalid+email+address", "register", next_url)
    if len(password) < 8:
        return _redirect_with_error("Password+must+be+at+least+8+characters", "register", next_url)
    if password != confirm:
        return _redirect_with_error("Passwords+do+not+match", "register", next_url)

    # Create the user in Supabase Auth first — this is the source of truth for credentials.
    try:
        supabase_uid = auth.create_supabase_user(email, password, username)
    except Exception as exc:
        msg = str(exc)
        if "already registered" in msg.lower() or "already been registered" in msg.lower():
            return _redirect_with_error("Email+already+registered", "register", next_url)
        logger.error("Supabase user creation failed: %s", exc)
        return _redirect_with_error("Registration+failed,+please+try+again", "register", next_url)

    # Mirror the user into MySQL so the rest of the app (collections, Cassandra)
    # can reference a local user_id = the Supabase UUID.
    try:
        create_user(supabase_uid, username, email, password_hash="")
    except IntegrityError:
        return _redirect_with_error("Username+or+email+already+taken", "register", next_url)

    auth.login(supabase_uid)
    return redirect(next_url if next_url.startswith("/") else "/")


@auth_bp.route("/logout", methods=["POST", "GET"])
def logout():
    auth.logout()
    return redirect(url_for("queries.home"))
