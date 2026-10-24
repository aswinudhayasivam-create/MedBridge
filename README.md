# MedBridge — Smart Medicine Redistribution Network

MedBridge is a **donation-only** sample application connecting donors with verified NGO/clinic receivers. **No selling, resale, checkout, prices or payments.** The later informational estimated donation value is not a transaction price.

## Project status

**Phase 1 complete:** Spring Boot and React/Vite starters, MySQL schema, idempotent demo seed data, setup and health endpoint. No production workflow or authentication is implemented yet. Phase 2 adds role models and JWT authentication; Phases 3–8 implement the remaining features.

## Prerequisites

- JDK 17 (`java -version`)
- Maven 3.9+ (`mvn -version`)
- MySQL 8.0+ running locally
- Node.js 20+ and npm (`node -v`, `npm -v`)
- Tesseract language pack will be required starting Phase 3

## 1. Create the database and restricted local user

In the MySQL CLI as an administrator:

```sql
CREATE DATABASE IF NOT EXISTS medbridge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'medbridge'@'localhost' IDENTIFIED BY 'change_me';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON medbridge.* TO 'medbridge'@'localhost';
```

For local demo simplicity, the application runs `schema.sql` and `data.sql` automatically. Both use `IF NOT EXISTS` / `INSERT IGNORE`, so restarting will not duplicate sample rows. Existing modified rows are left untouched. **Do not use automatic seeding in production.** Replace this mechanism with database migrations in a later phase.

## 2. Run the backend

From `backend/`, create environment variables in your shell (Spring Boot does not read `.env` automatically):

```bash
# Linux/macOS
export DB_URL='jdbc:mysql://localhost:3306/medbridge?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='medbridge'
export DB_PASSWORD='change_me'
mvn spring-boot:run
```

Windows PowerShell equivalent:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/medbridge?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USERNAME='medbridge'
$env:DB_PASSWORD='change_me'
mvn spring-boot:run
```

Health check: visit <http://localhost:8080/api/health> or run:

```bash
curl http://localhost:8080/api/health
```

Expected response: `{"application":"MedBridge","phase":"1","status":"UP"}` (JSON key order is not guaranteed). All other API paths are intentionally denied until authentication is built.

## 3. Run the frontend

In a **second terminal**, from `frontend/`:

```bash
npm install
npm run dev
```

Visit <http://localhost:5173>. The Phase 1 starter screen renders. The Vite development proxy forwards `/api/*` to `http://localhost:8080`.

## 4. Inspect seeded data

Run in MySQL:

```sql
USE medbridge;
SELECT role, COUNT(*) AS total FROM users GROUP BY role;
SELECT status, COUNT(*) AS total FROM medicines GROUP BY status;
SELECT u.email, p.org_name, u.verified FROM users u JOIN ngo_profiles p ON p.ngo_id = u.user_id;
```

Demo fixtures: **1 admin, 3 donors, 4 fictional NGOs, 10 medicines, 2 requests, 3 notifications**. Three fictional NGO accounts have `verified=1`; the fourth is unverified for verification-flow testing. Coordinates are illustrative around Coimbatore and do **not** correspond to verified actual medical providers.

All demo users: password `MedBridgeDemo123!` stored as BCrypt hash. Example: `admin@medbridge.test` (login is not available until Phase 2). The `.test` addresses will not deliver email. Never reuse the demo password or load the seed fixtures into production.

## Database diagram

```text
users (user_id PK) ─── 1:1 ─── ngo_profiles (ngo_id PK/FK)
       │
       ├── 1:N ─── medicines (donor_id FK)
       │                │
       │                └── 1:N ─── requests (medicine_id FK)
       │                                │
       ├────────────────────────────────┘ receiver_id FK
       │
       └── 1:N ─── notifications (user_id FK)
```

## Roles and planned workflow

- **DONOR:** list medicines, confirm OCR fields, approve/decline NGO requests, donation history.
- **NGO:** browse eligible nearby listings, request medicine, confirm collection.
- **ADMIN:** verify NGOs, review flags, moderate users and view aggregate impact statistics.

Medicine statuses: `LISTED → FLAGGED → APPROVED/REJECTED`, or `LISTED → APPROVED → MATCHED → REQUESTED → COMPLETED`. Declining a request returns the medicine to `MATCHED` if still eligible. Request rows have their own `PENDING`, `APPROVED`, `DECLINED`, `COLLECTED`, `CANCELLED` statuses. Status transitions must be implemented transactionally in Phase 4.

## Important safety constraints

This is an educational prototype, **not authorization to redistribute drugs**. Laws vary by jurisdiction. In India and elsewhere, medicine collection/distribution may require licensed partners, permitted supply chains, correct storage, tamper-evident packaging, batch traceability, recall checks, and a pharmacist's review. The intended safety filter is only an initial screening step, **not a medical or legal clearance**. Controlled medicines and items barred by applicable rules must never become transferable by admin override. Near-expiry medicines must be reviewed and also satisfy any minimum remaining shelf life at handover. Implement all compliance controls before any real deployment. OCR is assistive, and manual confirmation plus provenance checks are required. Do not disclose donor home coordinates to other users.

## Phase map

1. **Foundation:** this phase.
2. JWT authentication, roles, User and NGO entities.
3. Medicine upload, Tess4J OCR, safety checks, estimated value reference.
4. Distance matching, transactional requests, email/in-app notifications, admin API.
5. JUnit 5 and Mockito tests.
6. Shared React UI library, public pages and authentication UX.
7. Donor, NGO and admin dashboards connected to API.
8. Loading, accessibility, animations and deployment guidance.

## File layout

```text
medbridge/
├── backend/
│   ├── pom.xml
│   ├── .env.example
│   └── src/
│       ├── main/
│       │   ├── java/com/medbridge/
│       │   │   ├── MedBridgeApplication.java
│       │   │   ├── config/StartupSecurityConfig.java
│       │   │   ├── controller/HealthController.java
│       │   │   └── {model,dto,repository,service,security,exception}/ (Phase 2+)
│       │   └── resources/{application.properties,schema.sql,data.sql}
│       └── test/java/com/medbridge/ (Phase 5)
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── postcss.config.js
│   ├── tailwind.config.js
│   ├── index.html
│   ├── .env.example
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       ├── index.css
│       └── {components,pages,context,hooks,services,assets}/ (Phase 6+)
├── .gitignore
└── README.md
```
