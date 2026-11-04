import io
import json
from datetime import date, timedelta

from conftest import approve_ngo, auth_header, future_expiry, make_admin, register
from medbridge.db import Session
from medbridge.models import (Donation, DonationEvent, DonationRequest, Notification, Outbox, User)
from test_api import donor_token, make_donation, verified_ngo_token


def outbox_token(purpose_word):
    body = Session.query(Outbox).order_by(Outbox.id.desc()).first().body
    return body.split(f"{purpose_word}=")[1].split()[0]


# ----- email verification and password reset -----

def test_verify_email_flow_single_use(client):
    res = register(client, "v@test.dev")
    assert res.get_json()["user"]["email_verified"] is False
    tok = outbox_token("verify-email")
    assert client.post("/api/auth/verify-email", json={"token": tok}).status_code == 200
    assert client.post("/api/auth/verify-email", json={"token": tok}).status_code == 400  # single use
    me = client.get("/api/auth/me", headers=auth_header(res.get_json()["token"])).get_json()["user"]
    assert me["email_verified"] is True
    assert client.post("/api/auth/verify-email", json={"token": "nonsense"}).status_code == 400


def test_unverified_blocked_when_required(app, client):
    app.config["REQUIRE_EMAIL_VERIFICATION"] = True
    token = register(client, "u@test.dev").get_json()["token"]
    res = make_donation(client, token)
    assert res.status_code == 403 and res.get_json()["error"]["code"] == "email_unverified"
    client.post("/api/auth/verify-email", json={"token": outbox_token("verify-email")})
    assert make_donation(client, token).status_code == 201


def test_password_reset_flow(client):
    register(client, "r@test.dev", password="oldpassword1")
    a = client.post("/api/auth/forgot-password", json={"email": "r@test.dev"}).get_json()
    b = client.post("/api/auth/forgot-password", json={"email": "ghost@test.dev"}).get_json()
    assert a == b  # no account enumeration
    assert Session.query(Outbox).filter_by(to_address="ghost@test.dev").count() == 0
    tok = outbox_token("reset-password")
    assert client.post("/api/auth/reset-password", json={"token": tok, "password": "short"}).status_code == 400
    assert client.post("/api/auth/reset-password", json={"token": tok, "password": "newpassword1"}).status_code == 200
    assert client.post("/api/auth/reset-password", json={"token": tok, "password": "another-pass1"}).status_code == 400
    assert client.post("/api/auth/login", json={"email": "r@test.dev", "password": "oldpassword1"}).status_code == 401
    assert client.post("/api/auth/login", json={"email": "r@test.dev", "password": "newpassword1"}).status_code == 200


def test_reset_token_cannot_verify_email(client):
    register(client, "x@test.dev")
    client.post("/api/auth/forgot-password", json={"email": "x@test.dev"})
    tok = outbox_token("reset-password")
    assert client.post("/api/auth/verify-email", json={"token": tok}).status_code == 400


# ----- NGO documents -----

def test_ngo_cannot_self_approve_and_needs_document(client):
    admin = make_admin(client)
    r = register(client, "n@test.dev", role="ngo", organization="Org").get_json()
    h = auth_header(r["token"])
    assert client.get("/api/donations/browse", headers=h).status_code == 403
    assert client.post(f"/api/admin/ngos/{r['user']['id']}/verify", headers=auth_header(admin)).status_code in (404, 405)
    bad = client.post("/api/ngo/documents", headers=h, content_type="multipart/form-data",
                      data={"registration_number": "R1", "document": (io.BytesIO(b"MZ not a pdf"), "x.exe")})
    assert bad.status_code == 400
    approve_ngo(client, admin, r["user"]["id"], r["token"], reg="R1")
    assert client.get("/api/donations/browse", headers=h).status_code == 200


