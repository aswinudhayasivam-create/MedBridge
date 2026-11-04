import os
import sys
from datetime import date, timedelta

import pytest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from medbridge import create_app  # noqa: E402
from medbridge.config import Config  # noqa: E402
from medbridge.db import Session  # noqa: E402
from medbridge.ratelimit import reset as reset_rate_limit  # noqa: E402


class TestConfig(Config):
    # Set TEST_DATABASE_URL to run the same suite against another database (e.g. Postgres).
    DATABASE_URL = os.environ.get("TEST_DATABASE_URL", "sqlite:///:memory:")
    JWT_SECRET = "test-secret"
    AUTH_RATE_LIMIT = 100


@pytest.fixture()
def app():
    app = create_app(TestConfig)
    from medbridge.db import Base
    if not TestConfig.DATABASE_URL.startswith("sqlite"):
        Base.metadata.drop_all(bind=Session.bind)
        Base.metadata.create_all(bind=Session.bind)
    yield app
    Session.remove()


@pytest.fixture()
def client(app):
    reset_rate_limit()
    return app.test_client()


def register(client, email, role="donor", name="Test User", organization=None, password="password123", **extra):
    body = {"name": name, "email": email, "password": password, "role": role, **extra}
    if organization:
        body["organization"] = organization
    return client.post("/api/auth/register", json=body)


def auth_header(token):
    return {"Authorization": f"Bearer {token}"}


def make_admin(client):
    """Seed an admin directly (admin accounts are not self-registerable)."""
    from medbridge.models import User
    from medbridge.security import hash_password
    user = User(name="Admin", email="admin@test.dev",
                password_hash=hash_password("adminpass123"), role="admin",
                ngo_verified=False)
    Session.add(user)
    Session.commit()
    res = client.post("/api/auth/login", json={"email": "admin@test.dev", "password": "adminpass123"})
    return res.get_json()["token"]


def future_expiry(days=200):
    return (date.today() + timedelta(days=days)).isoformat()


def approve_ngo(client, admin, user_id, token, reg="REG-1234"):
    """NGO uploads a registration document; admin approves it (the only way to become admin-approved)."""
    import io
    res = client.post("/api/ngo/documents", headers=auth_header(token), content_type="multipart/form-data",
                      data={"registration_number": reg, "document": (io.BytesIO(b"%PDF-1.4 test"), "reg.pdf")})
    assert res.status_code == 201, res.get_json()
    doc_id = res.get_json()["document"]["id"]
    r2 = client.post(f"/api/admin/ngo-documents/{doc_id}/review", json={"decision": "approve"}, headers=auth_header(admin))
    assert r2.status_code == 200, r2.get_json()
