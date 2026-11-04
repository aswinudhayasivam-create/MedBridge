"""API behaviour tests: auth, roles, lifecycle, safety policy, errors."""
from conftest import (approve_ngo, auth_header, future_expiry, make_admin, register)


def donor_token(client, email="donor@test.dev"):
    return register(client, email, role="donor").get_json()["token"]


def verified_ngo_token(client, admin, email="ngo@test.dev"):
    res = register(client, email, role="ngo", organization="Test NGO")
    token = res.get_json()["token"]
    uid = res.get_json()["user"]["id"]
    approve_ngo(client, admin, uid, token, reg=f"REG-{email}")
    return token


def make_donation(client, token, **overrides):
    body = {
        "medicine_name": "Paracetamol 500mg",
        "strength": "500 mg",
        "category": "general",
        "quantity": 50,
        "unit": "tablets",
        "batch_number": "BN1234",
        "manufacturer": "Test Pharma Ltd",
        "has_qr": "yes",
        "expiry_date": future_expiry(),
        "sealed": True,
    }
    body.update(overrides)
    return client.post("/api/donations", json=body, headers=auth_header(token))


def approve_donation(client, admin, donation_id):
    return client.post(
        f"/api/admin/donations/{donation_id}/review",
        json={"decision": "approve"},
        headers=auth_header(admin),
    )


# ---------- health & auth ----------

def test_health(client):
    assert client.get("/api/health").get_json()["status"] == "ok"


def test_register_and_login(client):
    res = register(client, "a@test.dev")
    assert res.status_code == 201
    token = res.get_json()["token"]
    me = client.get("/api/auth/me", headers=auth_header(token))
    assert me.get_json()["user"]["email"] == "a@test.dev"


def test_login_wrong_password(client):
    register(client, "b@test.dev")
    res = client.post("/api/auth/login", json={"email": "b@test.dev", "password": "wrongpass1"})
    assert res.status_code == 401


def test_register_rejects_short_password(client):
    res = register(client, "c@test.dev", password="short")
    assert res.status_code == 400


def test_register_rejects_duplicate_email(client):
    register(client, "d@test.dev")
    assert register(client, "d@test.dev").status_code == 409


def test_register_cannot_self_assign_admin(client):
    res = register(client, "e@test.dev", role="admin")
    assert res.status_code == 400


def test_ngo_requires_organization(client):
    res = register(client, "f@test.dev", role="ngo")
    assert res.status_code == 400


def test_me_requires_token(client):
    assert client.get("/api/auth/me").status_code == 401


# ---------- role guards ----------

def test_ngo_cannot_create_donation(client):
    admin = make_admin(client)
    ngo = verified_ngo_token(client, admin)
    assert make_donation(client, ngo).status_code == 403


def test_donor_cannot_use_admin_routes(client):
    donor = donor_token(client)
    assert client.get("/api/admin/overview", headers=auth_header(donor)).status_code == 403


def test_unverified_ngo_cannot_browse(client):
    register(client, "g@test.dev", role="ngo", organization="Org")
    res = client.post("/api/auth/login", json={"email": "g@test.dev", "password": "password123"})
    token = res.get_json()["token"]
    assert client.get("/api/donations/browse", headers=auth_header(token)).status_code == 403


# ---------- safety policy ----------

def test_expired_medicine_rejected(client):
    donor = donor_token(client, "h@test.dev")
    res = make_donation(client, donor, expiry_date="2020-01-01")
    assert res.status_code == 400
    assert "Expired" in res.get_json()["error"]["message"]


def test_soon_expiring_medicine_rejected(client):
    donor = donor_token(client, "i@test.dev")
    res = make_donation(client, donor, expiry_date=future_expiry(days=5))
    assert res.status_code == 400


def test_blocked_category_rejected(client):
    donor = donor_token(client, "j@test.dev")
    res = make_donation(client, donor, category="controlled")
    assert res.status_code == 400


def test_bad_quantity_rejected(client):
    donor = donor_token(client, "k@test.dev")
    assert make_donation(client, donor, quantity=0).status_code == 400
    assert make_donation(client, donor, quantity="abc").status_code == 400


# ---------- full lifecycle ----------

