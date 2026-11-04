"""Domain model: users (donor/NGO/admin), donations, requests, audit log."""
import secrets
import json
from datetime import datetime, date

from sqlalchemy import (
    Column, Integer, String, Boolean, Date, DateTime, ForeignKey, Text, LargeBinary, UniqueConstraint
)
from sqlalchemy.orm import relationship

from .db import Base

ROLES = ("donor", "ngo", "admin")

DONATION_STATUSES = (
    "pending_review",  # submitted by donor, awaiting admin safety review
    "approved",        # listed, visible to admin-approved NGOs
    "rejected",        # failed admin review
    "requested",       # at least one NGO request pending
    "allocated",       # an NGO request approved; collection code issued
    "collected",       # handover confirmed with the collection code
    "cancelled",       # withdrawn by the donor before allocation
    "expired",         # passed its expiry date before handover
)

REQUEST_STATUSES = ("pending", "approved", "rejected", "withdrawn", "completed")


class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True)
    name = Column(String(120), nullable=False)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    role = Column(String(16), nullable=False)
    # Demo approval: an admin clicked approve. This is NOT a real identity or registry check.
    ngo_verified = Column(Boolean, nullable=False, default=False)
    organization = Column(String(160), nullable=True)
    email_verified = Column(Boolean, nullable=False, default=False)
    city = Column(String(80), nullable=True)
    pincode = Column(String(6), nullable=True)
    darpan_id = Column(String(40), nullable=True)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)

    def public_dict(self):
        return {
            "id": self.id,
            "name": self.name,
            "email": self.email,
            "role": self.role,
            "ngo_verified": self.ngo_verified,
            "organization": self.organization,
            "email_verified": self.email_verified,
            "city": self.city,
            "pincode": self.pincode,
            "darpan_id": self.darpan_id,
        }


class NgoNeed(Base):
    """A medicine an admin-approved NGO says it needs. A request for help, not an entitlement."""
    __tablename__ = "ngo_needs"
    id = Column(Integer, primary_key=True)
    ngo_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    medicine_name = Column(String(200), nullable=False)
    quantity = Column(Integer, nullable=False)
    unit = Column(String(40), nullable=False, default="tablets")
    needed_by = Column(Date, nullable=False)
    city = Column(String(80), nullable=True)
    pincode = Column(String(6), nullable=True)
    status = Column(String(10), nullable=False, default="open", index=True)  # open / closed
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)

    ngo = relationship("User", foreign_keys=[ngo_id])

    def dict(self):
        return {
            "id": self.id, "ngo_id": self.ngo_id,
            "organization": (self.ngo.organization or self.ngo.name) if self.ngo else None,
            "medicine_name": self.medicine_name, "quantity": self.quantity, "unit": self.unit,
            "needed_by": self.needed_by.isoformat(), "city": self.city, "pincode": self.pincode,
            "status": self.status,
        }


