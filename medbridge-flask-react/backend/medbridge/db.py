"""Engine/session lifecycle. One scoped session per app, removed on teardown."""
from sqlalchemy import create_engine
from sqlalchemy.orm import scoped_session, sessionmaker, declarative_base

Base = declarative_base()
Session = scoped_session(sessionmaker(expire_on_commit=False))


def normalize_url(url):
    """Hosts hand out postgres:// or postgresql:// URLs; use the psycopg 3 driver we ship."""
    for prefix in ("postgres://", "postgresql://"):
        if url.startswith(prefix):
            return "postgresql+psycopg://" + url[len(prefix):]
    return url


def init_engine(database_url):
    database_url = normalize_url(database_url)
    connect_args = {"check_same_thread": False} if database_url.startswith("sqlite") else {}
    engine = create_engine(database_url, connect_args=connect_args, pool_pre_ping=True)
    Session.configure(bind=engine)
    return engine


def create_all():
    from . import models  # noqa: F401  (register models)
    Base.metadata.create_all(bind=Session.bind)
