"""Admin: review donations, verify NGOs, decide requests, overview."""
from datetime import datetime

from flask import Blueprint, g, jsonify, request

from ..db import Session
from ..errors import ApiError
import json

from flask import Response

from ..models import Donation, DonationRequest, NgoDocument, User, event, log
from ..security import require_role

bp = Blueprint("admin", __name__, url_prefix="/api/admin")


@bp.get("/overview")
@require_role("admin")
def overview():
    q = Session.query
    return jsonify({
        "counts": {
            "donations_pending_review": q(Donation).filter_by(status="pending_review").count(),
            "donations_approved": q(Donation).filter(Donation.status.in_(("approved", "requested"))).count(),
            "requests_pending": q(DonationRequest).filter_by(status="pending").count(),
            "ngos_pending_approval": q(User).filter_by(role="ngo", ngo_verified=False).count(),
            "collected": q(Donation).filter_by(status="collected").count(),
        }
    })


@bp.get("/donations")
@require_role("admin")
def list_donations():
    status = request.args.get("status")
    query = Session.query(Donation).order_by(Donation.created_at.desc())
    if status:
        query = query.filter_by(status=status)
    return jsonify({"donations": [d.dict() for d in query.all()]})


@bp.post("/donations/<int:donation_id>/review")
@require_role("admin")
def review_donation(donation_id):
    donation = Session.get(Donation, donation_id)
    if donation is None:
        raise ApiError(404, "not_found", "Donation not found.")
    if donation.status != "pending_review":
        raise ApiError(409, "invalid_state", "This donation was already reviewed.")
    data = request.get_json(silent=True) or {}
    decision = (data.get("decision") or "").strip().lower()
    if decision == "approve":
        flags = json.loads(donation.flags) if donation.flags else []
        note = (data.get("override_note") or "").strip()
        if flags and len(note) < 5:
            raise ApiError(409, "flags_need_review",
                           "This listing has screening flags. Record what you checked in override_note to approve.")
        donation.status = "approved"
        event(donation.id, "admin_approved", g.user, ("Approved after manual review of flags" if flags else "Approved by admin"))
    elif decision == "reject":
        reason = (data.get("reason") or "").strip()
        if not reason:
            raise ApiError(400, "validation", "A reason is required when rejecting.")
        donation.status = "rejected"
        donation.rejection_reason = reason[:300]
        event(donation.id, "admin_rejected", g.user, "Rejected by admin")
    else:
        raise ApiError(400, "validation", "decision must be approve or reject.")
    log(g.user.id, f"donation.{decision}d", f"donation {donation.id}")
    Session.commit()
    return jsonify({"donation": donation.dict()})


@bp.get("/requests")
@require_role("admin")
def list_requests():
    status = request.args.get("status", "pending")
    rows = (
        Session.query(DonationRequest)
        .filter_by(status=status)
        .order_by(DonationRequest.created_at.asc())
        .all()
    )
    return jsonify({
        "requests": [
            {**r.dict(reveal_code_to=None), "donation": r.donation.dict() if r.donation else None}
            for r in rows
        ]
    })


@bp.post("/requests/<int:request_id>/decide")
@require_role("admin")
def decide_request(request_id):
    req = Session.get(DonationRequest, request_id)
    if req is None:
        raise ApiError(404, "not_found", "Request not found.")
    if req.status != "pending":
        raise ApiError(409, "invalid_state", "This request was already decided.")
    data = request.get_json(silent=True) or {}
    decision = (data.get("decision") or "").strip().lower()
    donation = Session.get(Donation, req.donation_id)
    if decision == "approve":
        if donation is None or donation.status not in ("requested", "approved"):
            raise ApiError(409, "invalid_state", "This donation is no longer available.")
        # Atomic claim: only one concurrent approval can move the donation to allocated.
        claimed = (
            Session.query(Donation)
            .filter(Donation.id == donation.id, Donation.status.in_(("requested", "approved")))
            .update({"status": "allocated"}, synchronize_session=False)
        )
        if claimed != 1:
            Session.rollback()
            raise ApiError(409, "invalid_state", "This donation was just allocated to another request.")
        req.status = "approved"
        req.collection_code = DonationRequest.new_collection_code()
        req.decided_at = datetime.utcnow()
        donation.status = "allocated"
        event(donation.id, "allocated", g.user, "Admin allocated the donation to an admin-approved NGO")
        # Every other pending request for this donation is rejected.
        others = (
            Session.query(DonationRequest)
            .filter(
                DonationRequest.donation_id == donation.id,
                DonationRequest.status == "pending",
                DonationRequest.id != req.id,
            )
            .all()
        )
        for other in others:
            other.status = "rejected"
            other.decided_at = datetime.utcnow()
    elif decision == "reject":
        req.status = "rejected"
        req.decided_at = datetime.utcnow()
        if donation and donation.status == "requested" and not (
            Session.query(DonationRequest)
            .filter_by(donation_id=donation.id, status="pending")
            .count()
        ):
            donation.status = "approved"
    else:
        raise ApiError(400, "validation", "decision must be approve or reject.")
    log(g.user.id, f"request.{decision}d", f"request {req.id}")
    Session.commit()
    return jsonify({"request": {**req.dict(reveal_code_to=None)}})


