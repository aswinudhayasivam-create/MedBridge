"""Runtime configuration. Everything environment-overridable for deployment."""
import os


DEV_SECRET = "dev-only-insecure-secret-change-me"


class Config:
    # "development" (default) or "production". Production is strict: see check_production().
    ENV = os.environ.get("MB_ENV", "development").lower()
    # Tables are created at startup only in development. Production uses migrations.
    AUTO_CREATE_TABLES = os.environ.get("AUTO_CREATE_TABLES", "1" if os.environ.get("MB_ENV", "development").lower() != "production" else "0") == "1"
    # Local default: SQLite file next to the app. In deployment set DATABASE_URL,
    # e.g. a MySQL (TiDB Cloud Starter) or Postgres connection string.
    DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///medbridge.db")
    # REQUIRED in deployment. The dev default only exists so the demo runs locally.
    JWT_SECRET = os.environ.get("JWT_SECRET", DEV_SECRET)
    JWT_TTL_HOURS = int(os.environ.get("JWT_TTL_HOURS", "12"))
    # Demo access-control policy, fully configurable. This is demonstration policy,
    # not legal advice - real medicine redistribution needs qualified review.
    MIN_DAYS_TO_EXPIRY = int(os.environ.get("MIN_DAYS_TO_EXPIRY", "30"))
    BLOCKED_CATEGORIES = tuple(
        c.strip().lower()
        for c in os.environ.get(
            "BLOCKED_CATEGORIES", "controlled,refrigerated,sample"
        ).split(",")
        if c.strip()
    )
    REQUIRE_EMAIL_VERIFICATION = os.environ.get(
        "REQUIRE_EMAIL_VERIFICATION", "1" if os.environ.get("MB_ENV", "development").lower() == "production" else "0") == "1"
    # outbox = development sink (nothing is sent). smtp = real sending (needs SMTP_* settings).
    MAIL_BACKEND = os.environ.get("MAIL_BACKEND", "outbox")
    SMTP_HOST = os.environ.get("SMTP_HOST", "")
    SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
    SMTP_USER = os.environ.get("SMTP_USER", "")
    SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")
    MAIL_FROM = os.environ.get("MAIL_FROM", "")
    EXPIRY_ALERT_DAYS = int(os.environ.get("EXPIRY_ALERT_DAYS", "30"))
    FRONTEND_URL = os.environ.get("FRONTEND_URL", "http://localhost:5173")
    MB_ENV = os.environ.get("MB_ENV", "development").lower()
    AUDIT_SIGNING_KEY = os.environ.get("AUDIT_SIGNING_KEY", "")
    MAX_DOCUMENT_BYTES = int(os.environ.get("MAX_DOCUMENT_BYTES", str(2 * 1024 * 1024)))
    # Private staging (MB_ENV=staging): invite-only, no email. Signup is closed unless SIGNUP_ENABLED=1.
    SIGNUP_ENABLED = os.environ.get("SIGNUP_ENABLED", "0" if os.environ.get("MB_ENV", "development").lower() == "staging" else "1") == "1"
    MAX_DOCS_PER_NGO = int(os.environ.get("MAX_DOCS_PER_NGO", "5"))
    MAX_DOC_BYTES_PER_NGO = int(os.environ.get("MAX_DOC_BYTES_PER_NGO", str(10 * 1024 * 1024)))
    FRONTEND_DIST = os.environ.get("FRONTEND_DIST", "")
    MAX_HANDOVER_ATTEMPTS = int(os.environ.get("MAX_HANDOVER_ATTEMPTS", "5"))
    MAX_QUANTITY_PER_DONATION = int(os.environ.get("MAX_QUANTITY_PER_DONATION", "500"))
    # Auth rate limit: requests per window per client IP.
    AUTH_RATE_LIMIT = int(os.environ.get("AUTH_RATE_LIMIT", "10"))
    AUTH_RATE_WINDOW_SECONDS = int(os.environ.get("AUTH_RATE_WINDOW_SECONDS", "60"))


def check_production(cfg):
    """Fail closed: refuse to start in production with unsafe settings."""
    env = str(cfg.get("ENV", "development")).lower()
    if env not in ("development", "staging", "production"):
        raise RuntimeError("MB_ENV must be development, staging or production.")
    if env == "development":
        return
    problems = []
    secret = cfg.get("JWT_SECRET") or ""
    if secret == DEV_SECRET or len(secret) < 32:
        problems.append("JWT_SECRET must be set to a random value of at least 32 characters.")
    url = cfg.get("DATABASE_URL") or ""
    if not url or url.startswith("sqlite"):
        problems.append("DATABASE_URL must point at a persistent hosted database (not SQLite).")
    if ":" not in (cfg.get("AUDIT_SIGNING_KEY") or "") or len(cfg.get("AUDIT_SIGNING_KEY") or "") < 24:
        problems.append("AUDIT_SIGNING_KEY ('kid:secret', 24+ chars) is required.")
    if env == "production":
        if cfg.get("MAIL_BACKEND") != "smtp" or not cfg.get("SMTP_HOST") or not cfg.get("MAIL_FROM"):
            problems.append("MAIL_BACKEND=smtp with SMTP_HOST and MAIL_FROM is required (the dev outbox sends nothing).")
    else:  # staging: explicit, never falls back from production
        if cfg.get("MAIL_BACKEND") not in ("disabled", "smtp"):
            problems.append("Staging needs MAIL_BACKEND=disabled (no email) or smtp. The dev outbox is not allowed on a host.")
        if cfg.get("MAIL_BACKEND") == "smtp" and (not cfg.get("SMTP_HOST") or not cfg.get("MAIL_FROM")):
            problems.append("MAIL_BACKEND=smtp needs SMTP_HOST and MAIL_FROM.")
        if cfg.get("SIGNUP_ENABLED"):
            problems.append("Staging is invite-only: leave SIGNUP_ENABLED unset or 0.")
    if problems:
        raise RuntimeError("Refusing to start in production: " + " ".join(problems))
