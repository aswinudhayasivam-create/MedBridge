# Phase 4: isolated local authentication

This branch extends the existing Spring Boot and React application. It is local work only: no GitHub push, merge, Render deployment, Vercel deployment, Aiven connection, or hosted environment change is part of these instructions.

Use fictional identities ending in `@example.invalid`. Do not enter medical information, production passwords, or real personal data. Medicine submission, transfers, payments, and NGO approval are outside this phase.

## Current verification status (20 October 2026)

- JDK 17 was installed from the official Temurin archive and its SHA-256 verified. Maven tests actually ran with Java 17.
- The official Docker installer is downloaded at `F:\ASWIN\Downloads\Docker\Docker Desktop Installer.exe`. Its Docker Inc Authenticode signature is valid and its SHA-256 matches `73431db38491f770543880366228a23c993f84d1e8e045f56d83b8a98940cf76`.
- Docker Desktop **4.95.0.242831 is installed at `F:\Docker\Docker`**, with Docker Engine 29.8.2 and Compose v5.6.0. The successful installer used `--installation-dir=F:\Docker\Docker`, `--wsl-default-data-root=F:\Docker\WSL`, `--backend=wsl-2` and `--no-windows-containers`. Its exit code was 0, and the Linux engine is operational through the local `desktop-linux` context.
- The actual WSL disks are `F:\Docker\WSL\disk\docker_data.vhdx` and `F:\Docker\WSL\main\ext4.vhdx`. Docker configuration/logs and standard CLI integration still use some C: locations managed by the official installer; no system folder was redirected manually. WSL and Virtual Machine Platform were already enabled. No WSL software installation, virtualization change, restart, Windows-wide TEMP/TMP change, Java relocation, paid subscription or payment method was required.
- The earlier installation hit a C: space check: 3,322 MiB required after extraction, 2,162 MiB available. The user separately approved moving three complete September installers to `F:\ASWIN\Downloads\Docker\Older-installers`. Each copy's SHA-256 was verified before removing its C: original. This moved 1,828,784,400 bytes (about 1.70 GiB), resolving the shortfall. After one canceled Windows UAC prompt, the user explicitly authorized another launch and approved it; that installation succeeded. No prompt was bypassed.
- Failed-attempt Docker-specific WinGet manifest metadata (360,231 file bytes), an empty extraction folder and two empty redirected-output files were removed. Measured C: free space increased by 524,288 bytes (512 KiB); F: was unchanged during that cleanup. Old uninstall remnants, unattributed temporary files, the unrelated Downloads compose file and Docker user data were retained. The older installers were preserved on F: in the separately approved move above. Detailed cleanup and move manifests are stored outside Git with the local verification evidence.
- **MySQL 8.4.11 is healthy.** The container publishes only `127.0.0.1:13306`, and inspection confirms `F:/Docker/MedBridge-MySQL` is its `/var/lib/mysql` bind mount. The existing schema created five tables with no seeded users or medicines. The real browser checks subsequently created two fictional accounts (one donor and one unverified NGO), both with BCrypt hashes. No medicine or production data was created or imported.
- `npm ci`: passed. `npm audit` and `npm audit --omit=dev`: zero vulnerabilities in every severity category. Dependency versions and the lockfile are unchanged.
- `npm run test:safety`: **14 passed**. `npm test`: **36 passed**, including 11 local-auth component tests. The component mocks are supplemented by real HTTP/MySQL and browser integration checks below.
- `mvn -B test` under Java 17: **28 passed**, including the real Testcontainers schema test. Explicit `LocalAuthMySqlIT`: **1 passed**, covering persistence, BCrypt, both roles' login, `/me`, duplicate email/license, ADMIN rejection, validation, missing/malformed/expired JWTs, 401/403 restrictions, NGO self-approval rejection and disabled direct registration. No backend failures, errors or skipped tests. The integration launcher ran both database suites successfully.
- Production build: passed. An additional build with `VITE_ENABLE_AUTH_SUBMISSIONS=true`, `NODE_ENV=development`, and `--mode local-auth` was served on loopback port 5173: both auth pages still showed Safe Preview and disabled submit buttons. A normal production build with submissions false was restored afterward.
- Homepage, registration, login, donor preview and NGO preview pass all **30 page/viewport combinations** at 320, 375, 393, 430, 768 and 1280 px. A confirmed 15 px overflow at 320 px came from the body's fixed 320 px minimum plus the scrollbar; changing only that minimum to 0 fixed it. Desktop styling and the compact mobile auth aside remain intact. At 393 px, registration's aside is about 150 px and login's about 128 px; role cards are equally sized, about 150 x 100 px.
- Browser registration/login, profile refresh, logout and reload-to-guest behavior passed for fictional accounts. The NGO dashboard remains "Awaiting verification." A browser-confirmed race that replaced success feedback with an automatic redirect was fixed; its new regression fails against the original code and passes with the fix.
- Password visibility, NGO selection and mobile menu/Escape focus return worked. Backend field errors use accessible input descriptions. Fresh browser checks reported no console warnings/errors. One earlier development CSS reload warning occurred during an intentional QA-server restart; it did not recur after a fresh load. Expected negative checks returned 401, 403 and 503; no unexpected application request failed in the completed flow.
- Final built-preview inspection recorded six successful document/asset requests, zero `/api/` requests and zero HTTP failures, even with the intentionally wrong true flag and local-auth build mode. No Vercel preview-feedback traffic was involved: these checks were entirely on localhost.
- The actually running backend was restarted **without** `-EnableRegistration`. A valid direct registration request returned 503 with safe JSON and left the user count at **2 before / 2 after**. The frontend and backend test servers were then stopped; Docker and the healthy isolated MySQL container remain available. Default registration/submission flags remain false.
- The local Phase 4 objective has no remaining test blocker. This work is not pushed, merged or deployed; production enablement and a future persistent/revocable session strategy require separate review and approval.

