# Phase 3 dependency security remediation

Reviewed 2026-10-20 on `feature/phase-3-dependency-hardening`, based on
`feature/phase-3-website` at `e8ee685a37d2c79f0ab4f868bca9433ec499e871`.
Frontend version remains 0.3.0; React and React DOM remain 18.3.1.

## Audit results

| Audit | Before | After |
| --- | --- | --- |
| All dependencies | 15: 7 moderate, 6 high, 2 critical | 0 |
| Production dependencies (`--omit=dev`) | 2 moderate | 0 |

These are npm's vulnerable-package counts, including inherited findings;
they are not counts of unique CVEs. No audit suppression, ignored advisories,
forced fixes, package aliases, or dependency overrides were used.
No remaining advisories were reported by npm at the time of validation.
A clean audit is a point-in-time result, not a guarantee of absence of defects.

## Resolved versions and compatibility

| Package | Original resolved version | Hardened version |
| --- | --- | --- |
| react-router-dom / react-router | 6.30.6 | 7.18.4 |
| Vite | 5.4.21 | 8.3.4 |
| Vitest | 2.1.9 | 5.0.5 |
| @vitejs/plugin-react | 4.7.0 | 5.2.0 |
| Tailwind CSS | 3.4.19 | 4.3.3 |
| @tailwindcss/postcss | absent | 4.3.3 |
| PostCSS | 8.5.31 | 8.5.31 |
| braces | 3.0.3 | absent |
| tinypool | 1.1.1 | absent |

The upgraded direct dependencies are pinned. The committed npm lockfile fixes
the full dependency tree and contains the Linux optional native packages needed
by GitHub Actions as well as the local Windows packages. Use `npm ci` to install.
The original untracked generated lockfile was preserved outside the repository
before regeneration.

Local validation used Node 24.21.0 and npm 11.19.0. Vitest 5 requires Node
22.12+ on supported release lines and Vite 6.4+. The package engine range follows
Vitest's supported Node lines; CI now selects Node 24. Vite 8 uses Rolldown/Oxc.
The existing simple Vite configuration remains compatible and is unchanged.
Router 7 supports React 18 and keeps the react-router-dom compatibility exports,
so existing declarative routes, Link, Navigate and MemoryRouter imports remain.
No framework-mode, SSR, backend, or authentication migration was introduced.

## Tailwind v4 and design preservation

The PostCSS integration explicitly uses @tailwindcss/postcss. Autoprefixer is
removed because v4 handles vendor prefixing. The CSS entry imports Tailwind v4
and explicitly loads the unchanged tailwind.config.js through @config, retaining
the existing colors, content paths, and DM Sans/Manrope theme fonts.

Compatibility base rules preserve v3 border and input-placeholder colors. All
existing custom design rules, typography, layout, and responsive media rules
after the Tailwind entry directives are unchanged. Do not remove these
compatibility rules without checking the design.

Tailwind v4 requires modern browsers: Safari 16.4+, Chrome 111+, Firefox 128+.
This is a browser-support change from v3; older browsers are not validated.
If support for older browsers is required, retain v3 only with an explicit
assessment of its unresolved build dependency advisories, or replace its
build/reset functionality with separately reviewed CSS tooling.

## braces advisory

GHSA-vfj7-8cjw-p6xm affects braces through 3.0.3 and has no patched release as of
this review. Deeply nested brace patterns can exhaust the Node call stack.
The original paths were Tailwind v3 -> chokidar and fast-glob/micromatch ->
braces. This was build/development tooling exposure rather than a browser
runtime dependency, but malicious source patterns or untrusted build inputs
could still interrupt a build. Merely trusting current inputs is not a fix.

Migrating to Tailwind v4 removes those paths and braces from the installed tree;
the advisory itself remains unpatched upstream. Alternative approaches would
be an upstream reviewed depth-limit patch or replacing the affected globbing
toolchain; forcing an incompatible override or suppressing npm audit would not
constitute remediation. The upgraded Vitest tree also removes tinypool and its
critical findings.

## Validation

- `npm ci`: passed; package-lock.json unchanged by the clean install.
- `npm audit`: 0 vulnerabilities.
- `npm audit --omit=dev`: 0 vulnerabilities.
- `npm run test:safety`: 10 tests passed.
- `npm test`: 12 tests passed across 3 files, including a new check that a
  forced form-submit event cannot bypass disabled login submissions.
- `npm run build`: passed with VITE_ENABLE_AUTH_SUBMISSIONS=false.
- Local browser checks: homepage, login, registration, donor preview, NGO preview,
  registration role switching, and navigation between screens passed.
- At a 1280px desktop viewport, all five routes matched the original build's
  visible DOM element geometry and sampled font, color, spacing, display, and
  border styles, with no horizontal overflow or browser console errors.
- Responsive CSS rules are unchanged; separate mobile device/browser coverage
  has not been performed. Older browser support is limited as documented above.
- The original baseline was the prior validated Phase 3 production build;
  no production website was contacted for these checks.

CI uses Node 24, npm ci, both audit commands, safety tests, mocked component/API
tests, and the build. It includes the security branch in its push filter, but
has not been run remotely because no push is authorized for this change set.

## Scope and release controls

VITE_ENABLE_AUTH_SUBMISSIONS remains false by default and in CI. No real login,
registration, medicine submission, user account, or database test data was sent.
The original 27 Phase 3 commits remain intact in the branch ancestry. Each new
commit contains exactly one changed file, including the package manifest,
lockfile, configuration, tests, workflow, and documentation.

No push, merge, deployment, backend change, Render/Aiven modification, Vercel
setting change, or production website change is part of this remediation.
Pushing may automatically trigger a Vercel preview and requires explicit owner
approval. A production deployment requires separate explicit approval.

## Sources

- https://github.com/advisories/GHSA-vfj7-8cjw-p6xm
- https://reactrouter.com/changelog
- https://vitest.dev/guide/migration/
- https://vite.dev/guide/migration
- https://tailwindcss.com/docs/upgrade-guide