def test_document_review_rules_and_flags(client):
    admin = make_admin(client)
    a = register(client, "a@test.dev", role="ngo", organization="A").get_json()
    b = register(client, "b@test.dev", role="ngo", organization="B").get_json()
    for r in (a, b):
        client.post("/api/ngo/documents", headers=auth_header(r["token"]), content_type="multipart/form-data",
                    data={"registration_number": "SAME-1", "document": (io.BytesIO(b"%PDF-1.4 x"), "d.pdf")})
    docs = client.get("/api/admin/ngo-documents", headers=auth_header(admin)).get_json()["documents"]
    assert len(docs) == 2
    flags = " ".join(docs[0]["review_flags"])
    assert "also appears on another NGO" in flags and "Darpan" in flags
    # a rejection needs a note; a donor/NGO cannot review
    assert client.post(f"/api/admin/ngo-documents/{docs[0]['id']}/review", json={"decision": "reject"},
                       headers=auth_header(admin)).status_code == 400
    assert client.post(f"/api/admin/ngo-documents/{docs[0]['id']}/review", json={"decision": "approve"},
                       headers=auth_header(a["token"])).status_code == 403
    f = client.get(f"/api/admin/ngo-documents/{docs[0]['id']}/file", headers=auth_header(admin))
    assert f.status_code == 200 and f.headers["X-Content-Type-Options"] == "nosniff"
    assert client.get(f"/api/admin/ngo-documents/{docs[0]['id']}/file", headers=auth_header(a["token"])).status_code == 403


def test_oversize_document_rejected(app, client):
    app.config["MAX_DOCUMENT_BYTES"] = 100
    r = register(client, "o@test.dev", role="ngo", organization="O").get_json()
    res = client.post("/api/ngo/documents", headers=auth_header(r["token"]), content_type="multipart/form-data",
                      data={"registration_number": "R", "document": (io.BytesIO(b"%PDF-" + b"0" * 500), "d.pdf")})
    assert res.status_code == 413


# ----- screening flags -----

def test_flagged_listing_needs_override_note(client):
    admin = make_admin(client)
    donor = donor_token(client)
    d = make_donation(client, donor, batch_number="", manufacturer="").get_json()["donation"]
    codes = {f["code"] for f in d["flags"]}
    assert {"missing_batch", "missing_manufacturer"} <= codes
    h = auth_header(admin)
    r = client.post(f"/api/admin/donations/{d['id']}/review", json={"decision": "approve"}, headers=h)
    assert r.status_code == 409 and r.get_json()["error"]["code"] == "flags_need_review"
    ok = client.post(f"/api/admin/donations/{d['id']}/review",
                     json={"decision": "approve", "override_note": "Checked pack photo in person"}, headers=h)
    assert ok.status_code == 200


def test_clean_listing_has_no_flags_and_no_qr_is_not_mandatory(client):
    donor = donor_token(client)
    clean = make_donation(client, donor).get_json()["donation"]
    assert clean["flags"] == []
    noqr = make_donation(client, donor, batch_number="Z9", has_qr="no").get_json()["donation"]
    assert [f["code"] for f in noqr["flags"]] == ["qr_not_confirmed"]


def test_duplicate_and_short_dated_flags(client):
    donor = donor_token(client)
    make_donation(client, donor)
    again = make_donation(client, donor).get_json()["donation"]
    assert "possible_duplicate" in {f["code"] for f in again["flags"]}
    short = make_donation(client, donor, batch_number="S1", expiry_date=future_expiry(100)).get_json()["donation"]
    assert "short_dated" not in {f["code"] for f in short["flags"]}


# ----- timeline, hash chain, receipt -----

def run_to_handover(client):
    admin = make_admin(client)
    donor = donor_token(client)
    ngo = verified_ngo_token(client, admin)
    d = make_donation(client, donor).get_json()["donation"]
    client.post(f"/api/admin/donations/{d['id']}/review", json={"decision": "approve"}, headers=auth_header(admin))
    rq = client.post("/api/requests", json={"donation_id": d["id"]}, headers=auth_header(ngo)).get_json()["request"]
    client.post(f"/api/admin/requests/{rq['id']}/decide", json={"decision": "approve"}, headers=auth_header(admin))
    code = client.get("/api/requests/mine", headers=auth_header(ngo)).get_json()["requests"][0]["collection_code"]
    return admin, donor, ngo, d["id"], rq["id"], code


