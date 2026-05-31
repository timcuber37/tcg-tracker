from flask import Blueprint, request, render_template, redirect, url_for, jsonify
from queries.cassandra_queries import (
    get_collection_by_user,
    get_cards_by_set,
    get_all_set_names,
)
from queries.postgres_search import search_catalog, get_catalog_set_names, get_current_prices, get_rare_card_ids, PAGE_SIZE
from routes.command_routes   import _fetch_and_cache_live_price
import auth

query_bp = Blueprint("queries", __name__)


@query_bp.route("/")
def home():
    query    = request.args.get("q", "").strip()
    set_name = request.args.get("set", "").strip()
    try:
        page = max(1, int(request.args.get("page", 1)))
    except (TypeError, ValueError):
        page = 1

    results, total = ([], 0)
    if query or set_name:
        results, total = search_catalog(query=query, set_name=set_name, page=page)

    total_pages = (total + PAGE_SIZE - 1) // PAGE_SIZE if total else 0
    set_names   = get_catalog_set_names(query)
    return render_template(
        "home.html",
        query=query, set_name=set_name,
        results=results, set_names=set_names,
        page=page, total_pages=total_pages, total=total,
    )


@query_bp.route("/collection")
def collection_view():
    user_id = auth.current_user_id()
    if not user_id:
        return redirect(url_for("queries.home"))

    selected_set = request.args.get("set", "").strip()

    cards = get_collection_by_user(user_id)
    current_prices = get_current_prices([c["card_id"] for c in cards])
    for card in cards:
        cid = card["card_id"]
        price = current_prices.get(cid, card["market_price_usd"])
        if price is None:
            price = _fetch_and_cache_live_price(cid)
        card["market_price_usd"] = price

    groups: dict[str, dict] = {}
    for card in cards:
        cid = card["card_id"]
        g = groups.setdefault(cid, {
            "card_id":          cid,
            "card_name":        card["card_name"],
            "set_name":         card["set_name"],
            "rarity":           card["rarity"],
            "condition":        card["condition"],
            "market_price_usd": card["market_price_usd"],
            "count":            0,
            "collection_ids":   [],
        })
        g["count"] += 1
        g["collection_ids"].append(card["collection_id"])

    all_grouped = sorted(groups.values(), key=lambda g: (g["set_name"] or "", g["card_name"] or ""))
    collection_sets = sorted({g["set_name"] for g in all_grouped if g["set_name"]})

    grouped = [g for g in all_grouped if not selected_set or g["set_name"] == selected_set]

    total_copies   = sum(g["count"] for g in grouped)
    priced_copies  = sum(g["count"] for g in grouped if g["market_price_usd"] is not None)
    total_value    = sum(g["count"] * g["market_price_usd"] for g in grouped if g["market_price_usd"] is not None)

    return render_template(
        "collection.html",
        cards=grouped,
        collection_sets=collection_sets,
        selected_set=selected_set,
        total_copies=total_copies,
        priced_copies=priced_copies,
        total_value=total_value,
    )


@query_bp.route("/market")
def market():
    sets         = get_all_set_names()
    selected_set = request.args.get("set_name", sets[0] if sets else "")
    cards        = get_cards_by_set(selected_set) if selected_set else []
    return render_template("market.html", sets=sets, selected_set=selected_set, cards=cards)


@query_bp.route("/api/rare-cards")
def rare_cards_api():
    try:
        ids = get_rare_card_ids(60)
    except Exception:
        ids = []
    return jsonify(ids)