class Donation(Base):
    __tablename__ = "donations"
    id = Column(Integer, primary_key=True)
    donor_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    medicine_name = Column(String(200), nullable=False)
    strength = Column(String(80), nullable=True)
    category = Column(String(80), nullable=False, default="general")
    quantity = Column(Integer, nullable=False)
    unit = Column(String(40), nullable=False, default="tablets")
    expiry_date = Column(Date, nullable=False)
    sealed = Column(Boolean, nullable=False, default=True)
    notes = Column(Text, nullable=True)
    pickup_city = Column(String(80), nullable=True)
    pickup_pincode = Column(String(6), nullable=True)
    batch_number = Column(String(60), nullable=True)
    manufacturer = Column(String(120), nullable=True)
    has_qr = Column(String(8), nullable=True)          # yes / no / unsure (donor self-report)
    qr_self_report = Column(Boolean, nullable=True)    # donor says a scan matched the pack (NOT verified evidence)
    need_id = Column(Integer, ForeignKey("ngo_needs.id"), nullable=True)
    flags = Column(Text, nullable=True)                # JSON list of screening flags
    status = Column(String(20), nullable=False, default="pending_review", index=True)
    rejection_reason = Column(String(300), nullable=True)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)

    donor = relationship("User", foreign_keys=[donor_id])

    def dict(self):
        return {
            "id": self.id,
            "donor_id": self.donor_id,
            "donor_name": self.donor.name if self.donor else None,
            "medicine_name": self.medicine_name,
            "strength": self.strength,
            "category": self.category,
            "quantity": self.quantity,
            "unit": self.unit,
            "expiry_date": self.expiry_date.isoformat(),
            "sealed": self.sealed,
            "notes": self.notes,
            "pickup_city": self.pickup_city,
            "pickup_pincode": self.pickup_pincode,
            "batch_number": self.batch_number,
            "manufacturer": self.manufacturer,
            "has_qr": self.has_qr,
            "qr_self_report": self.qr_self_report,
            "need_id": self.need_id,
            "flags": json.loads(self.flags) if self.flags else [],
            "status": self.status,
            "rejection_reason": self.rejection_reason,
            "created_at": self.created_at.isoformat() + "Z",
        }


class DonationRequest(Base):
    __tablename__ = "donation_requests"
    id = Column(Integer, primary_key=True)
    donation_id = Column(Integer, ForeignKey("donations.id"), nullable=False, index=True)
    ngo_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    message = Column(Text, nullable=True)
    status = Column(String(16), nullable=False, default="pending", index=True)
    # One-time handover code, issued only when the request is approved.
    collection_code = Column(String(12), nullable=True)
    failed_attempts = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    decided_at = Column(DateTime, nullable=True)

    donation = relationship("Donation", foreign_keys=[donation_id])
    ngo = relationship("User", foreign_keys=[ngo_id])

    @staticmethod
    def new_collection_code():
        return secrets.token_hex(3).upper()  # e.g. "9F3A1C"

    def dict(self, reveal_code_to=None):
        d = {
            "id": self.id,
            "donation_id": self.donation_id,
            "ngo_id": self.ngo_id,
            "ngo_name": self.ngo.name if self.ngo else None,
            "ngo_organization": self.ngo.organization if self.ngo else None,
            "message": self.message,
            "status": self.status,
            "created_at": self.created_at.isoformat() + "Z",
        }
        # The code is shown ONLY to the allocated NGO. The donor types the code the NGO
        # presents at handover, so the receiver cannot confirm its own pickup.
        if self.collection_code and reveal_code_to == "ngo":
            d["collection_code"] = self.collection_code
        return d


class AuditLog(Base):
    __tablename__ = "audit_log"
    id = Column(Integer, primary_key=True)
    actor_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    action = Column(String(80), nullable=False)
    detail = Column(String(400), nullable=True)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)


class NgoDocument(Base):
    """Registration document an NGO submits. An admin reviews it by hand.

    This is a human document check, not a lookup against any government registry.
    """
    __tablename__ = "ngo_documents"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    registration_number = Column(String(60), nullable=False)
    filename = Column(String(200), nullable=False)
    mime_type = Column(String(60), nullable=False)
    size_bytes = Column(Integer, nullable=False)
    content = Column(LargeBinary, nullable=False)
    status = Column(String(12), nullable=False, default="pending", index=True)  # pending/approved/rejected
    review_note = Column(String(300), nullable=True)
    reviewer_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    reviewed_at = Column(DateTime, nullable=True)

    user = relationship("User", foreign_keys=[user_id])

    def dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "organization": self.user.organization if self.user else None,
            "ngo_email": self.user.email if self.user else None,
            "registration_number": self.registration_number,
            "filename": self.filename,
            "mime_type": self.mime_type,
            "size_bytes": self.size_bytes,
            "status": self.status,
            "review_note": self.review_note,
            "created_at": self.created_at.isoformat() + "Z",
            "reviewed_at": self.reviewed_at.isoformat() + "Z" if self.reviewed_at else None,
        }