def test_timeline_chain_and_tamper_detection(client):
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    client.post(f"/api/donations/{did}/handover", json={"collection_code": code}, headers=auth_header(donor))
    t = client.get(f"/api/donations/{did}/timeline", headers=auth_header(donor)).get_json()
    labels = [e["label"] for e in t["events"]]
    assert labels == ["listed", "admin_approved", "ngo_requested", "allocated", "handover_attested"]
    assert t["integrity"]["ok"] is True
    # tamper with a stored event: the check must fail
    ev = Session.query(DonationEvent).filter_by(donation_id=did, label="admin_approved").first()
    ev.note = "edited later"
    Session.commit()
    t2 = client.get(f"/api/donations/{did}/timeline", headers=auth_header(donor)).get_json()
    assert t2["integrity"]["ok"] is False and "content" in t2["integrity"]["problem"]


def test_chain_detects_deletion_and_resigning_with_wrong_key(app, client):
    from medbridge.audit import seal, verify_chain
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    evs = Session.query(DonationEvent).filter_by(donation_id=did).order_by(DonationEvent.id).all()
    with app.app_context():
        assert verify_chain(evs)["ok"]
        assert not verify_chain(evs[:1] + evs[2:])["ok"]  # deleted middle event breaks the link
        evs[1].sig = "dev." + "0" * 64
        assert "signature" in verify_chain(evs)["problem"]
        Session.rollback()


def test_signing_key_rotation(app, client):
    from medbridge.audit import verify_chain
    app.config["AUDIT_SIGNING_KEY"] = "k1:" + "a" * 30
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    evs = Session.query(DonationEvent).filter_by(donation_id=did).order_by(DonationEvent.id).all()
    with app.app_context():
        app.config["AUDIT_SIGNING_KEY"] = "k2:" + "b" * 30 + ",k1:" + "a" * 30   # rotated, old key kept
        assert verify_chain(evs)["ok"]
        app.config["AUDIT_SIGNING_KEY"] = "k2:" + "b" * 30                       # old key dropped
        assert "unknown signing key" in verify_chain(evs)["problem"]


def test_timeline_and_receipt_access_control(client):
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    other = donor_token(client, "other@test.dev")
    assert client.get(f"/api/donations/{did}/timeline", headers=auth_header(other)).status_code == 404
    assert client.get(f"/api/donations/{did}/receipt.pdf", headers=auth_header(other)).status_code == 404
    assert client.get(f"/api/donations/{did}/receipt.pdf", headers=auth_header(ngo)).status_code == 200
    pdf = client.get(f"/api/donations/{did}/receipt.pdf", headers=auth_header(donor))
    assert pdf.status_code == 200 and pdf.data[:5] == b"%PDF-"


def test_events_hold_no_personal_data(client):
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    for e in Session.query(DonationEvent).filter_by(donation_id=did).all():
        blob = json.dumps(e.dict())
        for secret in ("donor@test.dev", "ngo@test.dev", "BN1234", code):
            assert secret not in blob


# ----- handover attempt limit -----

def test_handover_attempt_limit_and_reissue(client):
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    h = auth_header(donor)
    for _ in range(5):
        assert client.post(f"/api/donations/{did}/handover", json={"collection_code": "WRONG1"}, headers=h).status_code == 400
    locked = client.post(f"/api/donations/{did}/handover", json={"collection_code": code}, headers=h)
    assert locked.status_code == 423  # even the right code is refused once locked
    assert client.post(f"/api/admin/requests/{rid}/reissue-code", headers=auth_header(admin)).status_code == 200
    new = client.get("/api/requests/mine", headers=auth_header(ngo)).get_json()["requests"][0]["collection_code"]
    assert new != code
    assert client.post(f"/api/donations/{did}/handover", json={"collection_code": new}, headers=h).status_code == 200


# ----- needs board -----