def ngo_flags(u):
    """Review-completeness hints for the admin. They are NOT a fraud verdict."""
    flags = []
    docs = Session.query(NgoDocument).filter_by(user_id=u.id).all()
    if not docs:
        flags.append("No registration document submitted yet.")
    elif not any(d.status == "approved" for d in docs):
        flags.append("No approved registration document yet.")
    if not u.darpan_id:
        flags.append("No Darpan ID entered, so the review is incomplete (not a sign of fraud).")
    for d in docs:
        dup = (Session.query(NgoDocument).filter(NgoDocument.registration_number == d.registration_number,
                                                 NgoDocument.user_id != u.id).first())
        if dup:
            flags.append(f"Registration number {d.registration_number} also appears on another NGO account.")
            break
    return flags


@bp.get("/ngos")
@require_role("admin")
def list_ngos():
    rows = Session.query(User).filter_by(role="ngo").order_by(User.created_at.desc()).all()
    return jsonify({"ngos": [{**u.public_dict(), "review_flags": ngo_flags(u)} for u in rows]})


@bp.get("/ngo-documents")
@require_role("admin")
def list_documents():
    status = request.args.get("status")
    q = Session.query(NgoDocument).order_by(NgoDocument.created_at.desc())
    if status:
        q = q.filter_by(status=status)
    return jsonify({"documents": [{**d.dict(), "review_flags": ngo_flags(d.user)} for d in q.all()]})


@bp.get("/ngo-documents/<int:doc_id>/file")
@require_role("admin")
def document_file(doc_id):
    d = Session.get(NgoDocument, doc_id)
    if d is None:
        raise ApiError(404, "not_found", "Document not found.")
    return Response(d.content, mimetype=d.mime_type, headers={
        "Content-Disposition": f'attachment; filename="{d.filename}"', "X-Content-Type-Options": "nosniff"})


@bp.post("/ngo-documents/<int:doc_id>/review")
@require_role("admin")
def review_document(doc_id):
    d = Session.get(NgoDocument, doc_id)
    if d is None:
        raise ApiError(404, "not_found", "Document not found.")
    if d.status != "pending":
        raise ApiError(409, "invalid_state", "This document was already reviewed.")
    data = request.get_json(silent=True) or {}
    decision = (data.get("decision") or "").strip().lower()
    note = (data.get("note") or "").strip()[:300] or None
    if decision not in ("approve", "reject"):
        raise ApiError(400, "validation", "decision must be approve or reject.")
    if decision == "reject" and not note:
        raise ApiError(400, "validation", "A note is required when rejecting.")
    d.status = "approved" if decision == "approve" else "rejected"
    d.review_note, d.reviewer_id, d.reviewed_at = note, g.user.id, datetime.utcnow()
    u = d.user
    if decision == "approve":
        u.ngo_verified = True   # field name is historical; UI says "admin-approved (demo)"
    elif not Session.query(NgoDocument).filter(NgoDocument.user_id == u.id, NgoDocument.status == "approved").count():
        u.ngo_verified = False
    log(g.user.id, f"ngo.document_{d.status}", f"user {u.id}")
    Session.commit()
    return jsonify({"document": d.dict()})


@bp.post("/requests/<int:request_id>/reissue-code")
@require_role("admin")
def reissue_code(request_id):
    req = Session.get(DonationRequest, request_id)
    if req is None or req.status != "approved":
        raise ApiError(409, "invalid_state", "Only an allocated request can get a new code.")
    req.collection_code = DonationRequest.new_collection_code()
    req.failed_attempts = 0
    event(req.donation_id, "code_reissued", g.user, "Admin issued a new handover code")
    log(g.user.id, "request.code_reissued", f"request {req.id}")
    Session.commit()
    return jsonify({"ok": True})
