"""Password hashing, JWT issue/verify, and route guards."""
import functools
from datetime import datetime, timedelta

import jwt
from flask import current_app, g, request
from werkzeug.security import check_password_hash, generate_password_hash

from .db import Session
from .errors import ApiError
from .models import User


def hash_password(plain):
    return generate_password_hash(plain)


def verify_password(pw_hash, plain):
    return check_password_hash(pw_hash, plain)


def issue_token(user):
    now = datetime.utcnow()
    payload = {
        "sub": str(user.id),
        "role": user.role,
        "iat": now,
        "exp": now + timedelta(hours=current_app.config["JWT_TTL_HOURS"]),
    }
    return jwt.encode(payload, current_app.config["JWT_SECRET"], algorithm="HS256")


def _current_user():
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise ApiError(401, "auth_required", "Sign in to continue.")
    token = header[len("Bearer "):].strip()
    try:
        payload = jwt.decode(token, current_app.config["JWT_SECRET"], algorithms=["HS256"])
    except jwt.ExpiredSignatureError:
        raise ApiError(401, "token_expired", "Your session expired. Please sign in again.")
    except jwt.InvalidTokenError:
        raise ApiError(401, "token_invalid", "That sign-in token is not valid.")
    user = Session.get(User, int(payload["sub"]))
    if user is None:
        raise ApiError(401, "token_invalid", "That account no longer exists.")
    return user


def require_auth(fn):
    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        g.user = _current_user()
        return fn(*args, **kwargs)
    return wrapper


def require_role(*roles):
    def deco(fn):
        @functools.wraps(fn)
        @require_auth
        def wrapper(*args, **kwargs):
            if g.user.role not in roles:
                raise ApiError(403, "forbidden", "You do not have access to this action.")
            return fn(*args, **kwargs)
        return wrapper
    return deco


def require_verified_email():
    """Block actions until the email is verified, when the deployment requires it."""
    if current_app.config.get("REQUIRE_EMAIL_VERIFICATION") and not g.user.email_verified:
        raise ApiError(403, "email_unverified", "Verify your email address first (check your inbox).")