def test_needs_board(client):
    admin = make_admin(client)
    ngo = verified_ngo_token(client, admin)
    donor = donor_token(client)
    body = {"medicine_name": "Amoxicillin 250mg", "quantity": 200, "needed_by": future_expiry(30), "pincode": "600001"}
    pending_ngo = register(client, "p@test.dev", role="ngo", organization="Pending").get_json()["token"]
    assert client.post("/api/needs", json=body, headers=auth_header(pending_ngo)).status_code == 403
    assert client.post("/api/needs", json={**body, "needed_by": "2020-01-01"}, headers=auth_header(ngo)).status_code == 400
    n = client.post("/api/needs", json=body, headers=auth_header(ngo)).get_json()["need"]
    needs = client.get("/api/needs", headers=auth_header(donor)).get_json()["needs"]
    assert [x["id"] for x in needs] == [n["id"]]
    d = make_donation(client, donor, need_id=n["id"]).get_json()["donation"]
    assert d["need_id"] == n["id"]  # still goes through admin review, not auto-allocated
    assert d["status"] == "pending_review"
    assert make_donation(client, donor, need_id=9999).status_code == 400
    client.post(f"/api/needs/{n['id']}/close", headers=auth_header(ngo))
    assert client.get("/api/needs", headers=auth_header(donor)).get_json()["needs"] == []


def test_only_owner_closes_need(client):
    admin = make_admin(client)
    ngo = verified_ngo_token(client, admin)
    other = verified_ngo_token(client, admin, email="o2@test.dev")
    n = client.post("/api/needs", json={"medicine_name": "X", "quantity": 5, "needed_by": future_expiry(5)},
                    headers=auth_header(ngo)).get_json()["need"]
    assert client.post(f"/api/needs/{n['id']}/close", headers=auth_header(other)).status_code == 404


# ----- nearby matching -----

def test_nearby_ordering(client):
    admin = make_admin(client)
    ngo = register(client, "geo@test.dev", role="ngo", organization="Geo", city="Chennai", pincode="600001").get_json()
    approve_ngo(client, admin, ngo["user"]["id"], ngo["token"], reg="G1")
    donor = donor_token(client)
    spots = [("Far", "Delhi", "110001"), ("City", "Chennai", "700099"), ("Near", "Chennai", "600002"),
             ("Same", "Chennai", "600001")]
    for i, (name, city, pin) in enumerate(spots):
        d = make_donation(client, donor, medicine_name=name, batch_number=f"B{i}", pickup_city=city,
                          pickup_pincode=pin).get_json()["donation"]
        client.post(f"/api/admin/donations/{d['id']}/review", json={"decision": "approve"}, headers=auth_header(admin))
    out = client.get("/api/donations/browse", headers=auth_header(ngo["token"])).get_json()["donations"]
    assert [x["medicine_name"] for x in out] == ["Same", "Near", "City", "Far"]
    assert [x["match"] for x in out] == ["same PIN code", "nearby PIN area", "same city", None]
    assert make_donation(client, donor, pickup_pincode="12ab").status_code == 400


# ----- expiry alerts -----

def test_expiry_alerts_and_sweep(client):
    admin = make_admin(client)
    donor = donor_token(client)
    d = make_donation(client, donor, expiry_date=future_expiry(100)).get_json()["donation"]
    client.post(f"/api/admin/donations/{d['id']}/review", json={"decision": "approve"}, headers=auth_header(admin))
    h = auth_header(donor)
    assert client.get("/api/notifications", headers=h).get_json()["notifications"] == []
    row = Session.get(Donation, d["id"])
    row.expiry_date = date.today() + timedelta(days=10)
    Session.commit()
    n = client.get("/api/notifications", headers=h).get_json()
    assert n["unread"] == 1 and "expires in 10 days" in n["notifications"][0]["message"]
    client.get("/api/notifications", headers=h)  # idempotent: no duplicate alert
    assert Session.query(Notification).count() == 1
    row = Session.get(Donation, d["id"])
    row.expiry_date = date.today() - timedelta(days=1)
    Session.commit()
    client.get("/api/notifications", headers=h)
    assert Session.get(Donation, d["id"]).status == "expired"
    kinds = {x.kind for x in Session.query(Notification).all()}
    assert "expired" in kinds
    # expired listings are not visible to NGOs
    ngo = verified_ngo_token(client, admin)
    assert client.get("/api/donations/browse", headers=auth_header(ngo)).get_json()["donations"] == []
    assert client.post("/api/auth/login", json={"email": "donor@test.dev", "password": "password123"}).status_code == 200
    client.post("/api/notifications/read-all", headers=h)
    assert client.get("/api/notifications", headers=h).get_json()["unread"] == 0


# ----- OCR parsing (pure) and endpoint -----

