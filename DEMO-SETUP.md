# Offline-capable local demonstration setup

Repository: `F:\ASWIN\ASH\Projects\MED-Bridge\MedBridge-Phase3-GitHub`.
Branch: `feature/phase-5-8-demo-mvp`. Preserve all Phase 4 commits; do not push, merge or deploy without separate approval.

## Prerequisites

Use the existing Java 17, Maven, Node 24, Docker Desktop Linux engine and cached MySQL 8.4 image. Do not use Aiven, Render or production credentials. Dependencies must be installed/cached before going offline. No runtime CDN, geocoding or OCR service is required. The demo uses local/system fonts and a bundled botanical SVG pattern.

In a PowerShell terminal:

```powershell
Set-Location 'F:\ASWIN\ASH\Projects\MED-Bridge\MedBridge-Phase3-GitHub'
$env:JAVA_HOME = 'C:\Users\TEST\OneDrive\Documents\ChatGPT\MED-Bridge\phase4-tools\jdk-17.0.20.1+1'
$env:PATH = "$env:JAVA_HOME\bin;F:\Docker\Docker\resources\bin;" + $env:PATH
java -version
mvn -version
.\scripts\phase5-demo.ps1 -Action Database
.\scripts\phase5-demo.ps1 -Action Backend
```

The JDK path above is this machine's existing installation; on another machine select an installed JDK 17. These assignments affect only the terminal. Do not move Java installations or change system-wide variables.

In a second terminal:

```powershell
Set-Location 'F:\ASWIN\ASH\Projects\MED-Bridge\MedBridge-Phase3-GitHub'
.\scripts\phase5-demo.ps1 -Action Frontend
```

Open `http://127.0.0.1:5173/login`. Both HTTP services bind to loopback. MySQL remains bound only to `127.0.0.1:13306`, with existing persistent files on `F:\Docker\MedBridge-MySQL`. The generated database credentials/signing secret remain in ignored `.local-auth/development.env`; do not print, export, commit or replace that file.

The backend launcher keeps `APP_ALLOW_REGISTRATION=false` and independently opts into `APP_DEMO_WORKFLOWS=true`. Only the exact local-auth guard permits the demo migration and seed. The frontend enables submission and demo flags only for the loopback Vite development server. Normal development and all production builds keep hosted submissions disabled.

First demo startup adds five local-only detail/audit tables; it does not reset existing tables or delete prior records. Demo accounts are seeded once with BCrypt and fictional coordinates, and restart preserves approvals/data. If a seed account already exists its password and state are not silently overwritten. NGO verification is always fictional, not regulatory approval.

The photo step provides `/demo-synthetic-label.png` for download and re-upload. The supplied expiry is 2027-08-01. Use a future date at least three months away when demonstrating later. PNG/JPEG validation and metadata-stripping re-encoding run on the backend. OCR is NOT performed; manually confirm the candidate fields. No third-party image service is contacted.

## Tests

```powershell
# Java 17, with Docker available on PATH; normal defaults stay disabled:
Push-Location .\MedBridge-Phase-1\medbridge\backend
mvn -B -ntp verify
Pop-Location
# Real local MySQL demo journey, negative cases and concurrent reservations:
.\scripts\phase5-demo.ps1 -Action Test
# Original Phase 4 integration remains available:
.\scripts\phase4-local-auth.ps1 -Action IntegrationTests
Push-Location .\MedBridge-Phase-1\medbridge\frontend
npm run test:safety
npm test
npm audit
npm audit --omit=dev
npm run build
Pop-Location
git status --short
```

These tests use only fictional isolated data. Stop the interactive backend while running tests if resources are limited. Dependencies/images need to be cached for Maven/npm/Docker offline use; ordinary audit commands require registry access. The running application itself does not require internet.

Stop frontend/backend with Ctrl+C in their terminals. Stop MySQL without deleting data using `.\scripts\phase5-demo.ps1 -Action StopDatabase`. Do not use `down -v`, remove database files, reset tables, modify the startup guard, or connect to production to troubleshoot.

Sessions are intentionally in-memory: a browser refresh signs out, but signing in again restores persisted records. JWT rotation is not part of a routine restart; changing the signing secret would also invalidate encrypted demo handoff codes, which donors can replace by rescheduling.
