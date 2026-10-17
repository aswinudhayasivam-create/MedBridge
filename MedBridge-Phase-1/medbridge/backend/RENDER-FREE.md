# MedBridge — Render Free + Aiven MySQL Free

## Scope
Phase 2 Spring Boot API only. Existing GitHub paths and Vercel frontend stay unchanged. No medicine distribution should be operated using this demonstration without legal, regulatory, and safety review.

## Render web service setup
- Repository: `https://github.com/aswinudhayasivam-create/MedBridge`
- Branch: `feature/phase-2-auth`
- Plan: **Free ($0)**. Never upgrade as part of this setup.
- Runtime: **Docker**
- Docker context: `MedBridge-Phase-1/medbridge/backend`
- Dockerfile: `MedBridge-Phase-1/medbridge/backend/Dockerfile`
- Region: Oregon/Ohio/Virginia recommended when your Aiven Free database resides in North America; verify actual location before selecting.
- The backend reads Render's provided `PORT` through `server.port=${PORT:8080}`.
- Configure a health check at `/api/health`.

## Render environment variables — enter in Render dashboard, not GitHub or chat

| Variable | Value |
| --- | --- |
| `DB_URL` | `jdbc:mysql://<AIVEN_HOST>:<AIVEN_PORT>/<DATABASE>?sslMode=VERIFY_IDENTITY` |
| `DB_USERNAME` | Aiven MySQL username |
| `DB_PASSWORD` | Aiven MySQL password (secret) |
| `JWT_SECRET` | Unique high-entropy random string, 32+ UTF-8 bytes (secret) |
| `FRONTEND_URL` | `https://med-bridge-ruddy.vercel.app` |
| `SQL_INIT_MODE` | `always` for initial schema creation; `never` after tables have been created |
| `JWT_EXPIRY_SECONDS` | `3600` (optional) |

Use the **Aiven database name you actually created** (often `defaultdb`). MySQL host, port, and username are provided by Aiven; never use values guessed from the project name. With `sslMode=VERIFY_IDENTITY`, Java must trust the server's issuing CA. If TLS handshake fails, add Aiven's CA certificate to the JVM truststore before retrying; do not disable TLS certificate verification for a real deployment.

**Never enter real credentials in a GitHub commit, issue, screenshot, or ChatGPT message.**

## MySQL initialization and seed safety
This application manages schema creation via `schema.sql` (idempotent `CREATE TABLE IF NOT EXISTS`). The public deployment must **not** auto-execute `data.sql` because it includes fictional demo accounts and a known example password. Application configuration directs data initialization to an optional, absent resource instead. Confirm that this behavior remains unchanged during subsequent phases.

## Build and health check
GitHub Actions workflow `.github/workflows/medbridge-backend-ci.yml` compiles and tests the Java 17 backend. Docker uses Java 17 for both build and runtime. Test `GET https://<RENDER_SERVICE>.onrender.com/api/health` after deployment. This health response alone does **not** prove database connectivity or authentication flows; verify those separately in a controlled demo environment.

## Free-plan limitations
Render Free services spin down when idle and can have a cold-start delay. Memory is limited; monitor Java usage and be prepared to reduce dependencies, but do not switch to paid plans automatically. Free instance hour and bandwidth allowances may be shared with other services in the same workspace. Aiven Free instances can suspend after inactivity.