def test_parse_label_suggestions():
    from medbridge.ocr import parse_label
    text = "CROCIN ADVANCE\nParacetamol 500 mg Tablets\nB.No: ABC1234\nMfd by: Test Pharma Pvt Ltd\nEXP 03/2028\nMRP Rs 30"
    p = parse_label(text)
    assert p["expiry_date"] == "2028-03-31"
    assert p["batch_number"] == "ABC1234"
    assert p["manufacturer"].startswith("Test Pharma")
    assert p["strength"] == "500mg"
    assert p["medicine_name"] == "CROCIN ADVANCE"
    assert parse_label("") == {}
    assert "expiry_date" not in parse_label("EXP 13/2028")  # impossible month is ignored


def test_scan_endpoint_validation_and_no_storage(client, monkeypatch):
    donor = donor_token(client)
    h = auth_header(donor)
    assert client.post("/api/donations/scan", headers=h).status_code == 400
    bad = client.post("/api/donations/scan", headers=h, content_type="multipart/form-data",
                      data={"image": (io.BytesIO(b"GIF89a nope"), "a.gif")})
    assert bad.status_code == 400
    from medbridge import ocr
    monkeypatch.setattr(ocr, "ocr_bytes", lambda b: "EXP 12/2030\nBatch: Q1W2E3")
    png = b"\x89PNG\r\n\x1a\n" + b"0" * 20
    ok = client.post("/api/donations/scan", headers=h, content_type="multipart/form-data",
                     data={"image": (io.BytesIO(png), "a.png")})
    assert ok.status_code == 200
    body = ok.get_json()
    assert body["suggestions"]["expiry_date"] == "2030-12-31" and "may be wrong" in body["notice"]
    assert Session.query(Donation).count() == 0  # a scan never creates a listing
    other = client.post("/api/donations/scan", headers=auth_header(make_admin(client)), content_type="multipart/form-data",
                        data={"image": (io.BytesIO(png), "a.png")})
    assert other.status_code == 403


# ----- merkle -----

def test_merkle_root_deterministic_and_sensitive():
    from medbridge.audit import merkle_root
    import hashlib
    hs = [hashlib.sha256(str(i).encode()).hexdigest() for i in range(5)]
    assert merkle_root(hs) == merkle_root(list(hs))
    changed = hs[:]
    changed[3] = hashlib.sha256(b"x").hexdigest()
    assert merkle_root(changed) != merkle_root(hs)
    assert merkle_root(hs[:1]) == hs[0]


# ----- anchoring -----

def test_anchor_detects_edits_after_anchoring(app, client):
    import anchor as anchor_mod
    admin, donor, ngo, did, rid, code = run_to_handover(client)
    published = []
    def fake_publish(root, count):
        published.append((root, count))
        return {"tx": "0xabc", "id": 0, "stored": root}
    with app.app_context():
        a = anchor_mod.make_anchor(fake_publish)
        assert a.event_count == 4 and a.chain_label == "local-demo" and published[0][1] == 4
        assert anchor_mod.make_anchor(fake_publish) is None  # nothing new to anchor
    h = auth_header(admin)
    ok = client.post(f"/api/admin/anchors/{a.id}/check", headers=h).get_json()
    assert ok["matches"] is True and "local demo chain" in ok["note"]
    client.post(f"/api/donations/{did}/handover", json={"collection_code": code}, headers=auth_header(donor))
    with app.app_context():
        second = anchor_mod.make_anchor(None)  # unpublished anchor covers only the new event
        assert second.event_count == 1 and second.chain_label is None
    ev = Session.query(DonationEvent).filter_by(donation_id=did, label="allocated").first()
    ev.note = "changed after the fact"
    Session.commit()
    bad = client.post(f"/api/admin/anchors/{a.id}/check", headers=h).get_json()
    assert bad["matches"] is False
    assert client.get("/api/admin/anchors", headers=auth_header(donor)).status_code == 403


def test_anchor_refuses_mismatched_chain_root(app, client):
    import anchor as anchor_mod
    run_to_handover(client)
    with app.app_context():
        try:
            anchor_mod.make_anchor(lambda r, c: {"tx": "0x1", "stored": "0" * 64})
            assert False, "should have refused"
        except RuntimeError as e:
            assert "different root" in str(e)


