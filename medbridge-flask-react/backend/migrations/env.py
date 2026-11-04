"""Alembic environment. Uses DATABASE_URL (same as the app)."""
import os
import sys

from alembic import context
from sqlalchemy import create_engine

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from medbridge.db import Base  # noqa: E402
from medbridge import models  # noqa: E402,F401

target_metadata = Base.metadata


def _url():
    from medbridge.db import normalize_url
    return normalize_url(os.environ.get("DATABASE_URL", "sqlite:///medbridge.db"))


def run_migrations_online():
    engine = create_engine(_url())
    with engine.connect() as conn:
        context.configure(connection=conn, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


run_migrations_online()
