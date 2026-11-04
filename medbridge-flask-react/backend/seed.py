"""Seed fictional demo data. Safe to run repeatedly - it skips existing emails."""
from datetime import date, timedelta

import os
import sys

from medbridge import create_app
from medbridge.db import Session
from medbridge.models import Donation, User
from medbridge.security import hash_password

DEMO_PASSWORD = "MedBridge-Demo-2026"

if os.environ.get("MB_ENV", "development").lower() == "production":
    sys.exit("seed.py creates shared demo logins and is local-only. Refusing to run in production.")

app = create_app()

with app.app_context():
    def ensure_user(name, email, role, verified=False, organization=None):
        user = Session.query(User).filter_by(email=email).first()
        if user:
            return user, False
        user = User(
            name=name,
            email=email,
            password_hash=hash_password(DEMO_PASSWORD),
            role=role,
            ngo_verified=verified,
            organization=organization,
            email_verified=True,
            city="Chennai",
            pincode="600001",
        )
        Session.add(user)
        Session.commit()
        return user, True

    admin, _ = ensure_user("Demo Admin", "admin@medbridge.demo", "admin")
    donor, _ = ensure_user("Demo Donor", "donor@medbridge.demo", "donor")
    ngo, _ = ensure_user(
        "Demo NGO (Approved)", "ngo@medbridge.demo", "ngo",
        verified=True, organization="Sunrise Relief Foundation",
    )
    pending_ngo, _ = ensure_user(
        "Demo NGO (Pending)", "ngo.pending@medbridge.demo", "ngo",
        verified=False, organization="Riverbank Care Trust",
    )

    if not Session.query(Donation).filter_by(donor_id=donor.id).count():
        samples = [
            dict(medicine_name="Paracetamol 500mg", strength="500 mg", category="general",
                 quantity=100, unit="tablets", sealed=True, batch_number="DEMO-P100", manufacturer="Demo Pharma (fictional)", has_qr="no", pickup_city="Chennai", pickup_pincode="600001",
                 expiry_date=date.today() + timedelta(days=240),
                 notes="Fictional demo stock.", flags='[{"code": "qr_not_confirmed", "text": "Donor reports no QR/barcode or is unsure. This is not itself a problem (many medicines are not required to carry one) but needs a manual look."}]'),
            dict(medicine_name="Cetirizine 10mg", strength="10 mg", category="general",
                 quantity=60, unit="tablets", sealed=True, batch_number="DEMO-C060", manufacturer="Demo Pharma (fictional)", has_qr="no", pickup_city="Chennai", pickup_pincode="600002",
                 expiry_date=date.today() + timedelta(days=150),
                 notes="Fictional demo stock.", flags='[{"code": "qr_not_confirmed", "text": "Donor reports no QR/barcode or is unsure. This is not itself a problem (many medicines are not required to carry one) but needs a manual look."}]'),
        ]
        for s in samples:
            Session.add(Donation(donor_id=donor.id, **s))
        Session.commit()

    print("Demo accounts (password for all:", DEMO_PASSWORD + ")")
    for u in (admin, donor, ngo, pending_ngo):
        print(f"  {u.role:6} {u.email}")