def test_scan_prefilled_needs_confirmation_and_is_flagged(client):
    donor = donor_token(client)
    res = make_donation(client, donor, scan_fields=["strength", "batch_number", "bogus"])
    assert res.status_code == 400 and "photo-read" in res.get_json()["error"]["message"]
    ok = make_donation(client, donor, scan_fields=["strength", "batch_number", "bogus"], scan_confirmed=True)
    assert ok.status_code == 201
    flag = [f for f in ok.get_json()["donation"]["flags"] if f["code"] == "scan_prefilled"][0]
    assert "strength" in flag["text"] and "batch number" in flag["text"] and "bogus" not in flag["text"]


# ----- staging, quotas -----

def _cfg(**kw):
    from medbridge.config import Config
    class C(Config): pass
    for k, v in kw.items(): setattr(C, k, v)
    return C


STAGING_OK = dict(ENV="staging", MB_ENV="staging", JWT_SECRET="x" * 40, DATABASE_URL="postgresql://u:p@h/db",
                  AUDIT_SIGNING_KEY="k1:" + "s" * 30, MAIL_BACKEND="disabled", SIGNUP_ENABLED=False)


def test_staging_fails_closed():
    import pytest
    from medbridge.config import check_production
    check_production(_cfg(**STAGING_OK).__dict__ | {k: v for k, v in STAGING_OK.items()})
    for bad in ({"MAIL_BACKEND": "outbox"}, {"SIGNUP_ENABLED": True}, {"JWT_SECRET": "short"}, {"DATABASE_URL": "sqlite:///x.db"}, {"ENV": "prod"}):
        with pytest.raises(RuntimeError):
            check_production({**STAGING_OK, **bad})


def test_staging_invite_only_and_no_fake_email(tmp_path):
    from medbridge import create_app
    app = create_app(_cfg(DATABASE_URL=f"sqlite:///{tmp_path}/s.db", ENV="development", MB_ENV="staging", MAIL_BACKEND="disabled", SIGNUP_ENABLED=False, AUTO_CREATE_TABLES=True))
    c = app.test_client()
    r = c.post("/api/auth/register", json={"name": "A", "email": "a@b.co", "password": "longenough1", "role": "donor"})
    assert r.status_code == 403 and r.get_json()["error"]["code"] == "invite_only"
    r = c.post("/api/auth/forgot-password", json={"email": "a@b.co"})
    assert r.status_code == 503 and r.get_json()["error"]["code"] == "mail_disabled"
    assert c.get("/api/auth/dev-outbox").status_code == 404
    h = c.get("/api/health").get_json()
    assert h["email"] == "off" and h["signup_open"] is False and "not available on a host" in h["anchoring"]


def test_ngo_document_quota(app, client):
    ngo = register(client, "q0@x.co", role="ngo", organization="Quota Org").get_json()["token"]
    app.config["MAX_DOCS_PER_NGO"] = 2
    h = auth_header(ngo)
    codes = []
    for i in range(3):
        r = client.post("/api/ngo/documents", data={"registration_number": f"R{i}", "document": (io.BytesIO(b"%PDF-1.4 x"), "a.pdf")},
                        headers=h, content_type="multipart/form-data")
        codes.append(r.status_code)
    assert codes == [201, 201, 413]


def test_spa_served_when_dist_configured(tmp_path):
    from medbridge import create_app
    (tmp_path / "index.html").write_text("<html>app</html>")
    (tmp_path / "a.js").write_text("x=1")
    app = create_app(_cfg(DATABASE_URL=f"sqlite:///{tmp_path}/s.db", AUTO_CREATE_TABLES=True, FRONTEND_DIST=str(tmp_path)))
    c = app.test_client()
    assert b"app" in c.get("/").data and c.get("/a.js").data == b"x=1"
    assert b"app" in c.get("/some/client/route").data
    assert c.get("/api/nope").status_code == 404 and c.get("/api/nope").is_json
    assert b"app" in c.get("/../../etc/passwd").data  # traversal never leaves dist
    assert c.get("/api/health").get_json()["status"] == "ok"


def test_database_url_normalised():
    from medbridge.db import normalize_url
    assert normalize_url("postgres://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalize_url("postgresql://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalize_url("sqlite:///x.db") == "sqlite:///x.db"
