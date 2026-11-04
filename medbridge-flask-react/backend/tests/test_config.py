"""Production must fail closed on unsafe settings."""
import subprocess
import sys
import os

import pytest

from medbridge.config import DEV_SECRET, check_production

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def test_dev_mode_allows_defaults():
    check_production({"ENV": "development", "JWT_SECRET": DEV_SECRET, "DATABASE_URL": "sqlite://"})


def test_production_rejects_default_secret():
    with pytest.raises(RuntimeError):
        check_production({"ENV": "production", "JWT_SECRET": DEV_SECRET, "DATABASE_URL": "mysql://x"})


def test_production_rejects_short_secret():
    with pytest.raises(RuntimeError):
        check_production({"ENV": "production", "JWT_SECRET": "short", "DATABASE_URL": "mysql://x"})


def test_production_rejects_sqlite():
    with pytest.raises(RuntimeError):
        check_production({"ENV": "production", "JWT_SECRET": "x" * 40, "DATABASE_URL": "sqlite:///a.db"})


def test_production_accepts_strong_settings():
    check_production({"ENV": "production", "JWT_SECRET": "x" * 40, "DATABASE_URL": "mysql://u:p@h/db",
                      "AUDIT_SIGNING_KEY": "k1:" + "s" * 30, "MAIL_BACKEND": "smtp",
                      "SMTP_HOST": "smtp.example.test", "MAIL_FROM": "no-reply@example.test"})


def test_production_requires_audit_key_and_real_mail():
    with pytest.raises(RuntimeError) as e:
        check_production({"ENV": "production", "JWT_SECRET": "x" * 40, "DATABASE_URL": "mysql://u:p@h/db"})
    assert "AUDIT_SIGNING_KEY" in str(e.value) and "MAIL_BACKEND" in str(e.value)


def test_seed_refuses_in_production():
    env = {**os.environ, "MB_ENV": "production"}
    r = subprocess.run([sys.executable, "seed.py"], cwd=ROOT, env=env, capture_output=True, text=True)
    assert r.returncode != 0
    assert "local-only" in (r.stdout + r.stderr)


def test_app_refuses_to_start_in_production_without_secret():
    env = {k: v for k, v in os.environ.items() if k != "JWT_SECRET"}
    env["MB_ENV"] = "production"
    r = subprocess.run([sys.executable, "-c", "import app"], cwd=ROOT, env=env, capture_output=True, text=True)
    assert r.returncode != 0
    assert "Refusing to start" in r.stderr
