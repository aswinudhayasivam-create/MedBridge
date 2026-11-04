"""NGO requests for approved donations."""
from flask import Blueprint, g, jsonify, request

from ..db import Session
from ..errors import ApiError
from ..models import Donation, DonationRequest, event, log
from ..security import require_role, require_verified_email

bp = Blueprint("requests", __name__, url_prefix="/api/requests")


@bp.post("")
@require_role("ngo")
def create_request():
    require_verified_email()
    if not g.user.ngo_verified:
        raise ApiError(403, "ngo_unverified", "Your NGO account is awaiting admin approval.")
    data = request.get_json(silent=True) or {}
    donation_id = data.get("donation_id")
    donation = Session.get(Donation, donation_id) if isinstance(donation_id, int) else None
    if donation is None:
        raise ApiError(404, "not_found", "Donation not found.")
    if donation.status not in ("approved", "requested"):
        raise ApiError(409, "invalid_state", "This donation is not open for requests.")
    existing = (
        Session.query(DonationRequest)
        .filter_by(donation_id=donation.id, ngo_id=g.user.id, status="pending")
        .first()
    )
    if existing:
        raise ApiError(409, "duplicate_request", "You already have a pending request for this donation.")
    message = (data.get("message") or "").strip() or None
    req = DonationRequest(
        donation_id=donation.id,
        ngo_id=g.user.id,
        message=message[:1000] if message else None,
    )
    donation.status = "requested"
    Session.add(req)
    event(donation.id, "ngo_requested", g.user, "An admin-approved NGO asked for this donation")
    log(g.user.id, "request.created", f"donation {donation.id}")
    Session.commit()
    return jsonify({"request": req.dict(reveal_code_to="ngo")}), 201


@bp.get("/mine")
@require_role("ngo")
def my_requests():
    rows = (
        Session.query(DonationRequest)
        .filter_by(ngo_id=g.user.id)
        .order_by(DonationRequest.created_at.desc())
        .all()
    )
    return jsonify({
        "requests": [
            {**r.dict(reveal_code_to="ngo"), "donation": r.donation.dict() if r.donation else None}
            for r in rows
        ]
    })


@bp.post("/<int:request_id>/withdraw")
@require_role("ngo")
def withdraw_request(request_id):
    req = Session.get(DonationRequest, request_id)
    if req is None:
        raise ApiError(404, "not_found", "Request not found.")
    if req.ngo_id != g.user.id:
        raise ApiError(403, "forbidden", "You can only withdraw your own requests.")
    if req.status != "pending":
        raise ApiError(409, "invalid_state", "Only a pending request can be withdrawn.")
    req.status = "withdrawn"
    donation = Session.get(Donation, req.donation_id)
    if donation and donation.status == "requested" and not (
        Session.query(DonationRequest).filter_by(donation_id=donation.id, status="pending").count()
    ):
        donation.status = "approved"
    log(g.user.id, "request.withdrawn", f"request {req.id}")
    Session.commit()
    return jsonify({"request": req.dict(reveal_code_to="ngo")})
