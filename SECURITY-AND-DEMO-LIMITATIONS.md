# Security and demonstration limitations

## Environment separation

1. Public `@medbridge.test` credentials remain local-only under the complete unchanged isolation guard. No review profile, invitation header, flag, token or query parameter bypasses that rule.
2. Review identities end in `@review.medbridge.invalid`; passwords and invitation/signing keys are generated privately, never committed. These identities are rejected outside a ready review environment.
3. Review is disabled by default. It requires a dedicated approved database, exact profile and settings, a marker, restricted grants, private credentials, exact CORS origin and server deadline. Loopback-test transport cannot use a public bind or remote DB.
4. Normal production builds remain unable to submit authentication or workflows. An explicit review build is separate and still needs the backend invitation, login and all server checks. A Vercel preview alone cannot activate access.

## Review cutoff and account behavior

Access ends at **00:00 on October 27, 2026 Asia/Kolkata** (`2026-10-26T18:30:00Z`). Earlier expiration is permitted; later expiration is rejected. Startup and every API request fail closed at/after cutoff; a previously issued JWT cannot continue. The deadline does not automatically delete data or power down cloud resources. An approved operator should revoke invitation credentials and suspend the review service afterward.

Review seeding runs only after database proof, uses BCrypt and preserves existing accounts/states. Changing environment passwords does not silently rotate existing database account hashes. For a planned rotation, obtain approval, stop access, update the dedicated review account hashes using an authorized administrator procedure and verify again. No automatic reset or remote self-registration exists.

## Protection implemented

- Spring Security's stateless JWT filter reloads identity/roles from MySQL; pending NGOs do not receive verified-NGO authority.
- Ownership and current verification are checked again in the shared service.
- Transactional locking and reserved quantities prevent double allocation.
- Synthetic image type/dimension/size checks, metadata-stripping re-encoding and explicit confirmation.
- Four-digit code expiry, five-attempt lockout, BCrypt verifier and AES-GCM donor-only redisplay.
- Invitation-key comparison uses constant-time byte comparison; browser invitation and JWT remain memory-only and clear on logout/refresh.
- Review rate limits are bounded in memory and use socket peers rather than spoofable forwarding headers. Login limit 10/minute; API limit 300/minute; <=2048 peer buckets. Counter state resets on process restart and can be shared behind a proxy.
- Hosted MySQL requires `sslMode=VERIFY_IDENTITY`; missing/untrusted/mismatched CA must fail, never be worked around by disabling verification.
- Runtime review credentials have only SELECT/INSERT/UPDATE/DELETE on the fixed review database, not DDL/global privileges.

## Known limitations

This is a fictional college evaluation, not a medically validated service. OCR is not performed. Screening is not proof of medicine quality, legality, storage integrity or recipient eligibility. No real medicine, patient information, donor address, payment or physical transfer is allowed.

Received equals simulated collection. Confirmation may occur before the illustrative appointment time. Terminal re-requesting by the same NGO is blocked by the original unique constraint. Abandoned synthetic uploads and fictional test notifications/audit history can remain until an approved dedicated-data reset. Design follows the supplied PDFs closely, with explicit safety text and system-font fallbacks; it is not a pixel-perfect reproduction.

Free hosting can sleep, exhaust quotas, restart processes or become unavailable; no availability SLA is promised. Application-level invitation keys supplement rather than replace Vercel deployment protection. Distributed brute-force prevention, per-user throttling, session revocation, comprehensive monitoring and assistive-technology audits remain future work. Operators must avoid exposing credential-bearing logs/traces.

## Requirements before real users

Separate legal/medical/regulatory review, formal NGO/clinic verification, consent/privacy/data-retention controls, threat assessment, audited migrations and backups, revoke/refresh session design, tested operational monitoring and incident response, accessible user testing and an independently approved production release. Do not extend the short review window or activate public registration as a shortcut.

The old Phase 2 Render backend and existing protected service have not been updated by this local preparation. Do not infer that either has the new policy or is healthy. Never reconnect the old backend to a restored production database.
