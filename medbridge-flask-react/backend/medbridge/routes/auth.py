"""Auth: register (donor/NGO), login, current profile."""
import hashlib
import re
import secrets
from datetime import datetime, timedelta

from flask import Blueprint, current_app, g, jsonify, request

from ..db import Session
from ..errors import ApiError
from ..models import AuthToken, User, log
from ..ratelimit import limit
from ..mailer import mail_available, send_mail
from ..security import hash_password, issue_token, require_auth, verify_password

bp = Blueprint("auth", __name__, url_prefix="/api/auth")

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def _throttle():
    limit(current_app.config["AUTH_RATE_LIMIT"], current_app.config["AUTH_RATE_WINDOW_SECONDS"])


@bp.post("/register")
def register():
    _throttle()
    if not current_app.config.get("SIGNUP_ENABLED", True):
        raise ApiError(403, "invite_only", "Sign-up is closed. This test server is for invited testers only.")
    data = request.get_json(silent=True) or {}
    name = (data.get("name") or "").strip()
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""
    role = (data.get("role") or "").strip().lower()
    organization = (data.get("organization") or "").strip() or None
    city = (data.get("city") or "").strip()[:80] or None
    pincode = (str(data.get("pincode") or "")).strip() or None
    if pincode and not re.match(r"^\d{6}$", pincode):
        raise ApiError(400, "validation", "pincode must be 6 digits.")

    if not name:
        raise ApiError(400, "validation", "name is required.")
    if not EMAIL_RE.match(email):
        raise ApiError(400, "validation", "A valid email is required.")
    if len(password) < 8:
        raise ApiError(400, "validation", "Password must be at least 8 characters.")
    if role not in ("donor", "ngo"):
        raise ApiError(400, "validation", "Role must be donor or ngo. Admin accounts are created by the team.")
    if role == "ngo" and not organization:
        raise ApiError(400, "validation", "NGOs must give their organization name.")

    if Session.query(User).filter_by(email=email).first():
        raise ApiError(409, "email_taken", "An account with this email already exists.")

    user = User(
        name=name,
        email=email,
        password_hash=hash_password(password),
        role=role,
        ngo_verified=False,
        organization=organization,
        city=city,
        pincode=pincode,
    )
    Session.add(user)
    Session.flush()
    if mail_available():
        _send_token(user, "verify_email", 24)
    log(None, "user.registered", f"{role} {email}")
    Session.commit()
    return jsonify({"token": issue_token(user), "user": user.public_dict()}), 201


@bp.post("/login")
def login():
    _throttle()
    data = request.get_json(silent=True) or {}
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""
    user = Session.query(User).filter_by(email=email).first()
    # Same error either way, so the response does not reveal which emails exist.
    if user is None or not verify_password(user.password_hash, password):
        raise ApiError(401, "bad_credentials", "Email or password is incorrect.")
    log(user.id, "user.login", None)
    Session.commit()
    return jsonify({"token": issue_token(user), "user": user.public_dict()})


@bp.get("/me")
@require_auth
def me():
    return jsonify({"user": g.user.public_dict()})


def _hash(t):
    return hashlib.sha256(t.encode()).hexdigest()


def _send_token(user, purpose, hours):
    # Invalidate older unused tokens of the same purpose.
    for old in Session.query(AuthToken).filter_by(user_id=user.id, purpose=purpose, used_at=None).all():
        old.used_at = datetime.utcnow()
    raw = secrets.token_urlsafe(32)
    Session.add(AuthToken(user_id=user.id, purpose=purpose, token_hash=_hash(raw),
                          expires_at=datetime.utcnow() + timedelta(hours=hours)))
    path = "verify-email" if purpose == "verify_email" else "reset-password"
    link = f"{current_app.config['FRONTEND_URL'].rstrip('/')}/?{path}={raw}"
    if purpose == "verify_email":
        send_mail(user.email, "Confirm your MedBridge email", f"Open this link to confirm your email:\n{link}\nIt expires in {hours} hours.")
    else:
        send_mail(user.email, "Reset your MedBridge password", f"Open this link to choose a new password:\n{link}\nIt expires in {hours} hour. If you did not ask for this, ignore this email.")


def _consume(raw, purpose):
    row = Session.query(AuthToken).filter_by(token_hash=_hash(raw or ""), purpose=purpose).first()
    if row is None or row.used_at is not None or row.expires_at < datetime.utcnow():
        raise ApiError(400, "bad_token", "This link is invalid or has expired.")
    row.used_at = datetime.utcnow()
    return Session.get(User, row.user_id)


@bp.post("/verify-email")
def verify_email():
    _throttle()
    user = _consume((request.get_json(silent=True) or {}).get("token"), "verify_email")
    user.email_verified = True
    log(user.id, "user.email_verified", None)
    Session.commit()
    return jsonify({"ok": True})


@bp.post("/resend-verification")
@require_auth
def resend_verification():
    _throttle()
    if not mail_available():
        raise ApiError(503, "mail_disabled", "Email is turned off on this server, so no message can be sent.")
    if not g.user.email_verified:
        _send_token(g.user, "verify_email", 24)
        Session.commit()
    return jsonify({"ok": True})


@bp.post("/forgot-password")
def forgot_password():
    _throttle()
    if not mail_available():
        raise ApiError(503, "mail_disabled", "Password reset by email is turned off on this server. Ask the test admin to reset it.")
    email = ((request.get_json(silent=True) or {}).get("email") or "").strip().lower()
    user = Session.query(User).filter_by(email=email).first()
    if user is not None:
        _send_token(user, "reset_password", 1)
        Session.commit()
    # Same answer whether or not the email exists.
    return jsonify({"ok": True, "message": "If that email has an account, a reset link was sent."})


@bp.post("/reset-password")
def reset_password():
    _throttle()
    data = request.get_json(silent=True) or {}
    password = data.get("password") or ""
    if len(password) < 8:
        raise ApiError(400, "validation", "Password must be at least 8 characters.")
    user = _consume(data.get("token"), "reset_password")
    user.password_hash = hash_password(password)
    user.email_verified = True  # they proved control of the inbox
    log(user.id, "user.password_reset", None)
    Session.commit()
    return jsonify({"ok": True})


@bp.get("/dev-outbox")
def dev_outbox():
    """Development only: read the mail sink, since nothing is really sent."""
    from ..models import Outbox
    if current_app.config.get("MAIL_BACKEND") != "outbox" or current_app.config.get("MB_ENV") != "development":
        raise ApiError(404, "not_found", "Resource not found.")
    rows = Session.query(Outbox).order_by(Outbox.id.desc()).limit(20).all()
    return jsonify({"mail": [{"to": r.to_address, "subject": r.subject, "body": r.body} for r in rows]})
