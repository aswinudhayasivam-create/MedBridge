"""Create an invited tester account (staging). Run by the operator, never exposed over HTTP.

  python provision.py --email a@b.c --name "Name" --role donor|ngo|admin [--organization "Org"] [--city X --pincode 600001]

Prints a one-time random password. Hand it over privately. Marks email as verified because
no email is sent in staging; this records that the OPERATOR vouched for the tester.
"""
import argparse
import secrets

from medbridge import create_app
from medbridge.db import Session
from medbridge.models import User, log
from medbridge.security import hash_password

ap = argparse.ArgumentParser()
ap.add_argument("--email", required=True); ap.add_argument("--name", required=True)
ap.add_argument("--role", required=True, choices=["donor", "ngo", "admin"])
ap.add_argument("--organization"); ap.add_argument("--city"); ap.add_argument("--pincode")
a = ap.parse_args()
app = create_app()
with app.app_context():
    email = a.email.strip().lower()
    if Session.query(User).filter_by(email=email).first():
        raise SystemExit("account already exists")
    if a.role == "ngo" and not a.organization:
        raise SystemExit("--organization is required for ngo")
    pw = secrets.token_urlsafe(12)
    u = User(name=a.name, email=email, password_hash=hash_password(pw), role=a.role, ngo_verified=False,
             organization=a.organization, city=a.city, pincode=a.pincode, email_verified=True)
    Session.add(u); Session.flush()
    log(u.id, "user.provisioned", f"{a.role} invited by operator")
    Session.commit()
    print(f"created {a.role} {email}\none-time password: {pw}")
