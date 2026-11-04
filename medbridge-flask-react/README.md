# MedBridge (Python + React rebuild)

A medicine-donation coordination app: donors list unused, sealed medicines; NGOs post needs and request them; an admin reviews everything. This is a separate rebuild. The original Java version is not touched.

**Status: in development, test version.** Sample data only. It is not cleared for real medicines. Real use needs legal and pharmacy review first (see "Before real use").

## Stack
Flask + SQLAlchemy + Alembic (backend), React + Vite + Tailwind (frontend, installable web app basics), Tesseract (label OCR, local), Hardhat (optional local demo chain).

## What is built
- Donor, NGO and admin accounts. Email confirmation and password reset (single-use hashed tokens).
- NGO registration document upload and admin review. Admin approval is a hand check of a paper. It is not an identity or licence check and not permission to receive medicines. Review flags: no document, no Darpan ID, duplicate registration number.
- Label scan: Tesseract reads a photo and fills fields as *suggestions*. The donor must confirm they checked them against the pack. The photo is never stored. Changing the medicine name clears values that came from the old photo.
- Screening flags (missing batch or manufacturer, unsealed, QR not confirmed, short-dated, large quantity, duplicate). These are flags for a human, not a genuine/fake verdict. Approving a flagged listing needs a written note.
- City/PIN ordering, expiry alerts (in-app only), NGO needs board, donation timeline and a receipt PDF, handover code with attempt lock and admin reissue. The handover is the donor's attestation, not proof of pickup.
- Audit trail: each event is hashed into a chain and signed on the server (key id + secret, rotatable). This makes edits detectable. It is tamper-evident, not tamper-proof.
- Optional anchoring of Merkle roots to a **local demo chain** (Hardhat). We control it, so it proves nothing to third parties. It is not available on a host.

## Run locally
```
cd backend && pip install -r requirements.txt && python seed.py && python app.py      # API on :5000
cd frontend && npm install && npm run dev                                              # UI on :5173
# optional: sudo apt install tesseract-ocr   (label scan returns "unavailable" without it)
```
Seed logins share one demo password (`MedBridge-Demo-2026`): admin@medbridge.demo, donor@medbridge.demo, ngo@medbridge.demo. Local only. `seed.py` refuses to run in production.

Tests: `cd backend && pytest` (SQLite). Postgres: set `DATABASE_URL` and run the same. `cd chain && npm install && npx hardhat test`.
Anchoring demo: `cd chain && npx hardhat node`, then `cd backend && python anchor.py`.

## One-service build (untested image)
`Dockerfile` builds the React app and serves it plus the API with gunicorn (`FRONTEND_DIST`). The gunicorn + static serving path was checked locally (/, a client route and an asset return 200). The Docker image itself has NOT been built: Docker is not available where this was developed. Treat it as a first draft.

## Modes (`MB_ENV`)
- `development` (default): SQLite, test outbox for mail (nothing is sent).
- `staging`: private, invite-only test host. Fails to start unless JWT secret, non-SQLite DB, `AUDIT_SIGNING_KEY` are set, `MAIL_BACKEND=disabled` (or smtp), and signup is closed. Create testers with `python provision.py`. Email reset is off and says so. No fallback from production to staging.
- `production`: all of the above plus real SMTP. Tables come from Alembic migrations only.

## Test status (honest)
Passed: 61 backend tests on SQLite, 62 on a local PostgreSQL (with plain postgresql:// URLs and alembic upgrade), 3 contract tests, browser flows (signup, document review, scan, flag override, nearby badge, timeline, receipt, password reset, mobile layout, scan-stale-data regression, expiry alert, handover lock and reissue, NGO timeline).
Not verified: OCR on real phone photos (only a clean synthetic label and a mock), iPhone, MySQL/TiDB, any hosted deployment, real email sending, production email-verification gate in a browser.

## Security notes (not a production security claim)
- The seed demo password is for local development only and is public in this README. `seed.py` refuses to run in production. In staging or production, create accounts with `python provision.py` (random one-time password per account, including admins). There is no shared default admin.
- The login token is kept in browser localStorage, so any cross-site scripting bug would expose it. A cookie-based session would be safer and is not built.
- The rate limiter is in memory and per process. It does not survive restarts and does not work across several workers.
- There is no security audit or penetration test. Do not describe this as secure for real users.

## Before real use
Legal and pharmacy review of redistribution rules for the jurisdiction; real email sender; durable rate limiting (current limiter is in-memory); per-user admin accounts (seed shares one password); backups and recovery; a real identity process for NGOs; OCR validation on real photos. See `docs/DEPLOY_PROPOSAL.md` for the free-tier hosting plan and its limits.
