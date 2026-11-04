"""Donor donations: create, list own, cancel, browse (NGO), confirm collection."""
from flask import Blueprint, g, jsonify, request

from ..db import Session
from ..errors import ApiError
import json

from ..models import Donation, DonationRequest, NgoNeed, event, log
from ..policy import screen_donation, validate_donation_payload
from ..security import require_role, require_verified_email

bp = Blueprint("donations", __name__, url_prefix="/api/donations")


def _get_donation(donation_id):
    donation = Session.get(Donation, donation_id)
    if donation is None:
        raise ApiError(404, "not_found", "Donation not found.")
    return donation


@bp.post("")
@require_role("donor")
def create_donation():
    cleaned, problems = validate_donation_payload(request.get_json(silent=True) or {})
    if problems:
        raise ApiError(400, "validation", " ".join(problems))
    require_verified_email()
    if cleaned.get("need_id") is not None:
        need = Session.get(NgoNeed, cleaned["need_id"])
        if need is None or need.status != "open":
            raise ApiError(400, "validation", "That need is not open.")
    dup = Session.query(Donation).filter(
        Donation.donor_id == g.user.id, Donation.medicine_name == cleaned["medicine_name"],
        Donation.batch_number == cleaned["batch_number"], Donation.expiry_date == cleaned["expiry_date"],
        Donation.status.in_(("pending_review", "approved", "requested", "allocated"))).first() is not None \
        if cleaned["batch_number"] else False
    scan_fields = cleaned.pop("_scan_fields", [])
    flags = screen_donation(cleaned, existing_duplicate=dup, scan_fields=scan_fields)
    donation = Donation(donor_id=g.user.id, flags=json.dumps(flags) if flags else None, **cleaned)
    Session.add(donation)
    Session.flush()
    event(donation.id, "listed", g.user, "Listed by donor" + (f", screening flags: {len(flags)}" if flags else ""))
    log(g.user.id, "donation.created", "donation queued for review")
    Session.commit()
    return jsonify({"donation": donation.dict()}), 201


@bp.get("/mine")
@require_role("donor")
def my_donations():
    rows = (
        Session.query(Donation)
        .filter_by(donor_id=g.user.id)
        .order_by(Donation.created_at.desc())
        .all()
    )
    out = []
    for d in rows:
        item = d.dict()
        alloc = (
            Session.query(DonationRequest).filter_by(donation_id=d.id, status="approved").first()
        )
        item["allocated_to"] = (alloc.ngo.organization or alloc.ngo.name) if alloc else None
        out.append(item)
    return jsonify({"donations": out})


@bp.get("/browse")
@require_role("ngo")
def browse():
    if not g.user.ngo_verified:
        raise ApiError(403, "ngo_unverified", "Your NGO account is awaiting admin approval.")
    from datetime import date
    rows = (
        Session.query(Donation)
        .filter(Donation.status.in_(("approved", "requested")), Donation.expiry_date >= date.today())
        .order_by(Donation.expiry_date.asc())
        .all()
    )
    pin = (request.args.get("pincode") or g.user.pincode or "").strip()
    city = (request.args.get("city") or g.user.city or "").strip().lower()
    out = []
    for d in rows:
        item = d.dict()
        if pin and d.pickup_pincode == pin:
            item["match"], rank = "same PIN code", 0
        elif pin and d.pickup_pincode and d.pickup_pincode[:3] == pin[:3]:
            item["match"], rank = "nearby PIN area", 1
        elif city and (d.pickup_city or "").strip().lower() == city:
            item["match"], rank = "same city", 2
        else:
            item["match"], rank = None, 3
        out.append((rank, d.expiry_date, item))
    out.sort(key=lambda t: (t[0], t[1]))
    return jsonify({"donations": [i for _, _, i in out]})


@bp.post("/<int:donation_id>/cancel")
@require_role("donor")
def cancel_donation(donation_id):
    donation = _get_donation(donation_id)
    if donation.donor_id != g.user.id:
        raise ApiError(403, "forbidden", "You can only cancel your own donations.")
    if donation.status not in ("pending_review", "approved", "requested"):
        raise ApiError(409, "invalid_state", "This donation can no longer be cancelled.")
    donation.status = "cancelled"
    for req in donation_requests_pending(donation.id):
        req.status = "withdrawn"
    event(donation.id, "cancelled", g.user, "Withdrawn by donor")
    log(g.user.id, "donation.cancelled", f"donation {donation.id}")
    Session.commit()
    return jsonify({"donation": donation.dict()})


@bp.post("/<int:donation_id>/handover")
@require_role("donor")
def confirm_handover(donation_id):
    """The donor confirms handover by typing the code the NGO presents in person.

    Assurance: this shows the donor and the allocated NGO's representative were in
    contact and agree the handover happened. It is a donor attestation, not proof of
    physical pickup (no photo, signature or third-party check).
    """
    donation = _get_donation(donation_id)
    if donation.donor_id != g.user.id:
        raise ApiError(403, "forbidden", "Only the donor can confirm the handover.")
    req = (
        Session.query(DonationRequest)
        .filter_by(donation_id=donation.id, status="approved")
        .first()
    )
    code = ((request.get_json(silent=True) or {}).get("collection_code") or "").strip().upper()
    if req is None:
        raise ApiError(409, "invalid_state", "This donation is not awaiting handover.")
    from flask import current_app
    cap = current_app.config["MAX_HANDOVER_ATTEMPTS"]
    if (req.failed_attempts or 0) >= cap:
        raise ApiError(423, "code_locked", "Too many wrong codes. Ask the admin to issue a new code.")
    if not code or code != req.collection_code:
        req.failed_attempts = (req.failed_attempts or 0) + 1
        event(donation.id, "handover_code_failed", g.user, f"Wrong code ({req.failed_attempts}/{cap})")
        Session.commit()
        raise ApiError(400, "bad_code", "That code does not match.")
    # Atomic: a code can be redeemed once.
    done = (
        Session.query(Donation)
        .filter(Donation.id == donation.id, Donation.status == "allocated")
        .update({"status": "collected"}, synchronize_session=False)
    )
    if done != 1:
        Session.rollback()
        raise ApiError(409, "invalid_state", "This donation is not awaiting handover or was already collected.")
    req.status = "completed"
    donation.status = "collected"
    event(donation.id, "handover_attested", g.user, "Donor confirmed handover with the NGO's code (self-attested)")
    log(g.user.id, "donation.handover_confirmed", f"donation {donation.id}")
    Session.commit()
    return jsonify({"donation": donation.dict()})


def donation_requests_pending(donation_id):
    return Session.query(DonationRequest).filter_by(donation_id=donation_id, status="pending").all()
