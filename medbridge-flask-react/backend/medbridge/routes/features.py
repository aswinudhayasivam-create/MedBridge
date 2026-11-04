"""Needs board, notifications/expiry alerts, timeline, receipt, label scan, NGO documents."""
import io
import re
from datetime import date, datetime, timedelta

from flask import Blueprint, current_app, g, jsonify, request, send_file

from ..audit import verify_chain
from ..db import Session
from ..errors import ApiError
from ..models import (Donation, DonationEvent, DonationRequest, NgoDocument, NgoNeed,
                      Notification, User, event, log)
from ..security import require_auth, require_role, require_verified_email

bp = Blueprint("features", __name__, url_prefix="/api")

PIN_RE = re.compile(r"^\d{6}$")
OPEN_STATES = ("pending_review", "approved", "requested")


# ---------- expiry sweep + alerts ----------

def _notify(user_id, kind, donation, message):
    exists = Session.query(Notification).filter_by(user_id=user_id, kind=kind, donation_id=donation.id).first()
    if not exists:
        Session.add(Notification(user_id=user_id, kind=kind, donation_id=donation.id, message=message[:300]))


def run_expiry_sweep():
    """Lazy sweep (no background worker needed): expire lapsed listings, raise in-app alerts.
    In-app only. Nothing is emailed."""
    today = date.today()
    soon = today + timedelta(days=current_app.config["EXPIRY_ALERT_DAYS"])
    lapsed = Session.query(Donation).filter(Donation.status.in_(OPEN_STATES), Donation.expiry_date < today).all()
    for d in lapsed:
        d.status = "expired"
        for r in Session.query(DonationRequest).filter_by(donation_id=d.id, status="pending").all():
            r.status = "withdrawn"
        event(d.id, "expired", None, "Passed its expiry date before allocation")
        _notify(d.donor_id, "expired", d, f"Your listing '{d.medicine_name}' passed its expiry date and was closed.")
    near = Session.query(Donation).filter(
        Donation.status.in_(OPEN_STATES + ("allocated",)), Donation.expiry_date >= today,
        Donation.expiry_date <= soon).all()
    for d in near:
        days = (d.expiry_date - today).days
        _notify(d.donor_id, "expiring_soon", d, f"'{d.medicine_name}' expires in {days} days ({d.expiry_date}).")
        if d.status == "allocated":
            req = Session.query(DonationRequest).filter_by(donation_id=d.id, status="approved").first()
            if req:
                _notify(req.ngo_id, "expiring_soon", d, f"Allocated '{d.medicine_name}' expires in {days} days.")
    Session.commit()


@bp.get("/notifications")
@require_auth
def notifications():
    run_expiry_sweep()
    rows = (Session.query(Notification).filter_by(user_id=g.user.id)
            .order_by(Notification.created_at.desc()).limit(50).all())
    return jsonify({"notifications": [n.dict() for n in rows], "unread": sum(1 for n in rows if not n.read_at)})


@bp.post("/notifications/read-all")
@require_auth
def read_all():
    for n in Session.query(Notification).filter_by(user_id=g.user.id, read_at=None).all():
        n.read_at = datetime.utcnow()
    Session.commit()
    return jsonify({"ok": True})


# ---------- needs board ----------

@bp.get("/needs")
@require_auth
def list_needs():
    today = date.today()
    q = (Session.query(NgoNeed).join(User, User.id == NgoNeed.ngo_id)
         .filter(NgoNeed.status == "open", NgoNeed.needed_by >= today, User.ngo_verified.is_(True))
         .order_by(NgoNeed.needed_by.asc()))
    return jsonify({"needs": [n.dict() for n in q.all()]})


@bp.post("/needs")
@require_role("ngo")
def create_need():
    require_verified_email()
    if not g.user.ngo_verified:
        raise ApiError(403, "ngo_unverified", "Your NGO account is awaiting admin approval.")
    d = request.get_json(silent=True) or {}
    name = (d.get("medicine_name") or "").strip()
    try:
        qty = int(d.get("quantity"))
        needed_by = date.fromisoformat((d.get("needed_by") or "").strip())
    except (TypeError, ValueError):
        raise ApiError(400, "validation", "quantity must be a whole number and needed_by a YYYY-MM-DD date.")
    if not name or len(name) > 200:
        raise ApiError(400, "validation", "medicine_name is required (max 200 characters).")
    if qty <= 0 or qty > current_app.config["MAX_QUANTITY_PER_DONATION"] * 5:
        raise ApiError(400, "validation", "quantity is out of range.")
    if needed_by < date.today():
        raise ApiError(400, "validation", "needed_by must be today or later.")
    pin = (str(d.get("pincode") or g.user.pincode or "")).strip() or None
    if pin and not PIN_RE.match(pin):
        raise ApiError(400, "validation", "pincode must be 6 digits.")
    need = NgoNeed(ngo_id=g.user.id, medicine_name=name, quantity=qty,
                   unit=((d.get("unit") or "tablets").strip() or "tablets")[:40], needed_by=needed_by,
                   city=((d.get("city") or g.user.city or "").strip() or None), pincode=pin)
    Session.add(need)
    log(g.user.id, "need.created", "need posted")
    Session.commit()
    return jsonify({"need": need.dict()}), 201


