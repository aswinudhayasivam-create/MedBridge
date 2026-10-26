# MedBridge — Final submission, UI fidelity, and protected release checklist

> Submission target: **October 26, 2026, 08:00 Asia/Kolkata**. This checklist records acceptance criteria, **not** an assertion that the hosted application has passed them. This is a college demonstration using only fictional data; no real medicine collection or transfers.

## 1. Source, structure, and GitHub presentation

- [x] Complete working source is on `feature/tidb-hosted-review`; nested project roots intentionally remain `MedBridge-Phase-1/medbridge/frontend` and `MedBridge-Phase-1/medbridge/backend`. **Do not move either root** during release; Render Docker context and Vercel settings depend on them.
- [x] Default `main` README now describes the current project, links to the hosted frontend and complete source branch, and distinguishes local demo identities from private hosted-review credentials.
- [x] GitHub repository About website field points to `https://med-bridge-ruddy.vercel.app/`. This is an **existing frontend URL**, not proof of complete hosted workflow availability.
- [x] Existing screenshots and both original PDF references are committed; retain them as project design evidence.
- [x] Backend/frontend CI previously passed on commit `471701f099b106f0d50476e435b8ef5895245c44`; subsequent workflow/release-file changes require fresh check status.
- [ ] Open the draft final-release pull request and review changed files. **Do not merge** until the hosted review is proven and Vercel production impact is understood.
- [ ] Only after acceptance, integrate into `main` without force-push, squash, or losing commits. Preserve the **one changed file per authored commit** workflow. Git merges can have multiple files as part of the merge result; do not misrepresent them as one-file authored commits.
- [ ] Check GitHub default README and About link from a signed-out browser; pin MedBridge on the owner's profile manually if desired.

## 2. Exact uploaded UI references — donor and NGO

Reference PDFs already in this repository:

- [Donor journey — 11 pages, 10 steps](design-reference/MedBridge-How-It-Works-Donors.pdf)
- [NGO journey — 8 pages, 7 steps](design-reference/MedBridge-How-It-Works-NGOs.pdf)

The PDFs are **concept previews**, not completed deployed screenshots. Actual UI is React components in `frontend/src/demo/DemoApp.jsx` with style rules in `frontend/src/demo/demo.css`; no flat PDF screenshot should be substituted for interactive controls. Existing real component screenshots are catalogued in [demo-screenshots](demo-screenshots/README.md).

**Visual acceptance:** compare desktop and phone views with the PDFs: dark forest-green navigation/sidebar (`#0a3027`), cream background (`#fcfcf7`), yellow/marigold primary action (`#f5c748`), mint informational cards (`#e1f3e8`), restrained white cards, serif section headings, visible status tags, coherent spacing and responsive phone navigation. Keep readable text, large touch targets, keyboard focus indicators, usable forms and no horizontal overflow. Test 320, 375, 393, 430, 768 and 1280 CSS px.

### Donor journey (PDF step → live expected view)

| Step | PDF behavior | Live acceptance |
| --- | --- | --- |
| 1 | Donor login | Email/password login, safe error state and correct donor role |
| 2 | Dashboard | Actual persisted donation statuses, not hard-coded PDF counts |
| 3 | New donation basics | Name, form, quantity, batch, expiry; reject expiry within 3 months |
| 4 | Strip-label photograph | Accept synthetic image, show preview; assisted in-browser OCR suggests name/batch/expiry and the donor explicitly confirms; OCR makes no safety claim |
| 5 | Condition and submit | Sealed/storage declaration; real API creates a fictional listing awaiting admin review |
| 6 | Admin review | Admin must approve/reject before publication; no automatic approval |
| 7 | Approved/listed | Approved, eligible, unexpired records become visible only to verified nearby NGOs (10 km) |
| 8 | NGO request | Donor sees verified NGO identity, quantity and note; accept/decline with server-side ownership |
| 9 | Collection/handoff | Fictional schedule and private donor-only 4-digit code; NGO enters code; **simulated**, not physical |
| 10 | Notifications | Persisted approval/request/handoff updates and clear timestamps |

### NGO journey (PDF step → live expected view)

| Step | PDF behavior | Live acceptance |
| --- | --- | --- |
| 1 | NGO sign-in | Fictional NGO login; new NGO begins as pending, not self-verified |
| 2 | Verified access | Only administrator-approved NGO can browse/request; pending NGO denied |
| 3 | Browse approved donations | Cards with real medicine, quantity, distance, expiry and label evidence; approved and unexpired only |
| 4 | Request a donation | Quantity validation and message for donor; no duplicate/oversubscribed requests |
| 5 | Request status | Persisted timeline for pending, accepted, declined and completion states |
| 6 | Collection/handoff | NGO enters donor code; expired/wrong/too many attempts rejected; record simulated completion |
| 7 | Notifications | Accepted request, relevant new listings, fictional collection outcome |

