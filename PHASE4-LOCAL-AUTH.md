# Phase 4: isolated local authentication

This branch extends the existing Spring Boot and React application. It is local work only: no GitHub push, merge, Render deployment, Vercel deployment, Aiven connection, or hosted environment change is part of these instructions.

Use fictional identities ending in `@example.invalid`. Do not enter medical information, production passwords, or real personal data. Medicine submission, transfers, payments, and NGO approval are outside this phase.

## Current verification status (20 October 2026)

- JDK 17 was installed from the official Temurin archive and its SHA-256 verified. Maven tests actually ran with Java 17.
- The official Docker installer is downloaded at `F:\ASWIN\Downloads\Docker\Docker Desktop Installer.exe`. Its Docker Inc Authenticode signature is valid and its SHA-256 matches `73431db38491f770543880366228a23c993f84d1e8e045f56d83b8a98940cf76`.
- After safe cleanup, one all-users installation attempt supplied `--installation-dir=F:\Docker\Docker`, `--wsl-default-data-root=F:\Docker\WSL`, `--backend=wsl-2` and `--no-windows-containers`. It still failed the C: space check before installing: 3,322 MiB required after extraction, 2,162 MiB available, a **1,160 MiB shortfall**. Installation retries stopped until the user separately approved moving three older installers from C: to F:.
- Those three complete September installers were copied to `F:\ASWIN\Downloads\Docker\Older-installers`, SHA-256 verified, and only then removed from their original Downloads locations. This moved 1,828,784,400 bytes (about 1.70 GiB) and raised C: free space to about 4.38 GiB. The measured disk-space shortfall was resolved. The next installation launch waited for Windows administrator confirmation, then Windows returned **"The operation was canceled by the user."** The installer did not start on that launch. **Current blocker: administrator-approved installation, followed by Docker first-run/engine verification.** Do not reopen the administrator prompt automatically.
- Docker Desktop is **not installed**. `F:\Docker\Docker` and `F:\Docker\WSL` are intended paths, not verified runtime locations. WSL and Virtual Machine Platform were already enabled; no WSL installation, virtualization change, restart, Windows-wide TEMP/TMP change, or Java relocation was performed.
- Failed-attempt Docker-specific WinGet manifest metadata (360,231 file bytes), an empty extraction folder and two empty redirected-output files were removed. Measured C: free space increased by 524,288 bytes (512 KiB); F: was unchanged during that cleanup. Old uninstall remnants, unattributed temporary files, the unrelated Downloads compose file and Docker user data were retained. The older installers were preserved on F: in the separately approved move above. Detailed cleanup and move manifests are stored outside Git with the local verification evidence.
- **No MySQL container or backend with a database has been started. Real database persistence and browser-to-MySQL sign-in are not yet verified. Do not treat this branch as ready for production.**
- `npm ci`: passed. `npm audit` and `npm audit --omit=dev`: zero vulnerabilities in every severity category. Dependency versions and the lockfile are unchanged.
- `npm run test:safety`: 14 passed. `npm test`: 35 passed, including 10 local-auth component tests with mocked HTTP responses. These mocks do not establish real database integration.
- Maven's database-independent suite: 27 passed, no failures/errors/skips. The new MySQL integration test compiles but has not run. The existing Testcontainers schema test also remains pending Docker.
- Production build: passed. An additional build with `VITE_ENABLE_AUTH_SUBMISSIONS=true`, `NODE_ENV=development`, and `--mode local-auth` was served on loopback port 5173: both auth pages still showed Safe Preview and disabled submit buttons. A normal production build with submissions false was restored afterward.
- Homepage, registration, login, donor preview and NGO preview rendered without horizontal overflow at 320, 375, 393, 430, 768 and 1280 px. At 393 px, registration's teal aside measured about 150 px and login's about 128 px; role cards measured about 150 x 100 px each. No CSS was changed.
- Password visibility, NGO selection and the mobile menu/Escape focus return worked. The built site logged no browser warnings/errors; its local request observer recorded 103 successful document/asset requests and zero `/api/` requests.
- Explicit local dev mode showed its warning and safely refused submission when the backend was absent. The only proxied request was the isolation handshake `/api/auth/local-mode`, refused at `127.0.0.1:8080`. This is the expected missing-prerequisite failure, not a successful registration.

## Prerequisites and shell

Use Windows PowerShell or PowerShell 7 with Docker Desktop running Linux containers through a **local Windows named-pipe context**, JDK 17 and Maven on PATH, and a supported Node version from the frontend's `engines` field. Node and npm versions/dependencies are not upgraded by this phase. Do not substitute H2, SQLite, a remote Docker daemon, or another JDK for integration verification.

In every backend/test terminal, select the installed JDK 17 (substitute its actual path):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
java -version
mvn -version
```

These assignments affect this terminal and its children only. They do not change the system Java installation. If needed, add Docker Desktop's CLI `resources\bin` directory to this terminal's PATH. Check `docker context inspect --format '{{.Endpoints.docker.Host}}'`: it must be a local `npipe://` endpoint. Leave `DOCKER_HOST` and `DOCKER_TLS_VERIFY` unset. The launcher refuses a remote Docker context.

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

Docker's official Windows installer documentation supports `--installation-dir` and `--wsl-default-data-root`; these paths still require verification after a successful installation. Documentation: https://docs.docker.com/desktop/setup/install/windows-install/ and https://docs.docker.com/desktop/features/wsl/. Do not move existing WSL disks or Docker user data, edit installer caches/registry entries, or redirect Windows system folders to bypass the current failure.

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

## Finish the real browser integration check after Docker is ready

Use generated fictional donor and NGO accounts through `/register` and `/login`. Verify the database rows and BCrypt hashes, sign out and back in, refresh the local profile, attempt guest and cross-role dashboard URLs, and confirm the NGO stays unverified with privileged endpoints returning 403. Check wrong-password, duplicate email/license, bad-token and disabled-registration results. Do not approve an NGO or submit a medicine.

JWTs stay in React memory only. Reloading ends the browser session; logout clears it locally. There is no token revocation/session persistence mechanism, so a previously issued token remains valid until its server expiry. Review any future session strategy separately.

Stop the database without deleting its data:

```powershell
.\scripts\phase4-local-auth.ps1 -Action StopDatabase
git status --short
```

All previous branches and commits are preserved. Each changed file must be committed separately. Do not push, merge, or deploy this work without separate explicit approval and successful outstanding integration verification.