@bp.post("/needs/<int:need_id>/close")
@require_role("ngo")
def close_need(need_id):
    need = Session.get(NgoNeed, need_id)
    if need is None or need.ngo_id != g.user.id:
        raise ApiError(404, "not_found", "Need not found.")
    need.status = "closed"
    Session.commit()
    return jsonify({"need": need.dict()})


# ---------- timeline + receipt ----------

def _can_see(donation):
    u = g.user
    if u.role == "admin" or donation.donor_id == u.id:
        return True
    if u.role == "ngo":
        return Session.query(DonationRequest).filter(
            DonationRequest.donation_id == donation.id, DonationRequest.ngo_id == u.id,
            DonationRequest.status.in_(("approved", "completed"))).first() is not None
    return False


def _donation_or_403(donation_id):
    d = Session.get(Donation, donation_id)
    if d is None or not _can_see(d):
        raise ApiError(404, "not_found", "Donation not found.")
    return d


def _events(donation_id):
    return Session.query(DonationEvent).filter_by(donation_id=donation_id).order_by(DonationEvent.id.asc()).all()


@bp.get("/donations/<int:donation_id>/timeline")
@require_auth
def timeline(donation_id):
    d = _donation_or_403(donation_id)
    evs = _events(d.id)
    return jsonify({"events": [e.dict() for e in evs], "integrity": verify_chain(evs)})


@bp.get("/donations/<int:donation_id>/receipt.pdf")
@require_auth
def receipt(donation_id):
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas
    d = _donation_or_403(donation_id)
    evs = _events(d.id)
    integrity = verify_chain(evs)
    alloc = Session.query(DonationRequest).filter(
        DonationRequest.donation_id == d.id, DonationRequest.status.in_(("approved", "completed"))).first()
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    w, h = A4
    y = h - 60

    def line(text, size=10, bold=False, gap=15):
        nonlocal y
        c.setFont("Helvetica-Bold" if bold else "Helvetica", size)
        c.drawString(50, y, text[:105])
        y -= gap

    line("MedBridge handover record (demo)", 16, True, 26)
    line(f"Donation #{d.id}: {d.medicine_name} {d.strength or ''}, {d.quantity} {d.unit}")
    line(f"Expiry: {d.expiry_date}   Batch: {d.batch_number or 'not given'}   Status: {d.status}")
    line(f"Allocated NGO (admin-approved, demo): {(alloc.ngo.organization or alloc.ngo.name) if alloc else 'none'}", gap=22)
    line("Timeline", 12, True, 20)
    for e in evs:
        line(f"{e.created_at.isoformat()}Z  {e.label}  ({e.actor_role or 'system'})  {e.hash[:12]}")
        if y < 120:
            c.showPage(); y = h - 60
    y -= 8
    line("Chain integrity check at print time: " + ("OK" if integrity["ok"] else "FAILED - " + str(integrity["problem"])), 10, True)
    for t in ("This is a record, not proof of pickup. The handover is the donor's own confirmation.",
              "Hash-chained and server-signed: shows later edits, but whoever runs the server could rebuild it.",
              "It does not show the medicine is genuine or the NGO is legitimate. Admin approval is a demo check.",
              "Demo only. Not for real medicine redistribution without qualified legal and pharmacy review."):
        line(t, 8, gap=12)
    c.save()
    buf.seek(0)
    return send_file(buf, mimetype="application/pdf", download_name=f"medbridge-donation-{d.id}.pdf")


# ---------- label scan (suggestions only) ----------

def _is_image(b):
    return b[:8] == b"\x89PNG\r\n\x1a\n" or b[:3] == b"\xff\xd8\xff"


