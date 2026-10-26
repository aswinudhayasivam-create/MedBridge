# Viva walkthrough: a real local database demonstration

All accounts, labels, locations and collections are fictional. No medical/regulatory clearance or real redistribution is represented.

1. Explain the boundary: React dev server on 127.0.0.1:5173 → Java 17 Spring Boot on 127.0.0.1:8080 → isolated MySQL on 127.0.0.1:13306. Public registration remains false. Production build guards remove the demo entry and public demo credentials.
2. Select Donor on Login; show that autofill is not automatic sign-in. Click Login as Meera. Dashboard counts come from MySQL rather than the PDF illustrations.
3. Start New donation. Enter a synthetic name, form, category, quantity/unit, batch and expiry at least three months away. Demonstrate the native picker or manual date fallback.
4. Download and upload the synthetic label. Explain the image validation and the assisted in-browser OCR suggestion. The donor confirms the name/batch/expiry explicitly; nothing is auto-accepted.
5. Confirm sealed condition, fictional storage and the synthetic-only declaration. Review and submit. Show the persisted Submitted for review timeline; it is not yet available to NGOs.
6. Sign out and log in as Admin. Open Medicine review, inspect the label/declarations, enter a fictional review note and approve. Discuss server-side role checks and the audit event.
7. Open NGO applications. Explain GreenCross's fictional verification and HopeCare's pending status. A pending NGO can apply; only the administrator can approve/reject it.
8. Sign in as Divya / GreenCross. Browse approved, unexpired listings within the Haversine 10 km radius. Toggle Within 5 km, greater-than-six-month expiry and category filters. No exact coordinates or private addresses are exposed.
9. Request an available quantity with a note. The backend checks verification, distance, duplicate requests and quantity. Show the persisted request timeline.
10. Return to Meera. Inspect GreenCross's organization, demo registration reference, verification status, quantity and note. Accept with a note. Explain transactional locking/reservation: concurrent acceptances cannot reserve more than the listing quantity.
11. Schedule a simulated collection within seven days using fictional instructions. Reveal the donor-only four-digit code without putting it into logs, shared screenshots or documentation. Its BCrypt verifier and encrypted donor copy are stored rather than plaintext.
12. Return to Divya. Show that the NGO has an input, never the donor's code. A wrong code cannot complete the request; five wrong attempts lock it and expired codes fail. Donor rescheduling rotates the code. Enter the correct code to record a simulated collection/receipt.
13. Show donor and NGO dashboards, request timeline, notifications and admin audit records. All timestamps come from persisted events; the synthetic collection is not physical delivery. Collection/received timestamps refer to the same simulated completion.
14. Restart the backend and sign in again. Records, quantities, notifications and completion survive. Seed does not duplicate accounts or reset reviewed states. Browser refresh clears only the in-memory session.
15. Explain negative-case tests: pending NGO denied, expired/short shelf-life submission rejected, wrong owner forbidden, ADMIN self-registration unavailable, repeated collection rejected and concurrent acceptance limited.

## Useful viva answers

- Why not use Aiven for the demo? Isolation prevents public test credentials and fictional medical records from reaching production.
- Does the image prove medicine safety? No. It is a synthetic label with donor-confirmed details; assisted OCR only suggests text on the donor's device, and no medical evaluation is claimed.
- Is an NGO approval real? No. It changes fictional local demo permissions only.
- Can a frontend user forge their role? Tokens identify an account; the backend reloads role/verification from MySQL and checks ownership for each transition.
- Why encrypt as well as hash the code? BCrypt verifies attempts; authenticated encryption permits donor-only redisplay after sign-out/restart without plaintext database storage.
- What if two NGOs compete? The medicine row is locked and the reserved counter is updated conditionally inside the acceptance transaction.
- What is not production-ready? Public account launch, real medicine compliance/verification, production operational controls and session revocation remain separate work. This branch must not be deployed as an activated public service.


## Protected review evaluation

The same donor/NGO/admin steps above apply to the separately guarded review entry, using private review identities and an invitation key rather than the public local selector. In the local rehearsal use port 5174; after an approved deployment use only the privately supplied protected URL. Demonstrate that missing invitation, the original local identities, pending NGO actions and expired review access are rejected. Explain that the server deadline is midnight after October 26 in Asia/Kolkata and that a valid JWT cannot extend it. Never reveal the invitation, passwords, live handoff code or database configuration in a recording. State explicitly whether the demonstration is a local rehearsal or the fully verified hosted environment; current hosted status is pending.
