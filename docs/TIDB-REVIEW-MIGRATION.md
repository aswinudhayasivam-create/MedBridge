# TiDB Starter review migration — local preparation, not deployed

Base: `fd3b5bb68eb094f181ea26a75625048675c0811b`. Branch: `feature/tidb-hosted-review`.
The complete React/Spring Boot application is retained. No Aiven data is imported, no cloud resource is changed, and no hosted compatibility is claimed.

## Compatibility evidence and required live tests

| Feature | Official documentation finding | Release evidence still required |
| --- | --- | --- |
| Free MySQL-compatible Starter | Up to five eligible instances without a card; use a monthly limit of $0, never enable overage | Owner's actual account eligibility, MySQL mode, Starter tier, $0 limit and no payment method |
| Java 17 / Spring Boot 3 | Existing MySQL Connector/J speaks the supported protocol; dependencies unchanged | Actual JDBC connection, ORM metadata and application transactions |
| TLS | Public CA-signed certificates; Java system roots supported | VERIFY_IDENTITY succeeds; wrong hostname, untrusted CA and invalid trust fail |
| Foreign keys | Supported by modern TiDB; foreign_key_checks must stay enabled | All 11 relationships, orphan rejection and cascade behavior on this Starter instance |
| CHECK | Disabled by default; GLOBAL-only capability must already be ON before DDL | Four ENFORCED constraints; reject latitude, longitude, quantity and value violations |
| ENUM | Documented string type | Strict invalid-value rejection and every existing role/status mapping |
| AUTO_INCREMENT / LAST_INSERT_ID() | Supported, but distributed IDs can contain gaps and should not be treated as chronology | Generated IDs on the same transaction/connection; application joins and ownership |
| ON DUPLICATE KEY UPDATE | Supported syntax; do not infer full MySQL concurrency equivalence | Existing idempotence paths and duplicate requests |
| SELECT FOR UPDATE | Pessimistic mode supports row locks | Simultaneous NGO requests, donor approval/collection races, rollback and oversubscription tests |
| SHOW GRANTS | MySQL-compatible output; Starter connections require an instance username prefix | Actual database-only DML grants, no inherited role, global privilege, DDL or grant option |

