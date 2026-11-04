"""Configurable demonstration safety policy for donated medicines.

These rules are product behaviour for the demo, not legal or medical advice.
Real medicine redistribution is regulated; a qualified professional must
review any real-world deployment.
"""
import json
import re
from datetime import date, timedelta

from flask import current_app


PIN_RE = re.compile(r"^\d{6}$")


def validate_donation_payload(data):
    """Return (cleaned, problems). Never raises."""
    problems = []
    cleaned = {}

    name = (data.get("medicine_name") or "").strip()
    if not name:
        problems.append("medicine_name is required.")
    elif len(name) > 200:
        problems.append("medicine_name is too long (max 200 characters).")
    cleaned["medicine_name"] = name

    strength = (data.get("strength") or "").strip() or None
    if strength and len(strength) > 80:
        problems.append("strength is too long (max 80 characters).")
    cleaned["strength"] = strength

    category = (data.get("category") or "general").strip().lower()
    if category in current_app.config["BLOCKED_CATEGORIES"]:
        problems.append(
            "This category cannot be accepted through the demo platform."
        )
    cleaned["category"] = category

    try:
        quantity = int(data.get("quantity"))
        if quantity <= 0:
            problems.append("quantity must be greater than zero.")
        elif quantity > current_app.config["MAX_QUANTITY_PER_DONATION"]:
            problems.append(
                "quantity is above the per-donation limit of "
                f"{current_app.config['MAX_QUANTITY_PER_DONATION']}."
            )
    except (TypeError, ValueError):
        quantity = None
        problems.append("quantity must be a whole number.")
    cleaned["quantity"] = quantity

    unit = (data.get("unit") or "tablets").strip() or "tablets"
    cleaned["unit"] = unit[:40]

    raw_expiry = (data.get("expiry_date") or "").strip()
    try:
        expiry = date.fromisoformat(raw_expiry)
    except ValueError:
        expiry = None
        problems.append("expiry_date must be a date in YYYY-MM-DD format.")
    if expiry is not None:
        earliest = date.today() + timedelta(days=current_app.config["MIN_DAYS_TO_EXPIRY"])
        if expiry < date.today():
            problems.append("Expired medicines cannot be accepted.")
        elif expiry < earliest:
            problems.append(
                f"Medicines must have at least {current_app.config['MIN_DAYS_TO_EXPIRY']} "
                "days before expiry."
            )
    cleaned["expiry_date"] = expiry

    pin = (str(data.get("pickup_pincode") or "")).strip() or None
    if pin and not PIN_RE.match(pin):
        problems.append("pickup_pincode must be 6 digits.")
        pin = None
    cleaned["pickup_pincode"] = pin
    city = (data.get("pickup_city") or "").strip() or None
    cleaned["pickup_city"] = city[:80] if city else None
    batch = (data.get("batch_number") or "").strip() or None
    cleaned["batch_number"] = batch[:60] if batch else None
    mfr = (data.get("manufacturer") or "").strip() or None
    cleaned["manufacturer"] = mfr[:120] if mfr else None
    has_qr = (data.get("has_qr") or "").strip().lower() or None
    if has_qr not in (None, "yes", "no", "unsure"):
        problems.append("has_qr must be yes, no or unsure.")
        has_qr = None
    cleaned["has_qr"] = has_qr
    qsr = data.get("qr_self_report")
    cleaned["qr_self_report"] = bool(qsr) if has_qr == "yes" and qsr is not None else None
    need_id = data.get("need_id")
    cleaned["need_id"] = need_id if isinstance(need_id, int) else None

    allowed = {"medicine_name", "strength", "expiry_date", "batch_number", "manufacturer"}
    sf = data.get("scan_fields") or []
    sf = sorted({f for f in sf if f in allowed}) if isinstance(sf, list) else []
    if sf and data.get("scan_confirmed") is not True:
        problems.append("Confirm that you checked the photo-read details against the pack.")
    cleaned["_scan_fields"] = sf

    cleaned["sealed"] = bool(data.get("sealed", True))
    notes = (data.get("notes") or "").strip() or None
    cleaned["notes"] = notes[:2000] if notes else None
    return cleaned, problems


def screen_donation(cleaned, existing_duplicate=False, scan_fields=()):
    """Return a list of screening flags. This is SCREENING, not authentication.

    A flag means "a person must look at this before it circulates". No flag does not mean
    the medicine is genuine: nothing here checks batch numbers or manufacturers against
    any regulator's records.
    """
    flags = []

    def add(code, text):
        flags.append({"code": code, "text": text})

    if not cleaned.get("batch_number"):
        add("missing_batch", "No batch number was given, so a reviewer cannot check it against drug alerts.")
    if not cleaned.get("manufacturer"):
        add("missing_manufacturer", "No manufacturer was given.")
    if not cleaned.get("sealed"):
        add("unsealed", "Donor says the pack is not sealed.")
    if cleaned.get("has_qr") in ("no", "unsure"):
        add("qr_not_confirmed", "Donor reports no QR/barcode or is unsure. This is not itself a problem "
                                "(many medicines are not required to carry one) but needs a manual look.")
    exp = cleaned.get("expiry_date")
    if exp and exp < date.today() + timedelta(days=90):
        add("short_dated", "Expires within 90 days.")
    q = cleaned.get("quantity")
    if q and q > 500:
        add("large_quantity", "Unusually large quantity for one donor.")
    if scan_fields:
        add("scan_prefilled", "Software read these from a photo and the donor confirmed them: "
                              + ", ".join(f.replace("_", " ") for f in scan_fields) + ". Check them against the pack.")
    if existing_duplicate:
        add("possible_duplicate", "Same donor already has a listing with the same medicine, batch and expiry.")
    return flags