def test_full_lifecycle(client):
    admin = make_admin(client)
    donor = donor_token(client, "l@test.dev")
    ngo = verified_ngo_token(client, admin)

    donation = make_donation(client, donor).get_json()["donation"]
    assert donation["status"] == "pending_review"

    # Not visible to NGOs before approval
    assert client.get("/api/donations/browse", headers=auth_header(ngo)).get_json()["donations"] == []

    assert approve_donation(client, admin, donation["id"]).status_code == 200
    browse = client.get("/api/donations/browse", headers=auth_header(ngo)).get_json()["donations"]
    assert [d["id"] for d in browse] == [donation["id"]]

    # NGO requests; duplicate blocked
    res = client.post("/api/requests", json={"donation_id": donation["id"]},
                      headers=auth_header(ngo))
    assert res.status_code == 201
    req = res.get_json()["request"]
    assert "collection_code" not in req  # only after approval
    assert client.post("/api/requests", json={"donation_id": donation["id"]},
                       headers=auth_header(ngo)).status_code == 409

    # Admin approves the request -> donation allocated; admin does NOT see the code
    res = client.post(f"/api/admin/requests/{req['id']}/decide",
                      json={"decision": "approve"}, headers=auth_header(admin))
    assert res.status_code == 200
    assert "collection_code" not in res.get_json()["request"]

    # Only the allocated NGO sees the code; the donor does not
    mine = client.get("/api/requests/mine", headers=auth_header(ngo)).get_json()["requests"]
    code = mine[0]["collection_code"]
    assert code
    dmine = client.get("/api/donations/mine", headers=auth_header(donor)).get_json()["donations"]
    assert "collection_code" not in dmine[0]
    assert dmine[0]["allocated_to"] == "Test NGO"

    # The NGO cannot confirm its own pickup (endpoint is donor-only)
    assert client.post(f"/api/donations/{donation['id']}/handover",
                       json={"collection_code": code}, headers=auth_header(ngo)).status_code == 403

    # Donor: wrong code fails; right code completes; second redemption rejected
    bad = client.post(f"/api/donations/{donation['id']}/handover",
                      json={"collection_code": "XXXXXX"}, headers=auth_header(donor))
    assert bad.status_code == 400
    good = client.post(f"/api/donations/{donation['id']}/handover",
                       json={"collection_code": code}, headers=auth_header(donor))
    assert good.status_code == 200
    assert good.get_json()["donation"]["status"] == "collected"
    again = client.post(f"/api/donations/{donation['id']}/handover",
                        json={"collection_code": code}, headers=auth_header(donor))
    assert again.status_code in (400, 409)

    # Request is completed, so the NGO UI has no stale action
    mine = client.get("/api/requests/mine", headers=auth_header(ngo)).get_json()["requests"]
    assert mine[0]["status"] == "completed"


def test_other_donor_cannot_confirm_handover(client):
    admin = make_admin(client)
    donor1 = donor_token(client, "r1@test.dev")
    donor2 = donor_token(client, "r2@test.dev")
    ngo = verified_ngo_token(client, admin, "ngo2@test.dev")
    d = make_donation(client, donor1).get_json()["donation"]
    approve_donation(client, admin, d["id"])
    r = client.post("/api/requests", json={"donation_id": d["id"]}, headers=auth_header(ngo)).get_json()["request"]
    client.post(f"/api/admin/requests/{r['id']}/decide", json={"decision": "approve"}, headers=auth_header(admin))
    code = client.get("/api/requests/mine", headers=auth_header(ngo)).get_json()["requests"][0]["collection_code"]
    assert client.post(f"/api/donations/{d['id']}/handover", json={"collection_code": code},
                       headers=auth_header(donor2)).status_code == 403


def test_two_ngos_cannot_both_be_allocated(client):
    admin = make_admin(client)
    donor = donor_token(client, "s@test.dev")
    ngo_a = verified_ngo_token(client, admin, "na@test.dev")
    ngo_b = verified_ngo_token(client, admin, "nb@test.dev")
    d = make_donation(client, donor).get_json()["donation"]
    approve_donation(client, admin, d["id"])
    ra = client.post("/api/requests", json={"donation_id": d["id"]}, headers=auth_header(ngo_a)).get_json()["request"]
    rb = client.post("/api/requests", json={"donation_id": d["id"]}, headers=auth_header(ngo_b)).get_json()["request"]
    first = client.post(f"/api/admin/requests/{ra['id']}/decide", json={"decision": "approve"}, headers=auth_header(admin))
    second = client.post(f"/api/admin/requests/{rb['id']}/decide", json={"decision": "approve"}, headers=auth_header(admin))
    assert first.status_code == 200
    assert second.status_code == 409  # other pending request was auto-rejected, no double allocation
    mine_b = client.get("/api/requests/mine", headers=auth_header(ngo_b)).get_json()["requests"][0]
    assert mine_b["status"] == "rejected" and "collection_code" not in mine_b


def test_reject_requires_reason(client):
    admin = make_admin(client)
    donor = donor_token(client, "m@test.dev")
    donation = make_donation(client, donor).get_json()["donation"]
    res = client.post(f"/api/admin/donations/{donation['id']}/review",
                      json={"decision": "reject"}, headers=auth_header(admin))
    assert res.status_code == 400
    res = client.post(f"/api/admin/donations/{donation['id']}/review",
                      json={"decision": "reject", "reason": "Damaged packaging"},
                      headers=auth_header(admin))
    assert res.get_json()["donation"]["status"] == "rejected"


def test_donor_cancel_flow(client):
    donor = donor_token(client, "n@test.dev")
    donation = make_donation(client, donor).get_json()["donation"]
    res = client.post(f"/api/donations/{donation['id']}/cancel", headers=auth_header(donor))
    assert res.get_json()["donation"]["status"] == "cancelled"
    # already cancelled -> conflict
    assert client.post(f"/api/donations/{donation['id']}/cancel",
                       headers=auth_header(donor)).status_code == 409


def test_other_donor_cannot_cancel(client):
    donor1 = donor_token(client, "o@test.dev")
    donor2 = donor_token(client, "p@test.dev")
    donation = make_donation(client, donor1).get_json()["donation"]
    assert client.post(f"/api/donations/{donation['id']}/cancel",
                       headers=auth_header(donor2)).status_code == 403


def test_unknown_donation_404(client):
    admin = make_admin(client)
    assert client.get("/api/donations/browse", headers=auth_header(admin)).status_code in (403, 404)
    res = client.post("/api/requests", json={"donation_id": 9999},
                      headers=auth_header(verified_ngo_token(client, admin, "q@test.dev")))
    assert res.status_code == 404