## Prerequisites and shell

Use Windows PowerShell or PowerShell 7 with Docker Desktop running Linux containers through a **local Windows named-pipe context**, JDK 17 and Maven on PATH, and a supported Node version from the frontend's `engines` field. Node and npm versions/dependencies are not upgraded by this phase. Do not substitute H2, SQLite, a remote Docker daemon, or another JDK for integration verification.

In every backend/test terminal, select the installed JDK 17 (substitute its actual path):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
java -version
mvn -version
```

These assignments affect this terminal and its children only. They do not change the system Java installation. If needed, add the installed Docker CLI to this terminal's PATH with `$env:PATH = 'F:\Docker\Docker\resources\bin;' + $env:PATH`. Check `docker context inspect --format '{{.Endpoints.docker.Host}}'`: it must be a local `npipe://` endpoint. Leave `DOCKER_HOST` and `DOCKER_TLS_VERIFY` unset. The launcher refuses a remote Docker context.

## Prepare and start the database

From the repository root:

```powershell
Set-Location 'F:\ASWIN\ASH\Projects\MED-Bridge\MedBridge-Phase3-GitHub'
.\scripts\phase4-local-auth.ps1 -Action Prepare
.\scripts\phase4-local-auth.ps1 -Action Database
```

`Prepare` generates random credentials and a signing secret in `.local-auth\development.env`, protected by a Windows ACL for the current user and SYSTEM. It prints no secret values and preserves an existing file. Git ignores this directory. Never copy it into a commit, screenshot, issue, log, or chat. Docker administrators can inspect container environment variables; use only disposable local credentials.

Compose creates project `medbridge-phase4-local-auth`, service `database`, image `mysql:8.4`, a bind mount from **F:\Docker\MedBridge-MySQL** to `/var/lib/mysql`, and database `medbridge_phase4_dev`. The only published socket is **127.0.0.1:13306**. It mounts the existing `schema.sql` read-only for initial database setup. It does not mount or execute `data.sql`, import production data, or seed approved NGOs. Spring's local profile applies the same idempotent schema without seed data.

The launcher creates the data directory only after Docker is ready, and records its ownership in the ignored `.local-auth\data-directory.txt` marker. It refuses an unrecognized nonempty directory or a junction/symbolic link instead of modifying or deleting existing data. The bind mount disables Compose's automatic host-directory creation. Do not reuse this data with a different credentials file: MySQL's initialization variables apply only to an empty data directory. Keep the generated configuration for subsequent starts; do not delete database files or volumes as routine troubleshooting.

Docker's official Windows installer documentation supports `--installation-dir` and `--wsl-default-data-root`; the installed paths and actual disk files above were verified after installation. Documentation: https://docs.docker.com/desktop/setup/install/windows-install/ and https://docs.docker.com/desktop/features/wsl/. Do not move existing WSL disks or Docker user data, edit installer caches/registry entries, or redirect Windows system folders.

## Start the backend

In a JDK 17 terminal at the repository root:

```powershell
# Registration disabled (the default):
.\scripts\phase4-local-auth.ps1 -Action Backend

# Or stop it with Ctrl+C, then explicitly enable fictional local registration:
.\scripts\phase4-local-auth.ps1 -Action Backend -EnableRegistration
```

The launcher sets only child-process environment variables and restores them when it returns. The `local-auth` profile binds HTTP to `127.0.0.1:8080` and fixes the JDBC URL to:

```text
jdbc:mysql://127.0.0.1:13306/medbridge_phase4_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

The startup guard runs before ordinary DataSource beans are constructed. It rejects remote or different JDBC URLs, Hikari URL/data-source overrides, JNDI, mixed profiles, a non-loopback HTTP binding, an unexpected driver, schema mutation settings or seed-data locations. A registration flag set outside this single isolated profile fails startup. Do not disable this guard to resolve startup errors.

`APP_ALLOW_REGISTRATION` defaults to false in the ordinary application configuration. Both controller and service enforce it; the controller rejects valid direct requests with safe JSON and HTTP 503 before invoking the transactional service. Invalid request bodies may return validation errors instead, but cannot write accounts. This code has not been deployed to the live backend, so it makes no claim about that backend's current registration policy.

## Start the frontend

In a separate terminal:

```powershell
Set-Location 'F:\ASWIN\ASH\Projects\MED-Bridge\MedBridge-Phase3-GitHub\MedBridge-Phase-1\medbridge\frontend'
npm ci
Set-Location '..\..\..'
.\scripts\phase4-local-auth.ps1 -Action Frontend -EnableSubmissions
```

Open `http://127.0.0.1:5173`. The page must say **Local development mode — use fictional test accounts only**. Without `-EnableSubmissions`, buttons stay disabled. Stop the development servers with Ctrl+C when finished.

