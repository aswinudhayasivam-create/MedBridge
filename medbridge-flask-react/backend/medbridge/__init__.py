"""MedBridge application factory."""
import os

from flask import Flask, jsonify

from .config import Config, check_production
from .db import Session, create_all, init_engine
from .errors import register_error_handlers


def create_app(config_object=Config):
    app = Flask(__name__)
    app.config.from_object(config_object)

    check_production(app.config)
    init_engine(app.config["DATABASE_URL"])
    if app.config.get("AUTO_CREATE_TABLES"):
        create_all()
    register_error_handlers(app)

    from .routes import admin, auth, donations, features, requests
    app.register_blueprint(auth.bp)
    app.register_blueprint(donations.bp)
    app.register_blueprint(requests.bp)
    app.register_blueprint(admin.bp)
    app.register_blueprint(features.bp)

    @app.get("/api/health")
    def health():
        c = app.config
        return jsonify({"status": "ok", "service": "medbridge-api", "env": c["MB_ENV"],
                        "signup_open": bool(c.get("SIGNUP_ENABLED", True)),
                        "email": "off" if c["MAIL_BACKEND"] == "disabled" else ("test-only" if c["MAIL_BACKEND"] == "outbox" else "on"),
                        "anchoring": "local demo chain only, not available on a host"})

    # Optional: serve the built React app from the same origin (single-service hosting).
    dist = os.path.realpath(app.config["FRONTEND_DIST"]) if app.config.get("FRONTEND_DIST") else ""
    if dist and os.path.isfile(os.path.join(dist, "index.html")):
        from flask import send_from_directory

        @app.get("/", defaults={"path": ""})
        @app.get("/<path:path>")
        def spa(path):
            if path.startswith("api/") or path == "api":
                return jsonify({"error": {"code": "not_found", "message": "Resource not found."}}), 404
            full = os.path.realpath(os.path.join(dist, path))
            if path and full.startswith(os.path.realpath(dist) + os.sep) and os.path.isfile(full):
                return send_from_directory(dist, path)
            return send_from_directory(dist, "index.html")

    @app.teardown_appcontext
    def _remove_session(exc=None):
        Session.remove()

    return app