**Source/reference discrepancy to communicate during viva:** the PDFs' proposed OCR and human medical-clearance language are concepts. Current code performs **assisted in-browser OCR suggestion with explicit donor confirmation**, and fictional administrator review does **not** equal regulatory/clinical verification. Remove promises such as guaranteed review times unless actually implemented and supported.

## 3. Database and backend gates (actual hosted evidence required)

- [x] TiDB Starter MySQL-compatible instance created at documented $0 monthly limit; `medbridgereview20261026` created separately from `sys` and `test`.
- [x] Authenticated TiDB TLS identity/compatibility probe tested CHECK rejection (four cases), FK orphan/cascade, ENUM, upsert, IDs and row locking; disposable tables cleaned. Previous confirmed application table count: **0**.
- [ ] Securely create the runtime account `3vyjDjyxfuRzVcT.mb_review_app` with `REQUIRE SSL`, distinct strong password and **only** `SELECT, INSERT, UPDATE, DELETE` on `medbridgereview20261026.*`; verify `SHOW GRANTS` and denied DDL, other schemas and grant options.
- [ ] Explicitly verify administrator/root login only once using current credentials; if error 1045 recurs, diagnose exact account rather than endlessly resetting or retrying. Avoid password-copy confirmation loops; securely pre-save an independently generated runtime password if the local workflow supports it.
- [ ] Run the reviewed TiDB initializer exactly once after all safety gates, maintaining its attempt lock; never assume SQL DDL rolled back after failure.
- [ ] Verify exactly 11 named application tables, 4 enforced CHECKs, 11 FKs, matching `review_environment` marker and zero unapproved rows. Runtime account must not execute DDL.
- [ ] Verify actual TiDB-backed application transactions, two competing NGO requests, owner checks, rejected invalid submissions, rate limits and account isolation.
- [ ] Build protected backend from exact approved SHA on existing **Render Free** `medbridge-backend-protected`; use TiDB via `VERIFY_IDENTITY` and JVM system root trust, private review secrets and `spring.sql.init.mode=never`. No Aiven CA file in review service. Auto-deploy remains off.
- [ ] Confirm protected review readiness/API and refusal of registration, old public `@medbridge.test` identities, expired reviewer keys, cross-role operations and incorrect DB configuration.

## 4. Frontend hosting, privacy and end-to-end checks

- [ ] Deploy the **review-mode** React build to a separately protected Vercel review project, not the existing production domain, with `VITE_ENABLE_REVIEW=true` and exact `VITE_REVIEW_API_ORIGIN`. Never put any credentials/invitation secrets in `VITE_*`.
- [ ] Confirm evaluators can pass the intended preview protection under the Free plan; never publish an accidentally unrestricted working review login.
- [ ] Test real browser → Render API → TiDB: login, donor submit, admin approval, verified NGO discovery/request, donor accept/schedule, private code confirmation, notifications and refresh/backend restart persistence.
- [ ] Verify desktop and mobile screenshot matrix against each PDF step; record screenshots with synthetic data and **never** expose live handoff codes, passwords, signed URLs or invite keys.
- [ ] Check DevTools console/network for 4xx/5xx, routing refresh errors, missing images, CORS failures, stale API URLs and layout overflow. Verify frontend cannot make unsafe direct DB requests.
- [ ] Prepare instructor-facing URLs and an accurate demo walkthrough. If hosted path remains blocked, demonstrate the **tested local MySQL workflow**, explicitly describing it as local rather than a deployed site.

## 5. Safety / release decision

- Do **not** change or delete Aiven, resume the old Phase 2 Render backend, alter billing, add cards, or switch to paid resources.
- Do **not** publish private hosted-review credentials in GitHub. Intentionally public demo credentials in [DEMO-LOGIN.md](../DEMO-LOGIN.md) work **only** in exact isolated local mode.
- This review build is hard-stopped at **2026-11-23 00:00 IST (2026-11-22T18:30:00Z)** - access ends at the end of 22 November. Extending that cutoff, opening registration or activating real donations requires a separate security/product release.
- A passing unit test or a Vercel `READY` build alone is **not** a completed website. Declare hosted success only after the full test journey and safety gates pass.
- Do not promise zero bugs. Document remaining limitations honestly and prevent critical/security regressions with repeatable tests.