Real requests require all of: Vite's `serve` command, mode `local-auth`, an explicit true submissions flag, an empty API base URL and HTTP origin `localhost:5173` or `127.0.0.1:5173`. The fixed `/api` proxy targets only `127.0.0.1:8080`. Before transmitting a password or JWT, the client requests `/api/auth/local-mode` and requires the isolated-backend marker; registration also requires the backend's enabled flag.

All `vite build` outputs are disabled at compile time, even when the auth environment variable, mode, or NODE_ENV is misconfigured. `npm run dev` also remains disabled. Never set a live Render URL or enable this flag in hosted settings. Existing hosted Safe Preview messages and disabled buttons remain intact.

## Environment variables

| Variable | Scope and purpose |
| --- | --- |
| `JAVA_HOME`, `PATH` | Terminal-only selection of JDK 17, Maven and Docker CLI. |
| `PHASE4_DB_USERNAME` | Generated local MySQL application username. |
| `PHASE4_DB_PASSWORD` | Generated application password; never commit or print. |
| `PHASE4_DB_ROOT_PASSWORD` | Generated container initialization password; not used by the application. |
| `PHASE4_JWT_SECRET` | Generated local signing secret; never commit or print. |
| `SPRING_PROFILES_ACTIVE` | Launcher selects only `local-auth` for backend/MySQL integration. |
| `SPRING_DATASOURCE_URL` | Launcher fixes the loopback JDBC URL above. Conflicting Hikari overrides are rejected. |
| `APP_ALLOW_REGISTRATION` | Defaults false. Only `-EnableRegistration` or the explicit integration test command enables it locally. |
| `VITE_ENABLE_AUTH_SUBMISSIONS` | Defaults false; `-EnableSubmissions` enables only the permitted local development server. |
| `VITE_API_BASE_URL` | Empty; the local proxy is required. A nonempty value disables submissions. |
| `DOCKER_HOST`, `DOCKER_TLS_VERIFY` | Must remain unset for the launcher; remote Docker endpoints are refused. |

Ordinary hosted configuration, production secrets, Vercel variables, Render variables and Aiven settings are not read or changed by this launcher. Inherited conflicting Spring overrides fail the guard rather than authorizing a remote database.

## Run tests

```powershell
# No database required; also compiles the MySQL integration test:
.\scripts\phase4-local-auth.ps1 -Action UnitTests

# Requires the healthy Compose database and JDK 17:
.\scripts\phase4-local-auth.ps1 -Action IntegrationTests
```

The integration command explicitly runs `LocalAuthMySqlIT` against the isolated Compose database. It generates fictional donor/NGO identities and passwords at runtime, checks password hashes and persistence, donor and NGO login, `/me`, missing/malformed/expired JWTs, duplicate emails/licenses, ADMIN rejection, validation, role restrictions and a direct registration-disabled request with an unchanged row count. It removes only its own generated identities afterward. No NGO is approved by this test.

It then runs `DatabaseSchemaTest` in a separate temporary MySQL Testcontainers database with registration disabled, generated credentials and a loopback-only random port. The integration class's `*IT` suffix deliberately excludes it from ordinary Surefire runs; do not mistake compilation for successful execution.

```powershell
Set-Location '.\MedBridge-Phase-1\medbridge\frontend'
npm ci
npm audit
npm audit --omit=dev
npm run test:safety
npm test
npm run build
```

The component suite mocks HTTP inside Vitest only. It checks donor/NGO registration and login, `/me` as the profile authority, duplicate/validation errors, loading/success messages, in-memory expiry, rejected profile refresh, guest/cross-role redirects, logout and no storage writes. New test passwords/tokens are generated at runtime.

## Reproduce the verified browser integration checks

Use generated fictional donor and NGO accounts through `/register` and `/login`. Verify the database rows and BCrypt hashes, sign out and back in, refresh the local profile, attempt guest and cross-role dashboard URLs, and confirm the NGO stays unverified with privileged endpoints returning 403. Check wrong-password, duplicate email/license, bad-token and disabled-registration results. Do not approve an NGO or submit a medicine.

JWTs stay in React memory only. Reloading ends the browser session; logout clears it locally. There is no token revocation/session persistence mechanism, so a previously issued token remains valid until its server expiry. Review any future session strategy separately.

Stop the database without deleting its data:

```powershell
.\scripts\phase4-local-auth.ps1 -Action StopDatabase
git status --short
```

All previous branches and commits are preserved. Each changed file is committed separately. Do not push, merge, deploy, or enable public registration without separate explicit approval.
