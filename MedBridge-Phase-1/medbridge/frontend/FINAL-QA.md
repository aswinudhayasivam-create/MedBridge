# MedBridge Phase 3 final QA

Date: 2026-10-20. Branch: `feature/phase-3-final-qa`.
Base: `feature/phase-3-mobile-auth-ui` at
`607dbc4a9438322668f7acc637f8ff255f6aaa2b`.

## Outcome

The local production build passed the requested five-page, six-width matrix
(30 cases). No push, merge, deployment, production setting change, backend
change, or database access was performed. Authentication remains disabled.
This verifies the preview frontend, not readiness to operate a medicine service.

## Pages and responsive compatibility

Every entry below passed at 320, 375, 393, 430, 768, and 1280 CSS px, with an
852px browser viewport height. The in-app Chromium browser was used; these are
viewport simulations, not physical-device or cross-browser certification.

| Page | Result |
| --- | --- |
| `/` | Homepage, role links, section anchor, navigation and layout pass |
| `/register` | Donor/NGO selection, conditional fields, password controls and disabled submission pass |
| `/login` | Password controls, keyboard interaction and disabled submission pass |
| `/preview/donor` | Static donor workspace and page links pass |
| `/preview/ngo` | Static NGO workspace, verification notices and page links pass |

No horizontal overflow occurred in any matrix case. Expanded hamburger menus
and NGO registration fields were also checked at all five requested mobile/tablet
widths without overflow. Mobile donor/NGO cards remain equal-sized and 100px tall.
Registration branding remains approximately 150px tall at 320/375/393px and 128px
at 430px. All authored CSS, homepage, auth-page and dashboard source designs are
unchanged from the mobile UI base branch.

## Confirmed bugs fixed

1. CI's push filter named only two branches, so mobile UI pushes were skipped.
   It now matches `feature/phase-3-*`, including mobile UI and final QA branches,
   retaining the frontend/workflow path filters, Node 24, locked installs, audit
   steps and disabled-auth environment. This change becomes active for a branch
   containing it after an approved push; it does not retroactively run older pushes.
2. Escape did not dismiss open mobile navigation. A regression test reproduced
   the failure. Escape now closes it and restores focus to the toggle. The toggle
   now identifies the controlled navigation using `aria-controls`. Existing
   pointer interaction, link closing behavior and desktop design are preserved.
3. The browser requested `/favicon.ico` and received 404. A local SVG favicon
   using the existing Lucide HeartHandshake branding, with its license notice,
   is now referenced explicitly. The final browser requests `/favicon.svg`,
   which returns 200 with `image/svg+xml`.

## Accessibility and interaction checks

- One main landmark and one h1 on each page; input labels and meaningful page links present.
- No empty or placeholder-only hrefs were found in the five-page matrix.
- Enter opens the hamburger; Tab reaches its links with a visible focus outline;
  Escape closes it and restores toggle focus. Following a page link closes it.
- Donor/NGO selection works with pointer and keyboard; aria-pressed tracks the
  selected role, and NGO fields appear/disappear correctly.
- Password visibility controls work on both forms without submitting; the input
  value is retained. Only a fixed synthetic QA string was used and then cleared.
- Input and button keyboard focus styling remains visible. Mobile inputs retain
  47px height and 16px text. No focus trap was introduced for the disclosure menu.
- Full screen-reader, contrast, zoom/reflow, reduced-motion and physical-device
  accessibility audits have not been completed; this is not a WCAG conformance claim.

## Auth and data safety

`VITE_ENABLE_AUTH_SUBMISSIONS=false` remains the example default and CI setting;
the local build and tests also explicitly set it false. Registration/login source,
API client, permissions and dependency versions are unchanged.

Tests dispatch forced submit events to login, donor registration and NGO
registration and assert that the fetch spy is never called. Browser Enter-key
checks leave the submit buttons disabled. No account, medicine, redistribution,
patient information, medical information or production data was created or sent.

Unauthenticated `/dashboard`, `/dashboard/donor` and `/dashboard/ngo` all redirect
to `/login` in browser checks and regression tests, without API calls. Unsupported
preview roles redirect to the homepage. These checks do not exercise real JWT
sessions, backend authorization, NGO verification enforcement or a production database.

## Console, assets and network

The final local browser session returned no captured console warnings or errors.
A passive localhost preview-server observer recorded request method, path, status
and content type only; it did not record request bodies or change application
responses. Final application requests returned 200 or cache-validating 304.
The HTML, JavaScript, CSS and SVG favicon were also requested directly and returned
200 with the expected content types. No `/api/*` or non-GET application request
was recorded throughout browser testing. No final missing application assets were found.

The initial favicon 404 is retained in the baseline evidence and was fixed as
described above; it is not hidden from the report. Inline SVG icons and CSS art
do not require separate image downloads. External Google Fonts are separate from
the MedBridge origin; no font-related browser console failure was observed.
No complete cross-origin HAR trace was available, so that statement is limited
to observed console/rendering evidence.

The existing approved Vercel preview was sampled read-only and redirected to
Vercel sign-in. No sign-in, protection change or temporary share link was created.
Hosted preview-feedback/toolbar network checks therefore remain unverified.
Vercel SSO or feedback-toolbar traffic belongs to the hosting platform, not the
MedBridge API. No Vercel feedback script runs in the local build. Such requests
must not be counted as MedBridge account-submission or application-asset failures.
The final QA code has not been deployed, so local results are not hosted results.

## Installation, audit, tests and build

Node 24.21.0; npm 11.19.0; frontend version remains 0.3.0.

| Command | Result |
| --- | --- |
| `npm ci` | Passed with the existing lockfile |
| `npm audit` | 0 vulnerabilities |
| `npm audit --omit=dev` | 0 vulnerabilities |
| `npm run test:safety` | 10 passed |
| `npm test` | 25 passed across 4 test files |
| `npm run build` | Passed |

The suite includes 13 new final QA regression cases covering forced submissions,
all guest dashboard routes, both password controls, keyboard menu dismissal,
navigation-link closing, homepage preview links and unsupported preview roles.
The earlier Escape failure was observed before the fix and passed afterward.
The npm install warning about deprecated whatwg-encoding is not an audit advisory;
no dependency upgrade was applied for this QA task.

CI has not run remotely because this change set remains local. Each changed file
is committed individually; previous commits and older branches remain preserved.
Screenshots, raw matrix data, interaction evidence and network logs are stored in
the local workspace's `final-qa-evidence` folder outside this repository.

## Remaining work before production

- Obtain approval for any push that might trigger a preview; inspect remote CI
  after that push. Recheck the final commit on an authenticated hosted preview.
- Complete real-browser/device coverage (including Safari/iOS and Firefox) and
  a full accessibility audit. Tailwind v4's newer-browser requirements still apply.
- Keep submissions disabled until a separately authorized activation review.
  Validate real auth and NGO authorization only in an isolated test environment,
  never through production account/database creation during QA.
- The Phase 3 medicine, matching, transfer and redistribution workflows remain
  unavailable. Resolve the legal/clinical safeguards and production-readiness
  items already listed in README-PHASE3.md before enabling a real service.
- A merge or production deployment requires separate explicit owner approval.
