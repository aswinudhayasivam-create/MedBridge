# Private test deployment proposal (NOT executed, needs the owner's approval)

Status: proposal only. Nothing is deployed. No existing MedBridge service, GitHub repo, TiDB, Render or Vercel project is touched.

## Shape
- One NEW Render free web service (Docker, installs tesseract-ocr) serving the Flask API and the built React app.
- One NEW Neon free Postgres project. All state lives here.
- Private use: invited testers only, sample data only, no real medicines.

## What is stored where (checked in code)
| Data | Location | Survives Render sleep/redeploy? |
|---|---|---|
| Accounts, donations, needs, notifications, audit events, anchors | Postgres | yes |
| NGO registration documents (max 2 MB each, MAX_DOCUMENT_BYTES) | Postgres BLOB column | yes |
| Test-mail outbox | Postgres table | yes |
| Receipts, timelines | generated on request from Postgres | n/a, nothing on disk |
| Label photos for OCR | processed in memory, never stored | n/a |
| JWT secret, AUDIT_SIGNING_KEY | host environment variables, not in the DB or ZIP | yes while set |
| Local Hardhat chain | NOT hosted. It is a local demo only | no: hosted version has the signed hash chain only, anchoring stays off |

Nothing needed after a restart is on the web service's disk. Document growth is bounded by the 1 GB Neon limit; a per-user and total document cap should be added before invites.

## Limits (from provider docs read 2026-11-04)
- Render free: 512 MB RAM, 0.1 CPU, sleeps after 15 min idle, about 1 min wake, ephemeral disk, no outbound ports 25/465/587 (https://render.com/docs/free).
- Neon free: 1 GB per project, 100 CU-hours per month, scales to zero after 5 min, instant restore window of 6 hours capped at 1 GB of history (https://neon.com/faqs/free-plan-limits-and-quotas, https://neon.com/docs/introduction/plans). That is a short recovery window, not a long-term backup. Periodic exports (pg_dump) would be separate work.

## Unverified until a trial deploy
- Docker on Render's Free plan (docs say Docker is supported; free-plan statement not found).
- Tesseract speed and memory on 0.1 CPU / 512 MB.
- Wake-up time of app plus DB together.

## Not solved
- Real email: SMTP is blocked on free Render. A provider with an HTTPS API and a verified sender is needed. Not chosen, not checked. Until then verification/reset mail goes to the test outbox only, so real testers cannot receive it. Testers would need accounts pre-verified by the admin.
- Rate limiter is in-memory (single process only).
- No public Docker image with user code or credentials. Deploy source must come from a ZIP or repo the owner approves.

## Exact approvals that would be needed
1. Creating one new Render service and one new Neon project (owner's accounts or new ones).
2. A way to give Render the source that the owner approves (his decision, separate from the GitHub push gate).
3. A decision on the email path.