@bp.post("/donations/scan")
@require_role("donor")
def scan_label():
    f = request.files.get("image")
    if f is None:
        raise ApiError(400, "validation", "Attach a label photo as 'image'.")
    data = f.read(current_app.config["MAX_DOCUMENT_BYTES"] + 1)
    if len(data) > current_app.config["MAX_DOCUMENT_BYTES"]:
        raise ApiError(413, "too_large", "Image is too large (max 2 MB).")
    if not _is_image(data):
        raise ApiError(400, "validation", "Only PNG or JPEG images are accepted.")
    from .. import ocr
    try:
        text = ocr.ocr_bytes(data)
    except RuntimeError as e:
        if str(e) == "not_installed":
            raise ApiError(503, "ocr_unavailable", "Label scanning is not available on this server. Type the details instead.")
        raise ApiError(502, "ocr_failed", "Could not read that image. Try a clearer photo or type the details.")
    return jsonify({
        "suggestions": ocr.parse_label(text),
        "notice": "Read automatically and may be wrong. Check every field. This does not show the medicine is genuine.",
    })


# ---------- NGO documents ----------

@bp.post("/ngo/documents")
@require_role("ngo")
def upload_document():
    reg = (request.form.get("registration_number") or "").strip()
    f = request.files.get("document")
    if not reg or len(reg) > 60:
        raise ApiError(400, "validation", "registration_number is required (max 60 characters).")
    if f is None:
        raise ApiError(400, "validation", "Attach the registration document as 'document'.")
    data = f.read(current_app.config["MAX_DOCUMENT_BYTES"] + 1)
    if len(data) > current_app.config["MAX_DOCUMENT_BYTES"]:
        raise ApiError(413, "too_large", "Document is too large (max 2 MB).")
    if data[:5] == b"%PDF-":
        mime = "application/pdf"
    elif _is_image(data):
        mime = "image/png" if data[:4] == b"\x89PNG" else "image/jpeg"
    else:
        raise ApiError(400, "validation", "Only PDF, PNG or JPEG files are accepted.")
    from sqlalchemy import func
    n, total = Session.query(func.count(NgoDocument.id), func.coalesce(func.sum(NgoDocument.size_bytes), 0)).filter_by(user_id=g.user.id).one()
    cfg = current_app.config
    if n >= cfg["MAX_DOCS_PER_NGO"] or int(total) + len(data) > cfg["MAX_DOC_BYTES_PER_NGO"]:
        raise ApiError(413, "document_quota", f"Document limit reached ({cfg['MAX_DOCS_PER_NGO']} files, {cfg['MAX_DOC_BYTES_PER_NGO'] // (1024*1024)} MB in total). Ask the test admin.")
    name = re.sub(r"[^A-Za-z0-9._ -]", "_", (f.filename or "document"))[:150]
    doc = NgoDocument(user_id=g.user.id, registration_number=reg, filename=name, mime_type=mime,
                      size_bytes=len(data), content=data)
    darpan = (request.form.get("darpan_id") or "").strip()
    if darpan:
        g.user.darpan_id = darpan[:40]
    Session.add(doc)
    log(g.user.id, "ngo.document_submitted", "registration document submitted")
    Session.commit()
    return jsonify({"document": doc.dict()}), 201


@bp.get("/ngo/documents")
@require_role("ngo")
def my_documents():
    rows = Session.query(NgoDocument).filter_by(user_id=g.user.id).order_by(NgoDocument.created_at.desc()).all()
    return jsonify({"documents": [d.dict() for d in rows]})


# ---------- audit anchors (admin) ----------

@bp.get("/admin/anchors")
@require_role("admin")
def list_anchors():
    from ..models import Anchor
    rows = Session.query(Anchor).order_by(Anchor.id.desc()).limit(50).all()
    return jsonify({"anchors": [{
        "id": a.id, "root": a.root, "event_count": a.event_count, "chain_label": a.chain_label or "not published",
        "chain_ref": a.chain_ref, "created_at": a.created_at.isoformat() + "Z"} for a in rows]})


@bp.post("/admin/anchors/<int:anchor_id>/check")
@require_role("admin")
def check_anchor(anchor_id):
    """Recompute the Merkle root from stored events and compare. Detects edits after anchoring."""
    from ..audit import merkle_root
    from ..models import Anchor
    a = Session.get(Anchor, anchor_id)
    if a is None:
        raise ApiError(404, "not_found", "Anchor not found.")
    evs = (Session.query(DonationEvent).filter(DonationEvent.id >= a.first_event_id, DonationEvent.id <= a.last_event_id)
           .order_by(DonationEvent.id.asc()).all())
    from ..audit import canonical_hash
    rehashed_ok = all(canonical_hash(e) == e.hash for e in evs)
    root_ok = len(evs) == a.event_count and merkle_root([e.hash for e in evs]) == a.root
    return jsonify({"matches": bool(rehashed_ok and root_ok), "events_checked": len(evs),
                    "note": "Matches means stored events are unchanged since anchoring (to the extent the anchor itself is trustworthy)."
                            + (" This anchor is on a local demo chain we control." if (a.chain_label == "local-demo") else "")})