Sources checked October 25, 2026: [free quota/eligibility](https://docs.pingcap.com/tidbcloud/serverless-limitations/), [spending limit](https://docs.pingcap.com/tidbcloud/manage-serverless-spend-limit/), [TLS and username prefix](https://docs.pingcap.com/tidbcloud/secure-connections-to-serverless-clusters/), [CHECK](https://docs.pingcap.com/tidb/stable/constraints/), [foreign keys](https://docs.pingcap.com/tidbcloud/foreign-key/), [ENUM](https://docs.pingcap.com/tidb/stable/data-type-string/), [AUTO_INCREMENT](https://docs.pingcap.com/tidb/stable/auto-increment/), [INSERT](https://docs.pingcap.com/tidb/stable/sql-statement-insert/), [pessimistic transactions](https://docs.pingcap.com/tidb/stable/pessimistic-transaction/), [SHOW GRANTS](https://docs.pingcap.com/tidb/stable/sql-statement-show-grants/).
The CHECK feature flag `tidb_enable_check_constraint` is GLOBAL-only, not SESSION-scoped ([system variable reference](https://docs.pingcap.com/tidb/stable/system-variables/#tidb_enable_check_constraint-new-in-v720)). Neither application nor initializer changes it. The selected Starter instance must already expose it as ON. If it is OFF or inaccessible, stop before DDL and resolve whether an eligible Starter instance can support the required constraints. A global change is outside the approved schema scope and requires a separate isolation/approval review; do not grant the runtime global administration.

These are documented capabilities, not successful TiDB-backed tests. Stop if the selected Starter service cannot enforce the existing constraints. Never silently remove them or switch to a paid tier.

## Immutable destination approval

`tidb-review-destination.properties` is loaded directly from the classpath, outside Spring's property override mechanism. It now pins the owner-approved and TLS-authenticated gateway hostname, port 4000 and exact case-sensitive prefix. Changing that approval requires a reviewed source change and rebuild. Destination approval alone does not satisfy database proof or enable review. A shared TiDB gateway hostname alone cannot identify an instance; the exact prefix is also pinned. No environment allowlist or arbitrary hostname activates access.

The JDBC URL must be exactly `jdbc:mysql://<pinned-host>:4000/medbridgereview20261026?sslMode=VERIFY_IDENTITY&serverTimezone=UTC`. The runtime connection name must be `<pinned-prefix>.mb_review_<suffix>`. Root/administrator names cannot pass. Startup also rejects administrative/role/global/table/column grants and requires all four database DML privileges.

The review profile, purpose, private invitation, distinct private passwords/JWT, safe SQL/ORM modes, verified security marker and deadline remain required. `@medbridge.test` identities remain local-only. Public registration stays false. Readiness and requests fail at `2026-11-22T18:30:00Z` (November 23, 00:00 IST); change the cutoff only through a reviewed commit with owner approval, never to resolve delays.

## Private configuration checklist

Store settings outside Git. Never send passwords in chat or add them to JSON, Docker build arguments, VITE variables, shell command lines, or logs.

Non-secret initialization JSON fields: `host`, `port` (4000), `database` (`medbridgereview20261026`), `usernamePrefix`, `ownerUser`, `runtimeUser`, `compatibility` (`MySQL`), `plan` (`Starter`), `monthlySpendingLimit` (numeric 0), `paymentMethodAdded` (false). These are assertions to independently verify in the owner's dashboard, not billing controls enforced by this script. No instance is provisioned automatically.

For a separately approved dedicated Render review service:

| Variable | Required value/check |
| --- | --- |
| SPRING_PROFILES_ACTIVE | Exactly `review` |
| REVIEW_ENABLED / REVIEW_PURPOSE | `true` / `FICTIONAL_COLLEGE_REVIEW_ONLY` only after approval |
| REVIEW_TRANSPORT / REVIEW_BIND_ADDRESS | `hosted-tls` / `0.0.0.0` |
| REVIEW_DB_HOST / REVIEW_DB_URL | Exact compiled pin and exact URL above |
| REVIEW_DB_USERNAME | Exact prefixed restricted runtime user |
| REVIEW_DB_PASSWORD | Private distinct random password, at least 24 characters |
| REVIEW_TLS_TRUST | `system`; no conflicting `/etc/secrets/ca.pem` on the TiDB review service |
| REVIEW_DB_INIT_SQL | Exactly `SET SESSION foreign_key_checks=ON, tidb_txn_mode='pessimistic'` |
| REVIEW_EXPIRES_AT | No later than `2026-11-22T18:30:00Z` |
| REVIEW_FRONTEND_ORIGIN | Exact separately approved HTTPS review origin |
| REVIEW_ACCESS_KEY / REVIEW_JWT_SECRET | Separate private random secrets, at least 48 characters |
| REVIEW_DONOR_PASSWORD / REVIEW_NGO_PASSWORD / REVIEW_PENDING_PASSWORD / REVIEW_ADMIN_PASSWORD | Four distinct private random passwords, at least 24 characters |
| APP_ALLOW_REGISTRATION / APP_DEMO_WORKFLOWS | Both `false` |
| SQL_INIT_MODE / effective Hibernate DDL | `never` / `none` |

Review attached environment groups, Spring JSON/config imports, command-line/Hikari/JNDI overrides, JAVA_TOOL_OPTIONS and truststore/driver overrides before deployment. Do not change existing services to satisfy this checklist. Pool lifetime is five minutes per documented Starter guidance; maximum pool remains four.

The Java 17 image uses its public root trust store for TiDB and validates that it exists and is readable. No Aiven CA import is needed in this mode. A conflicting CA secret file or missing/invalid trust store stops startup. Non-review custom-CA startup is retained for the existing Aiven build. Actual server certificate and wrong-host/untrusted-root tests remain blocked until the instance is available.

## Initialization procedure — requires separate cloud approval

1. Owner selects an eligible MySQL-compatible Starter instance with $0 spending and no card. Independently verify the exact endpoint and prefix, then commit the approved destination pin in a separate one-file commit. No Aiven import/reset is involved.
2. Separately approve creating the empty review database and distinct schema-owner/runtime users if not already present. The initializer creates neither databases nor users. Runtime gets only SELECT, INSERT, UPDATE and DELETE on `medbridgereview20261026.*`; schema owner handles DDL and REFERENCES. No broad roles or grant option. TiDB may display the account prefix differently internally; inspect actual SHOW GRANTS and stop on an unrecognized form rather than broadening permissions.
3. Verify Windows/Docker TCP connectivity. Obtain a documented public root CA bundle for the MySQL CLI from the operating system/vendor; do not reuse the private Aiven project CA. No SSL bypass.
4. Run `scripts/initialize-tidb-review-schema.ps1 -SettingsPath <private-json> -CaCertificatePath <public-root-bundle>` in private PowerShell. Dry run reads only local configuration and does no SQL.
5. After explicit database approval, run once with `-Apply`. Enter the restricted runtime and separate schema-owner passwords interactively. Passwords are transient process/container environment only; local Docker administrators can inspect a running container. Do not use transcripts or inspect container environments. The attempt lock is outside Git under LOCALAPPDATA; a failure requires private state inspection and fresh approval, never automatic rerun or lock deletion.
6. The script verifies runtime grants, DATABASE(), TiDB version, empty destination, GLOBAL CHECK capability and session settings, then applies unchanged `schema.sql`, `demo-schema.sql`, `review-schema.sql`. It stops on the first error and never drops constraints. DDL is not transactionally rolled back. It verifies 11 table names, four enforced CHECKs, 11 foreign keys, behavioral invalid CHECK/orphan rejection, the marker and zero records in all ten data tables. Probe records use transactions and are rolled back. No other schema is selected or written; this does not claim an independent whole-server data comparison.
7. Actual TiDB application tests are a separate required step. Use the restricted user, review-only credentials and fictional data after separate seeding approval. No old public demo passwords. The initializer does not seed accounts.

## Mandatory TiDB acceptance evidence

Record exact instance/build, private TLS/grant evidence and test results. Exercise private reviewer login, synthetic donor submission/photo, admin approval, pending NGO verification, discovery, request, donor approval, four-digit simulated collection, notifications and persistence across backend restart. Test invalid login, original public demo identities, missing/wrong invitation, ownership, roles, unauthorized accounts, expiry and duplicates. Race concurrent requests/approvals/collection; verify a single valid outcome, consistent reserved quantity and rolled-back failures. Verify actual FK/cascade/CHECK/ENUM, ID and upsert behavior. No public production requests or real medicine records.

The existing disposable MySQL tests establish regression coverage only. They cannot certify TiDB compatibility, public TLS, cloud availability, transaction locking or restart persistence on TiDB. A separate TiDB-backed harness/full browser run is still required before a hosted release.

## Hosting and rollback approval gates

Do not push this branch without approval. Current workflow branch filters cover Phase 2/4/5 branches, not `feature/tidb-hosted-review`; migration-branch CI is not claimed. Any CI update must preserve disposable test DB isolation and be committed one file at a time.

After passing TiDB tests, separately approve the exact Git push, dedicated Render settings/deployment, and separate protected Vercel review project/build. Keep Render auto-deploy off and the legacy Phase 2 service suspended. Keep normal Vercel production unchanged. Review build remains the existing React code (`npm run build -- --mode review`), with explicit review origin/flag and normal auth/demo flags false. No private invitation or password in frontend assets. Verify protection and the complete hosted journey before sharing.

Rollback only to a verified registration-protected review build compatible with the chosen provider and existing schema. An Aiven-only old build is not a valid TiDB rollback. If no safe build exists, obtain approval to suspend the review backend; preserve the review database for inspection. Never revive Phase 2, reconnect Aiven, disable TLS/constraints, extend expiry, or reset data as an availability workaround. Aiven files/service remain untouched until replacement verification and separately approved cleanup.

## Local validation of this migration

Java 17 Maven: 139 tests passed and package built. Existing disposable local MySQL authentication/demo/review suites: 1/3/5 passed. Frontend: 16 safety tests, 51 Vitest tests, both npm audits zero vulnerabilities, production build passed. Initial review integration failed because MySQL quotes grant recipients with backticks; the parser and regression coverage now handle both quoted forms without permitting broader privileges.

Seven startup checks passed with the installed Java 17 keytool/Git Bash using dedicated workspace fixture paths: missing explicit trust, missing roots, invalid store, conflicting custom CA, invalid custom CA, missing custom CA and successful system-trust dispatch. JVM launch was replaced with a sentinel so these tests did not start Spring or contact a database. They do not establish real TiDB certificate/hostname compatibility. The container-native test script is included for Linux/CI; a full backend image build and actual TiDB negative TLS tests remain required.

No passwords, hosted connections, user creation, SQL application, push, deployment or cloud configuration changes were performed for this migration.

## Authenticated Starter preflight — October 25 follow-up

The owner's exact case-sensitive instance prefix and ISRG Root X1 CA were used successfully with MySQL VERIFY_IDENTITY. Windows/Docker TCP passed; authenticated server version was `8.0.11-TiDB-v8.5.3-serverless`. DATABASE() matched `medbridgereview20261026`; initial table count was zero. GLOBAL CHECK, GLOBAL foreign-key capability and SESSION foreign_key_checks all read 1. No global setting was changed.

Two uniquely named, ownership-marked disposable probe tables were created, then metadata validation failed with error 1054 before behavioral rejection tests. The validator incorrectly assumed MySQL's TABLE_CONSTRAINTS.ENFORCED column, which TiDB does not expose. Both probe tables were removed after ownership validation and the final table count was zero. No application schema, accounts or other databases were modified. No probe or initializer retry was performed.

The local validator now uses TiDB's TIDB_CHECK_CONSTRAINTS for named CHECK inventory plus SHOW CREATE TABLE to reject NOT ENFORCED definitions; it also checks global foreign-key capability. This metadata correction has focused local regression coverage, but has not been exercised against TiDB. A new explicitly controlled disposable probe must verify actual CHECK/orphan rejection and cleanup before initialization readiness can be claimed. The immutable destination approval now pins the authenticated hostname and exact case-sensitive prefix; configuration alone still cannot make readiness pass without database proof; a separate restricted runtime user remains required. Administrator credentials are not a runtime configuration.

Reference: [TiDB TABLE_CONSTRAINTS layout](https://docs.pingcap.com/tidb/stable/information-schema-table-constraints/) and [TiDB CHECK metadata](https://docs.pingcap.com/tidb/stable/information-schema-tidb-check-constraints/).

Follow-up focused validation: 17 TiDB safety tests passed, including rejection of NOT ENFORCED/missing named checks and refusal to use the prefixed root as runtime. The original full Maven/Vitest suites were not repeated. No corrected cloud probe has run yet.
