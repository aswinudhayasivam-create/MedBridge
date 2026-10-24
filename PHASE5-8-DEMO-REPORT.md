# MedBridge Phases 5–8 — isolated functional demonstration

Verified locally on 24 October 2026. Working branch: `feature/phase-5-8-demo-mvp`, based on unchanged Phase 4 head `5fc9e58ff5050a0f9f59a044921141036e533648`. Nothing was pushed, merged, deployed, activated in production or sent to Aiven. Render and Vercel settings were not touched.

The real local browser → Spring Boot → MySQL journey was completed: Meera submitted a synthetic label/donation, the administrator approved it, Divya requested two of four strips, Meera accepted and scheduled a simulated collection, a wrong code was rejected, and the correct donor-only code completed the simulated collection. The donor and NGO dashboards and notifications reflect that persisted event. A backend restart preserved accounts, verification states, donation/reserved quantities, completed requests and notifications without reseeding duplicates.

## Run the demo

See [DEMO-SETUP.md](DEMO-SETUP.md) for the exact installed JDK 17 path and prerequisite checks. From the repository, start Docker Desktop and use separate terminals:

```powershell
.\scripts\phase5-demo.ps1 -Action Database
.\scripts\phase5-demo.ps1 -Action Backend
# Second terminal:
.\scripts\phase5-demo.ps1 -Action Frontend
```

Open `http://127.0.0.1:5173/login`. Login details are intentionally public fictional fixtures in [DEMO-LOGIN.md](DEMO-LOGIN.md). Every selector only autofills: click Login to authenticate against MySQL. Admin is available only through the guarded local demo entry; ADMIN self-registration remains forbidden. Use [DEMO-WALKTHROUGH.md](DEMO-WALKTHROUGH.md) for the complete viva sequence and technical answers.

## Screens and data behavior

- Shared fictional-account login; donor/NGO/pending-NGO/admin selectors, password visibility and loading/errors.
- Donor dashboard, four-step donation wizard, PNG/JPEG upload and explicit manual label confirmation, condition/declaration/review, persisted donation timelines, NGO request decisions, collection scheduling, donor-only code retrieval and notifications/read state.
- Verified NGO dashboard, approved nearby listing discovery, radius/category/shelf-life filters, request details/quantity/note, persisted request and collection timelines, protected code confirmation and notifications.
- Pending NGO dashboard and application submission; verified browse/request/collection actions remain server-side forbidden until admin verification.
- Admin overview/statistics, account/NGO application overview, fictional NGO approval/rejection/revocation, medicine photo/declaration review and approval/rejection, request monitoring and audit events.
- Reusable cards, fields, badges, timelines, manual/native date entry, desktop sidebar and mobile bottom navigation with a More menu exposing every role's pages. Data is scoped to route and authenticated session, avoiding cross-screen data flashes.

Dashboard numbers derive from actual MySQL records, not the illustrative PDF counts. The existing two Phase 4 fictional accounts are preserved alongside the four new seed accounts; account totals may therefore exceed four. Integration tests can leave fictional notification/audit history and unused synthetic uploads; those are actual persisted events, not invented statuses. Test medicine/request fixtures are cleaned separately from the retained browser walkthrough record.

## Backend API

