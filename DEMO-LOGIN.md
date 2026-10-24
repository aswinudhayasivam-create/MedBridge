# Intentionally public fictional demo logins

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


## Separate protected online review preparation

The four identities above remain local-only under `DemoIdentityPolicy`, even in review mode. Separate review accounts use the reserved domain `@review.medbridge.invalid`, private randomly generated passwords and a private invitation gate. None of their working passwords or a hosted review URL is published. The local rehearsal generates only test fixtures in ignored `.review/local.env`; these are not deployed credentials. Review access expires server-side at midnight after October 26, 2026 Asia/Kolkata. See [deployment approvals](DEPLOYMENT-GUIDE.md) and [security boundaries](SECURITY-AND-DEMO-LIMITATIONS.md).
