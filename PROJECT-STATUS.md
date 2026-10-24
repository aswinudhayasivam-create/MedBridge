# Project status and hosting readiness

Verified locally: 24 October 2026. Working branch: `feature/phase-5-8-demo-mvp`. Starting HEAD for this preparation: `0c2527afba61e49e7f51ba9345b2ccf64143556c`. All 70 original Phase 5–8 commits and original Phase 4 history are preserved. A final full-SHA single-file commit manifest accompanies the handoff.

## Environment status

| Environment | Status |
| --- | --- |
| Original local demo | Verified; original four accounts, persisted history, pending restrictions and complete REST integration retained |
| Hosted-like loopback review rehearsal | Verified locally against a separate temporary database; not deployed |
| Hosted Vercel → Render → MySQL review | **Pending approval and infrastructure; not verified online** |
| Existing Phase 2 / protected Render | Not modified; no claim of current health or new deployed protection |
| Aiven recovery | Not accessed, powered on or modified |
| Vercel production | Not modified |

## Actual validation

| Check | Final result |
| --- | --- |
| Java 17 Maven tests/package | 124 passed |
| New review policy tests | 22 passed |
| Local demo / original authentication / disposable review MySQL | 3 / 1 / 5 passed; schema checks passed |
| Vitest | 51 passed |
| Node safety tests | 16 passed |
| npm audit / npm audit --omit=dev | 0 vulnerabilities in both |
| Default production / explicit review builds | Passed, including deliberately misconfigured flag regressions |
| Existing local browser journey | All four public local logins, donor history, admin overview, pending restrictions and completed NGO timelines verified; complete local REST journey passed |
| Review browser journey / restart persistence | Fresh donor → admin → NGO → donor → code → completion and notifications passed; fresh login and unchanged database snapshot after restart |
| Required viewport widths | 320, 375, 393, 430, 768, 1280 px; 120 checks across 20 shared review pages, zero horizontal overflow; original PDF/CSS evidence preserved |
| Markdown relative links / CI lint / secret and history review | Relative links and actionlint passed; private values excluded; final single-file history checked at handoff |

Development failures were fixed before final verification: the login limiter incorrectly applied its stricter counter to non-login calls, and the reused integration fixture looked up a local-domain pending account instead of its separate review identity. Full-suite testing also caught early Spring guard construction, which was corrected with an explicit bean factory. Final totals above refer to the corrected code. No test bypass, weaker isolation or production request was used to make checks pass.

## Go / no-go

Local code/documentation readiness: Ready for review and a separately approved push; no hosted success claimed. Online deployment/exposure: **NO-GO until the individually approved database, CA/configuration, backend deployment, protected frontend and real hosted verification steps in [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md) pass**. Free MySQL quota and protected evaluator access are explicit unresolved prerequisites. No working hosted credentials or review URL are published.

## Documents

[Root overview](README.md) · [local credentials](DEMO-LOGIN.md) · [setup](DEMO-SETUP.md) · [walkthrough](DEMO-WALKTHROUGH.md) · [architecture](SYSTEM-ARCHITECTURE.md) · [API](API-DOCUMENTATION.md) · [deployment](DEPLOYMENT-GUIDE.md) · [limitations](SECURITY-AND-DEMO-LIMITATIONS.md).

Previous detailed local/PDF evidence remains in [PHASE5-8-DEMO-REPORT.md](PHASE5-8-DEMO-REPORT.md). This preparation preserves the original screenshot/PDF set and the completed functional implementation. Additional local rehearsal screenshots are linked in the final handoff; they do not represent a hosted deployment.


## Actual review rehearsal evidence

These are **local** review-profile captures, not an online deployment or a published hosted review URL:

- [Private login with no public local selectors](docs/review-screenshots/review-login-local.jpg)
- [Persisted review donor completion after restart](docs/review-screenshots/review-donor-after-restart.jpg)
- [120 measured viewport checks](docs/review-screenshots/review-layout-checks.json)

The file-upload automation exceeded its requested timeout during rehearsal; it eventually completed, and the remaining complete browser journey succeeded. No security timeout or guard was weakened. Both local HTTP backends and both MySQL listeners remain loopback-only. Temporary browser fixture copies were removed; the generated local rehearsal environment file remains private and Git-ignored.
