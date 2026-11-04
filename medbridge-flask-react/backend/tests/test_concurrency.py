"""Concurrent approvals must not double-allocate. Needs a real multi-connection DB
(set TEST_DATABASE_URL to a Postgres URL); skipped on in-memory SQLite."""
import os
import threading

import pytest

from conftest import approve_ngo, auth_header, future_expiry, make_admin, register
from medbridge import create_app
from medbridge.db import Session

pytestmark = pytest.mark.skipif(
    not os.environ.get("TEST_DATABASE_URL"), reason="needs a real database server"
)


def test_concurrent_approvals_allocate_once(app):
    client = app.test_client()
    admin = make_admin(client)
    donor = register(client, "cd@test.dev").get_json()["token"]
    ngos = []
    for i in range(4):
        r = register(client, f"cn{i}@test.dev", role="ngo", organization=f"Org{i}").get_json()
        approve_ngo(client, admin, r["user"]["id"], r["token"], reg=f"R{i}")
        ngos.append(r["token"])
    d = client.post("/api/donations", json={"medicine_name": "X", "batch_number": "B1", "manufacturer": "M", "quantity": 10, "expiry_date": future_expiry()},
                    headers=auth_header(donor)).get_json()["donation"]
    client.post(f"/api/admin/donations/{d['id']}/review", json={"decision": "approve"}, headers=auth_header(admin))
    req_ids = [client.post("/api/requests", json={"donation_id": d["id"]}, headers=auth_header(t)).get_json()["request"]["id"] for t in ngos]
    codes = []

    def approve(rid):
        c = app.test_client()
        res = c.post(f"/api/admin/requests/{rid}/decide", json={"decision": "approve"}, headers=auth_header(admin))
        codes.append(res.status_code)
        Session.remove()

    threads = [threading.Thread(target=approve, args=(rid,)) for rid in req_ids]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert codes.count(200) == 1
    assert all(c in (200, 409) for c in codes)
    Session.remove()
    approved = [t for t in ngos if client.get("/api/requests/mine", headers=auth_header(t)).get_json()["requests"][0]["status"] == "approved"]
    assert len(approved) == 1
