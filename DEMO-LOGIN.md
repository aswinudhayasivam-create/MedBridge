# Intentionally public fictional demo logins

> Hosted review update (27 October 2026): email/password-only login is deployed. The shared invitation-key requirement is removed. JWT, roles, ownership, isolated database/TLS, disabled registration, rate limits and the end-of-22-November-2026 Asia/Kolkata cutoff remain. Public local demo passwords still do not work online. Only newly rotated, disposable public demo passwords below are intentionally published.

These credentials are PUBLIC, DEMO-ONLY test fixtures, not secrets. They work only in the explicitly enabled isolated local development database. Never reuse them, seed them remotely, put them in hosted environment settings or CI secrets, or enable deployed authentication with them.

| Selector | Name / organization | Email | Public demo password |
| --- | --- | --- | --- |
| Donor | Meera K | meera@medbridge.test | DemoDonor2026! |
| Verified NGO | Divya Rao / GreenCross Foundation | divya@medbridge.test | DemoNgo2026! |
| Pending NGO | HopeCare Foundation | pending@medbridge.test | DemoPending2026! |
| Admin | MedBridge Review Team | admin@medbridge.test | DemoAdmin2026! |

Start using `DEMO-SETUP.md`, open `http://127.0.0.1:5173/login`, choose a selector and explicitly click Login. Selection autofills but does not authenticate. Login uses the real Spring Boot API and BCrypt hashes stored in local MySQL. There is no public ADMIN registration.

`APP_DEMO_WORKFLOWS` defaults false. The provisioning runner first validates the unchanged exact local-auth profile, HTTP loopback binding and `127.0.0.1:13306/medbridge_phase4_dev` database configuration. Existing demo accounts are preserved rather than reset on restart. Passwords are hashed using BCrypt; no plaintext password is stored in MySQL.

The demo selector module is imported only by the development-only entry. Production Vite builds must contain neither these credentials nor an enabled demo entry, even with accidentally true environment flags. Never deploy this branch without a separately approved review and push. There is no date-triggered activation after October 26.

All verification decisions are fictional. No medicine, prescription, medical information, real address or production account belongs in this demonstration.

## Hosted fictional demo logins - online only

[Open the hosted demo](https://medbridge-review-20261026-git-fe-dcd96f-aswinudhayasivam-create.vercel.app/)

These are intentionally public, disposable credentials for synthetic records only. Anyone can sign in, including as administrator and change fictional records. Never enter real medicine, patient or address information. No invitation key is needed. The previous private role passwords no longer work. Registration stays disabled. Access ends at the end of 22 November 2026 (Asia/Kolkata). Free Render hosting can need about a minute to wake up.

| Demo role | Online email | Public demo-only password |
|---|---|---|
| Donor | `donor@review.medbridge.invalid` | `Demo-Donor-C599xKcifZvE58lDbe4G!` |
| Verified NGO | `ngo@review.medbridge.invalid` | `Demo-Ngo-HTfbLrf5x8bn5iDPnfef!` |
| Pending NGO | `pending@review.medbridge.invalid` | `Demo-Pending-_Q8cSbJzugmzbbMlqPG-!` |
| Administrator | `admin@review.medbridge.invalid` | `Demo-Admin-NsMb4uZCxhZlLAdUwZPu!` |

Pending NGO can log in but cannot browse/request medicines until verified. Do not reuse these public passwords on any real account. JWT, role/ownership checks, rate limits, isolated database/TLS and server cutoff remain enforced.


## Separate protected online review preparation

The four identities above remain local-only under `DemoIdentityPolicy`, even in review mode. Separate review accounts use the reserved domain `@review.medbridge.invalid`, private passwords and email/password-only login (no invitation key). None of their working passwords or a hosted review URL is published. The local rehearsal generates only test fixtures in ignored `.review/local.env`; these are not deployed credentials. Review access expires server-side at midnight after November 22, 2026 Asia/Kolkata. See [deployment approvals](DEPLOYMENT-GUIDE.md) and [security boundaries](SECURITY-AND-DEMO-LIMITATIONS.md).
