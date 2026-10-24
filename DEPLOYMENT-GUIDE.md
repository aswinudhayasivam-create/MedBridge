# Protected online review deployment guide — not executed

**STOP before each cloud/configuration/database action and obtain separate approval.** No service, environment variable, CA file, database, domain or deployment has been changed by this preparation. No hosted review credentials or live review URL are published.

## Current evidence and unresolved infrastructure

| Resource | Known context | What remains unresolved |
| --- | --- | --- |
| `medbridge-backend` | Older Phase 2 service; not a valid review/rollback target | Keep isolated from any restored database; do not reconnect or deploy it |
| `medbridge-backend-protected` | Separate Phase 4 service; user reports first deployment failed without `/etc/secrets/ca.pem` | No claim of health; branch/config/CA changes require approval |
| Aiven | User reports stopped service and hostname mismatch with older configuration | Owner must verify identity, backups, actual hostname/port/CA, quota and intended destination before any recovery |
| Vercel | Existing production must remain unchanged | Separate protected review project/build/origin and audience require approval |

These are reported/historical facts, not a fresh cloud health assessment. This task does not inspect Aiven data or restart it.

## Free-plan decision gate

Use only existing free entitlement, without a payment method. [Render Free](https://render.com/docs/free) sleeps after 15 minutes without traffic, loses local filesystem changes on spin-down/redeploy and shares free instance-hour/usage limits. Keep images in MySQL; allow a cold-start delay in the evaluation plan.

[Aiven MySQL Free](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier) documents a 1 GB disk, connection limit and only one free MySQL service per organization. A separate free service is therefore **not assumed available**. Before provisioning or restoring anything, the owner must confirm a permitted free isolated database option. If no compliant free option exists, hosted review stays blocked; do not upgrade, add a card, or reuse production data automatically.

Preferred target: a separate approved free MySQL service with only the review database. Any proposal to share an existing server with a dedicated review schema/user needs explicit approval and a separate isolation assessment; this preparation does not authorize it. The code can prove database/schema/grant/marker boundaries, but cannot prove the cloud account's physical resource separation.

## Required approvals in order

1. One feature-branch push of the reviewed commit set (not main), after fresh deployment-source/preview safety preflight.
2. Any read-only cloud inventory needed to resolve current state, without fetching secrets.
3. Any Aiven power-on/recovery or a distinct free review-database provisioning action; first keep the old backend isolated and verify backups.
4. Creation of the empty dedicated review database, marker/schema and restricted runtime user; no production migration/reset/seed.
5. Private Render variables/CA secret-file configuration and any dedicated service creation or protected-service branch switch.
6. Backend deployment of the exact separately approved Git SHA.
7. Separate protected Vercel review project/configuration and review deployment; never change Vercel production.
8. Exposure of the verified protected URL and temporary reviewer credentials to a specified reviewer audience.
9. Any reset, credential rotation, rollback, suspension, database shutdown or cleanup action.

## Database, migration and backup checkpoints

Before recovery, record the service ID and current status, confirm an available backup/export and its restore procedure, and verify the actual hostname/port/database/CA in the private dashboard. Do not print connection strings with passwords. Keep the Phase 2 service disconnected from that destination. The application performs no production migrations.

For an **empty, approved dedicated review database** named `medbridgereview20261026`, an authorized schema owner applies, in order:

1. `MedBridge-Phase-1/medbridge/backend/src/main/resources/schema.sql`
2. `MedBridge-Phase-1/medbridge/backend/src/main/resources/demo-schema.sql`
3. `MedBridge-Phase-1/medbridge/backend/src/main/resources/review-schema.sql`

Never apply `data.sql`. Verify all eleven tables, expected marker/version/expiry, no non-review accounts, and no production records. Create a private `mb_review_...` runtime user with only SELECT, INSERT, UPDATE and DELETE on this database; remove global/DDL/grant-option permissions. Verify `SHOW GRANTS` privately. The startup proof rejects broader grants and unexpected identity records. Do not give the application schema-owner credentials.

Review seed adds four separate fictional accounts only after proof. It does not overwrite existing passwords/statuses or reset data on restart. A missing marker or schema is a blocker, not a reason to enable SQL initialization.

## Exact Render review settings

Dedicated review target only, or the protected service **only after explicit approval**:

| Setting | Proposed value |
| --- | --- |
| Repository | `aswinudhayasivam-create/MedBridge` |
| Branch | `feature/phase-5-8-demo-mvp`; pin the exact approved SHA at deployment |
| Auto-deploy / PR previews | Off |
| Plan / runtime | Free / Docker |
| Docker context | `MedBridge-Phase-1/medbridge/backend` |
| Dockerfile | `MedBridge-Phase-1/medbridge/backend/Dockerfile` |
| Docker command | Keep existing ENTRYPOINT; no override |
| Health check | `/api/review/ready` |
| Bind / port | `0.0.0.0` / Render's supplied `PORT` |

The existing entrypoint requires a non-empty CA certificate at `/etc/secrets/ca.pem` and imports it into a temporary JVM truststore. Add the **correct dedicated review MySQL CA** as a private Render secret file only after approval. Verify its issuer/fingerprint and endpoint hostname. Missing, stale or mismatched CA must block deployment. Do not disable TLS or hostname verification. Do not put secrets in Docker ARG, source, image layers or build logs.

## Private environment checklist

Enter values privately in the dedicated Render service, never GitHub/chat/README:

| Variable | Required check |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Exactly `review`; no local-auth/production/mixed profile |
| `REVIEW_ENABLED` | `true` only for this dedicated review service |
| `REVIEW_PURPOSE` | `FICTIONAL_COLLEGE_REVIEW_ONLY` |
| `REVIEW_TRANSPORT` / `REVIEW_BIND_ADDRESS` | `hosted-tls` / `0.0.0.0` |
| `REVIEW_EXPIRES_AT` | `2026-10-26T18:30:00Z` or earlier; never later |
| `REVIEW_DB_HOST` | Exact approved dedicated Aiven hostname, no protocol |
| `REVIEW_DB_URL` | `jdbc:mysql://<approved-host>:<port>/medbridgereview20261026?sslMode=VERIFY_IDENTITY&serverTimezone=UTC` |
| `REVIEW_DB_USERNAME` / `REVIEW_DB_PASSWORD` | Dedicated `mb_review_...` DML-only user / distinct private random password (>=24 characters) |
| `REVIEW_ACCESS_KEY` / `REVIEW_JWT_SECRET` | Distinct private random values, 48–128 characters, sufficient character diversity |
| `REVIEW_DONOR_PASSWORD`, `REVIEW_NGO_PASSWORD`, `REVIEW_PENDING_PASSWORD`, `REVIEW_ADMIN_PASSWORD` | Distinct private random passwords, 24–128 characters; never the public local passwords |
| `REVIEW_FRONTEND_ORIGIN` | One exact HTTPS review origin, no path/trailing slash, query, wildcard or credentials |
| `APP_ALLOW_REGISTRATION` / `APP_DEMO_WORKFLOWS` | `false` / `false` |
| `SQL_INIT_MODE` | `never`; review profile also requires effective initialization mode `never` |

Audit attached environment groups, `SPRING_APPLICATION_JSON`, command-line options, `SPRING_CONFIG_*`, datasource/Hikari/JNDI overrides, old `DB_*`, `JWT_SECRET` and frontend settings. Remove conflicts only after approval. Effective ORM DDL must be `none`. Never copy old production connection values into review settings. CA/password/account/signing/invitation values must be distinct private configuration.

## Vercel review settings

Use a **separate review project** rooted at `MedBridge-Phase-1/medbridge/frontend`, framework Vite, install `npm ci --no-audit --no-fund`, build `npm run build -- --mode review`, output `dist`. Set `VITE_ENABLE_REVIEW=true`, `VITE_REVIEW_API_ORIGIN` to the approved HTTPS review API origin, and both existing auth/demo flags false. No invitation key, password or JWT secret belongs in a `VITE_*` variable. The reviewer enters the private invitation key at login; it stays in memory.

Preserve Vercel Authentication/deployment protection. Verify the intended evaluator can access it on the actual free plan before committing to an audience. [Vercel Authentication](https://vercel.com/docs/deployment-protection/methods-to-protect-deployments/vercel-authentication) availability/audience constraints must be checked; no paid password-protection feature or bypass is assumed. If protected access cannot be arranged within free entitlement, stop and report the blocker. Do not expose a public fallback URL automatically.

## Verification before exposure

Record exact backend/frontend Git SHAs. Confirm TLS hostname verification and private DB grants/marker, readiness 200, no real data, public registration denied, original four local identities denied, missing invitation denied, wrong role/owner denied, rate limiting, pending NGO restrictions and server cutoff. Run the entire donor → admin → NGO → donor → code → simulated completion journey through the **hosted browser/API/MySQL**, then restart/redeploy the review backend and verify persistence. Review network/console errors, responsive widths and absence of public local credentials/secrets in compiled assets. Keep working reviewer credentials/URL private until the owner approves their exposure. Local rehearsal results alone do not establish online success.

## Fail-closed rollback, containment and safe reset

Rollback only to a verified registration-protected, review-guarded build compatible with the dedicated schema and deadline. **Never blindly roll back to Phase 2.** Keep auto-deploy off. Retain the dedicated database and backup. If no safe build can run, obtain approval to suspend the review web service in Render (Service → Settings → Suspend service); access becomes unavailable. Do not delete the service, weaken protection, reconnect the old backend, extend the deadline or enable registration to recover availability. Verify from outside that protected data/auth endpoints are unavailable. Recovery requires proof of exact source/config/CA/database/expiry, readiness, negative security tests and a new complete hosted journey before approved re-exposure.

Reset is a separately approved offline operator procedure, never an HTTP endpoint. First suspend access and take/verify a **review-only** backup. Privately verify `DATABASE()`, marker/version/expiry, grants and absence of non-review identities. In a transaction on the dedicated database, clear only fictional workflow rows in dependency order: `demo_events`, `demo_request_details`, `requests`, `demo_medicine_details`, `medicines`, `demo_photos`, `notifications`; retain account/password/profile/marker rows. Reconcile fictional NGO verification/application state deliberately, commit only after checks, then restart and retest. Do not disable foreign keys, drop schemas/volumes, run broad deletes on another database, or automate a reset against a URL chosen from old environment files. Preserve evidence/backup for rollback.

At cutoff, application access fails closed automatically; approved operator suspension and credential revocation can follow. Data retention/deletion and Aiven shutdown remain separate approved actions, not an automatic side effect of expiry.

The fixed review database name intentionally contains no SQL wildcard characters, so database-level grants cannot accidentally match another name through an underscore pattern. See the [MySQL GRANT reference](https://dev.mysql.com/doc/refman/8.0/en/grant.html).

For consistent illustrative schedule times, configure the dedicated review JVM timezone as Asia/Kolkata at the approved deployment, retaining existing memory/error options (append `-Duser.timezone=Asia/Kolkata` to the reviewed `JAVA_TOOL_OPTIONS`). Verify displayed timestamps through the hosted browser. The cutoff uses an absolute server `Instant`, independent of this display timezone.

Before any separately approved containment action, confirm that the current Render service exposes the Suspend control and that the operator has permission. Do not substitute a paid maintenance option or delete the service. Review the current [rollback configuration semantics](https://render.com/docs/rollbacks): rollback does not reverse database changes, environment-group effects need review, and auto-deploy must be rechecked.
