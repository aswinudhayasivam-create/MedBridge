# API documentation

All workflow records are fictional. The same controllers and transactional service serve the exact local demo and separately verified review environment. There is no public medicine transfer, payment, account signup or reset API.

## Authentication and transport

Local demo: relative `/api` through the exact loopback Vite server; handshake `GET /api/auth/local-mode`. Review: explicit review build → configured API origin; private `X-MedBridge-Review-Key` header plus `Authorization: Bearer <JWT>` after login. A review capability handshake precedes frontend credential/token transmission. Hosted origins require HTTPS; the exact loopback rehearsal pair is the only HTTP review exception. Never put either secret in a URL, screenshot or Git file.

| Method / path | Permission / result |
| --- | --- |
| `GET /api/health` | Minimal liveness; not proof of database or workflow health |
| `GET /api/auth/local-mode` | Only local-auth profile; exact existing isolation guard |
| `GET /api/auth/review-mode` | Review profile, private invitation and ready/unexpired environment |
| `GET /api/review/ready` | Minimal review database readiness, no invitation required; unavailable after cutoff |
| `POST /api/auth/login` | BCrypt authentication; review requires private invitation and separate review identity |
| `GET /api/auth/me` | Valid token, identity policy and current stored profile |
| `POST /api/auth/register` | Disabled in review and by default; not used for evaluation |

Login request: `{ "email": "<privately supplied review identity>", "password": "<private password>" }`. Response contains `token`, `tokenType`, `expiresInSeconds`, and the safe user profile. The four public `@medbridge.test` identities are accepted only by the original verified local environment, never review. Previously valid tokens do not bypass identity or expiry checks.

## Shared fictional workflows

| Method | Path | Server permission / action |
| --- | --- | --- |
| GET | `/api/demo/summary` | Current account summary; administrator additionally receives account/audit overview |
| GET | `/api/demo/medicines` | Owning donor's records or admin screening records |
| POST | `/api/demo/medicines` | Donor, valid owned photo, explicit confirmations, sealed/declaration, >=3-month expiry |
| GET | `/api/demo/medicines/{id}/events` | Owning donor or administrator |
| POST | `/api/demo/photos` | Donor; multipart PNG/JPEG <=1 MB, bounded dimensions, re-encoded without metadata |
| GET | `/api/demo/photos/{id}` | Authorized owner/reviewer/eligible associated viewer |
| POST | `/api/demo/medicines/{id}/review` | Administrator; `{approve, note}` |
| GET | `/api/demo/browse?radius=10&months=0&category=` | Verified NGO; approved, available, unexpired, <=10 km; no exact home coordinates |
| POST | `/api/demo/medicines/{id}/requests` | Verified NGO; `{quantity, note}`, available quantity and duplicate checks |
| GET | `/api/demo/requests` | Owning donor/requesting NGO or administrator |
| POST | `/api/demo/requests/{id}/decision` | Owning donor; `{approve, note}`, transactional reservation |
| POST | `/api/demo/requests/{id}/schedule` | Owning donor; `{when, note}`, future illustrative schedule within seven days |
| GET | `/api/demo/requests/{id}/code` | Owning donor only, no-store; never share in public documentation |
| POST | `/api/demo/requests/{id}/collect` | Verified requesting NGO; `{code}`, expiry/five-attempt limit/repeat-completion checks |
| POST | `/api/demo/ngo-application` | NGO's fictional application/reapplication |
| POST | `/api/demo/ngos/{id}/review` | Administrator; `{approve, note}`, fictional verification/rejection |
| GET | `/api/demo/notifications` | Current account only |
| POST | `/api/demo/notifications/{id}/read` | Notification owner only |

Medicine payload fields: `name`, `form`, `category`, `quantity`, `unit`, `expiry`, `batch`, `photoId`, `photoConfirmed`, `sealed`, `storage`, `declared`. The actual UI is the reference caller; never send real prescriptions, addresses or patient data. Photo assistance reports `ocrPerformed:false`; manual confirmation is required.

## State transitions and constraints

Submitted (`LISTED` internally) → admin `APPROVED` or `REJECTED`. NGO request `PENDING` → donor `APPROVED` or `DECLINED`. Acceptance locks the medicine and reserves only available quantity. A scheduled accepted request can become `COLLECTED` only with the correct protected code. Received is the same simulated completion, not a second physical-delivery claim. Code redisplay is donor-only; storage uses BCrypt plus authenticated encryption, never plaintext.

## Errors and abuse protection

Safe JSON errors use 400 for invalid/state/code input, 401 for unauthenticated/denied identity or invitation, 403 for role/verification/ownership, 404 for disabled/missing workflow, 409 for conflicts and 503 for unavailable/expired review. Login attempts receive a stricter per-peer limit; 429 includes `Retry-After: 60`. The frontend displays safe messages. Startup configuration failures never echo connection strings/secrets.

No test in the verification suite calls a production authentication endpoint. Hosted TLS/CA and full online browser behavior remain pending deployment approval.
