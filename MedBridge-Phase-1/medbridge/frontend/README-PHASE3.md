# MedBridge — Phase 3 website preview

This is a **development preview**, not a live medicine exchange. It uses the existing React 18/Vite/Tailwind stack and the current Spring Boot authentication API. **No medicine listings, transfers, payments or real accounts are created by default.**

## Available screens

- `/` — landing page, role guidance and clear safety notices
- `/register` — donor and NGO registration; NGO organization fields are conditional
- `/login` — token-based sign-in form
- `/preview/donor`, `/preview/ngo` — static, public dashboard previews; no mocked API data is presented as real
- `/dashboard` — authenticated workspace, restricted by the response's server-provided role

## Run / verify

```bash
npm install
npm test
npm run build
npm run dev
```

## API integration, disabled until explicit approval

Copy `.env.example` to `.env.local`. By default `VITE_ENABLE_AUTH_SUBMISSIONS=false`, so forms are disabled and **cannot call** `/api/auth/register` or `/api/auth/login`.

After backend and legal review, the owner may explicitly opt in to real account submissions by configuring:

```dotenv
VITE_ENABLE_AUTH_SUBMISSIONS=true
VITE_API_BASE_URL=https://medbridge-backend-43wz.onrender.com
```

Do **not** enable this for a publicly accessible Vercel preview without approval. With no base URL in local development, Vite proxies `/api` to `http://localhost:8080`. CORS on the Spring backend must explicitly permit the deployed frontend's origin. The backend returns a signed JWT plus a user object; the frontend keeps the token **in memory only** (not in localStorage). Refreshing the page logs the user out. JWT expiry/reauthentication, device session management and refresh tokens need further design before public launch.

The security fix deployed in Phase 2 returns `401` for `/api/auth/me` when no token is supplied. Unverified NGO accounts must not receive `ROLE_NGO` privileges. This frontend treats server-provided `verified` status as informational only; the backend must continue enforcing authorization.

## Production-readiness gaps

- No live medicine handling or matching; requires clinical oversight, legal verification and safeguards.
- Finish email verification, rate limiting, password resets, CSRF/cookie strategy if sessions change, audit logging and account accessibility review.
- Add backend integration tests for the full registration → login → role restriction flow in an isolated environment.
- Enforce admin review of NGO licenses and eligibility; frontend access controls are not a substitute for API authorization.
- Configure real error pages, observability, availability, privacy policy, terms, and accessible design audits.
- The preview must not auto-deploy to Vercel without explicit owner consent. GitHub integration can automatically create previews when a new branch receives commits.

## Security limitations

The project remains a Phase 3 preview. HTTPS is required for external APIs; JWT is not persisted; no sample records or test accounts are created. Vite `VITE_*` variables are publicly embedded into frontend assets: never store secrets in these variables. Source-control commits are separate from approval to deploy.