The existing `/api/auth/login`, `/api/auth/me` and profile-only `/api/auth/local-mode` remain the authentication foundation. Every `/api/demo/*` action requires a valid authenticated account, the explicit demo flag and the unchanged exact local-auth guard; role, current verification and ownership are checked on the server.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/demo/summary` | Current role's records/statistics; admin accounts/audit |
| GET / POST | `/api/demo/medicines` | Donor/admin records; donor submission |
| GET | `/api/demo/medicines/{id}/events` | Owned donation timeline; admin review access |
| POST / GET | `/api/demo/photos` / `/api/demo/photos/{id}` | Validated synthetic upload / authorized image retrieval |
| POST | `/api/demo/medicines/{id}/review` | Admin-only fictional approval/rejection |
| GET | `/api/demo/browse?radius=10&months=0&category=` | Verified-NGO approved, unexpired, available discovery |
| POST | `/api/demo/medicines/{id}/requests` | Verified NGO's quantity/note request |
| GET | `/api/demo/requests` | Owned or admin-monitored requests |
| POST | `/api/demo/requests/{id}/decision` | Owning donor's accept/decline |
| POST | `/api/demo/requests/{id}/schedule` | Owning donor's simulated schedule/code rotation |
| GET | `/api/demo/requests/{id}/code` | Donor-only decrypted display; no-store response |
| POST | `/api/demo/requests/{id}/collect` | Verified requesting NGO's attempt-limited confirmation |
| POST | `/api/demo/ngo-application` | NGO's fictional verification application |
| POST | `/api/demo/ngos/{id}/review` | Admin-only fictional NGO review |
| GET | `/api/demo/notifications` | Current account's notification records |
| POST | `/api/demo/notifications/{id}/read` | Ownership-checked read transition |

Medicine acceptance uses a row lock and conditional reserved-quantity update. Two concurrent three-unit acceptances against a four-unit listing produce one success and one conflict; reserved quantity remains three. Collection cannot proceed without an accepted request, a scheduled collection, a verified requesting NGO and a correct unexpired code. Five incorrect attempts lock the code; the attempt counter persists despite rejection. Repeated completion is rejected. The four-digit verifier is BCrypt; donor redisplay uses AES-GCM ciphertext derived from the existing local signing secret. Neither plaintext code nor a JWT is stored in the new database tables or printed by application logging.

## Minimal local schema extension

Existing `users`, `ngo_profiles`, `medicines`, `requests`, `notifications`, their mappings and existing `schema.sql` remain. New guarded, additive `CREATE TABLE IF NOT EXISTS` definitions add:

1. `demo_medicine_details`: form/unit, declarations, photo reference, review note/time and reserved quantity.
2. `demo_photos`: owned validated/re-encoded synthetic image bytes.
3. `demo_request_details`: requested quantity/note, simulation schedule, hashed/encrypted code, expiry, attempts and completion timestamp.
4. `demo_events`: actor/recipient audit and timeline events.
5. `demo_ngo_applications`: pending/approved/rejected fictional verification state.

The runner validates the exact existing local-auth environment before running these statements or provisioning accounts. It does not run on an ordinary hosted configuration or remote JDBC target. It does not reset or import the production schema. Persistence stays on `F:\Docker\MedBridge-MySQL`, published only on `127.0.0.1:13306`.

## Validation

| Check | Result |
| --- | --- |
| Java 17 `mvn -B -ntp verify` | 30 tests passed; 0 failures/errors/skips; backend packaged |
| Explicit `DemoMySqlIT` | 3 passed: complete real HTTP/MySQL journey and negatives/concurrency, same-role ownership/privacy, Haversine sanity |
| Original `LocalAuthMySqlIT` | 1 passed; original Phase 4 security behavior retained |
| Testcontainers schema checks | Passed in normal suite and rerun by integration launchers |
| Vitest | 43 passed across 6 files, including account selection, pending NGO denial, filters, notifications, manual dates and route/session isolation |
| Node safety suite | 15 passed, including a compiled-build test with deliberately true flags |
| npm audit / production-only audit | 0 vulnerabilities in both; no dependency upgrades |
| Production build | Passed with normal disabled flags |
| Deliberately misconfigured build | Passed with true auth/demo flags, development NODE_ENV and local-auth mode; no public demo credentials in compiled HTML/JS/CSS |
| Workflow actionlint / ShellCheck | Passed; Phase 5 triggers and isolated demo integration prepared locally |
| Browser end-to-end | Completed real donor → admin → NGO → donor → code → simulated receipt flow |
| Restart persistence/idempotence | Local snapshot unchanged across restart; browser subsequently displayed completed record |
| Responsive UI | Required widths 320, 375, 393, 430, 768, 1280 checked; saved layout evidence has no horizontal-overflow failures |

The browser found a transient route-data/key warning, which was fixed and regression-tested. A fresh login → notifications → browse check then had no console warnings/errors. The deliberate wrong-code request returned its expected rejection; it was not an unexpected application failure. No Vercel feedback or hosted service traffic was involved in local QA. No production registration or login request was made.

The dependency/runtime services are local and cached; the application has no remote OCR/geocoding dependency. A physical internet-disconnect test was not performed. Registry audits and first-time dependency/image installation still require internet. Local/system font fallbacks remain readable offline.

## PDF comparison checklist

All 19 pages were rendered and visually inspected, including desktop and phone screenshots, before implementation. Originals are preserved under [docs/design-reference/](docs/design-reference/). They are design/workflow references, not authority for infrastructure changes. No PDF screenshot is used as a flattened interface.

| Reference page | Implemented comparison |
| --- | --- |
| Donor 1 | Forest branding, cream/mint/yellow palette, serif hierarchy and botanical direction |
| Donor 2 | Real login form plus required local-only fictional account selector |
| Donor 3 | Database-derived dashboard cards and recent donations |
| Donor 4 | Responsive basics form with category/unit and three-month backend rule |
| Donor 5 | Synthetic label preview and explicit confirmation; honest manual fallback instead of fake OCR |
| Donor 6 | Sealed/storage/declaration controls and final review |
| Donor 7 | Submitted status/timeline plus actual admin review screen |
| Donor 8 | Persisted approval and verified-NGO discovery eligibility |
| Donor 9 | Correct donor's NGO organization/reference/verification/quantity/note and decisions |
| Donor 10 | Simulated schedule, private donor code and protected NGO confirmation |
| Donor 11 | Persisted notification history and read actions |
| NGO 1 | Same coherent visual family; no synthetic PDF-cover screenshot interface |
| NGO 2 | Real GreenCross login through shared fictional selector |
| NGO 3 | Verification panel and database-derived counts; pending NGO restricted |
| NGO 4 | Approved nearby cards with radius, expiry and category filters |
| NGO 5 | Detailed quantity/note request form with backend constraints |
| NGO 6 | Persisted request/decision/schedule/completion timeline, including declines |
| NGO 7 | Scheduled synthetic collection and masked four-digit confirmation input |
| NGO 8 | Real request, schedule and completion notifications |

This is a close implementation of the design direction, not a claim of pixel-perfect identity. Intentional differences include safety text, four account selectors, extra validation fields, a mobile More menu and Georgia/system typography. Illustrated statistics/times are replaced by actual records/timestamps. No real donor addresses are collected or exchanged. Receipt is the same persisted simulated event as collection, not a separate real delivery claim. Scheduling is illustrative: the demo can confirm a correctly coded simulated handoff before the displayed appointment time.

## Screenshots and remaining limitations

Actual browser captures are provided under `docs/demo-screenshots/`; filenames describe the walkthrough stage. They show real React screens backed by MySQL, not fabricated reference images. Some captures show different notification counts as the real journey and integration checks produced additional fictional events.

- OCR was intentionally not implemented: no Tesseract executable/trained-data installation was found, and no external/paid OCR service was used. The manual fallback is labeled throughout.
- This is a local college demonstration, not production account activation or medical clearance. Production compliance, abuse prevention and durable/revocable sessions remain out of scope.
- No physical internet-disconnect test or assistive-technology audit was performed; keyboard semantics, labels, focus styles and responsive overflow were checked, with manual date entry available.
- Historical fictional test notifications/audit entries and abandoned synthetic image uploads may remain locally. No automatic purge/reset or destructive cleanup was added.
- A given NGO cannot submit another request for the same listing after a terminal decision, because the existing unique medicine/receiver constraint is intentionally preserved. Duplicate active requests are prevented; terminal re-requesting would need a separate migration/design.
- CI is prepared and linted, but this branch has not been pushed; no Phase 5 GitHub Actions run or Vercel preview is claimed.

## Git review

Each changed file, including each PDF, screenshot, configuration and documentation file, is committed separately. Original Phase 4 history is preserved. Use `git log --reverse --name-only 5fc9e58..HEAD` to inspect the exact sequence; the final handoff includes a full SHA/path manifest. The report's own commit cannot contain its own final hash, so obtain final HEAD with `git rev-parse HEAD`.

Stop here for review. Any push must be separately approved and target only `feature/phase-5-8-demo-mvp`; an automatic preview must keep auth/demo guards disabled. No push, merge, production deployment, hosted account activation, Render/Vercel change or Aiven power-on/database modification is authorized by this report.