class AuthToken(Base):
    """Single-use email verification / password reset tokens (only the hash is stored)."""
    __tablename__ = "auth_tokens"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    purpose = Column(String(20), nullable=False)  # verify_email / reset_password
    token_hash = Column(String(64), nullable=False, unique=True)
    expires_at = Column(DateTime, nullable=False)
    used_at = Column(DateTime, nullable=True)


class Outbox(Base):
    """Development mail sink. Nothing is sent to anyone; production must use SMTP."""
    __tablename__ = "outbox"
    id = Column(Integer, primary_key=True)
    to_address = Column(String(255), nullable=False)
    subject = Column(String(200), nullable=False)
    body = Column(Text, nullable=False)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)


class Notification(Base):
    __tablename__ = "notifications"
    __table_args__ = (UniqueConstraint("user_id", "kind", "donation_id", name="uq_notification_once"),)
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    kind = Column(String(30), nullable=False)
    donation_id = Column(Integer, ForeignKey("donations.id"), nullable=True)
    message = Column(String(300), nullable=False)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    read_at = Column(DateTime, nullable=True)

    def dict(self):
        return {
            "id": self.id, "kind": self.kind, "donation_id": self.donation_id,
            "message": self.message, "created_at": self.created_at.isoformat() + "Z",
            "read": self.read_at is not None,
        }


class DonationEvent(Base):
    """Timeline of a donation from listing to handover."""
    __tablename__ = "donation_events"
    id = Column(Integer, primary_key=True)
    donation_id = Column(Integer, ForeignKey("donations.id"), nullable=False, index=True)
    label = Column(String(40), nullable=False)
    note = Column(String(300), nullable=True)
    actor_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    actor_role = Column(String(10), nullable=True)
    created_at = Column(DateTime, nullable=False)
    prev_hash = Column(String(64), nullable=True)
    hash = Column(String(64), nullable=True)
    sig = Column(String(80), nullable=True)

    def dict(self):
        return {"label": self.label, "note": self.note, "actor_role": self.actor_role,
                "created_at": self.created_at.isoformat() + "Z", "hash": self.hash}


def event(donation_id, label, actor=None, note=None):
    """Append a hash-chained, server-signed custody event for a donation.

    Tamper-evident only: whoever controls the database and signing key can rebuild
    the chain. Events carry no personal data (no names, addresses or batch numbers).
    """
    from .db import Session
    from .audit import seal
    Session.flush()
    prev = (Session.query(DonationEvent).filter_by(donation_id=donation_id)
            .order_by(DonationEvent.id.desc()).first())
    ev = DonationEvent(
        donation_id=donation_id, label=label, note=note,
        actor_id=getattr(actor, "id", None), actor_role=getattr(actor, "role", None) if actor else "system",
        created_at=datetime.utcnow().replace(microsecond=0),
        prev_hash=prev.hash if prev else None,
    )
    seal(ev)
    Session.add(ev)


def log(actor_id, action, detail=None):
    from .db import Session
    Session.add(AuditLog(actor_id=actor_id, action=action, detail=detail))


class Anchor(Base):
    """A Merkle root over custody events, optionally published to a chain.

    chain_label says WHICH chain. "local-demo" means a throwaway chain on a developer
    machine that we control, so it proves nothing to a third party.
    """
    __tablename__ = "anchors"
    id = Column(Integer, primary_key=True)
    root = Column(String(64), nullable=False)
    event_count = Column(Integer, nullable=False)
    first_event_id = Column(Integer, nullable=False)
    last_event_id = Column(Integer, nullable=False)
    chain_label = Column(String(30), nullable=True)
    chain_ref = Column(String(120), nullable=True)   # e.g. tx hash on the local demo chain
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
