# MedBridge Phase 2 — Authentication

## What is implemented
- POST /api/auth/register (DONOR or NGO only; NGO verification remains false)
- POST /api/auth/login
- GET /api/auth/me (Authorization: Bearer YOUR_TOKEN)
- GET /api/roles/donor, /api/roles/ngo, /api/roles/admin — access checks
- BCrypt (strength 12) password hashes, stateless signed JWT (1 hour by default)
- SQL-backed users and 1:1 NGO profiles. Public admin signup disabled.

## Cloud deployment (Railway)
1. Create Railway Spring Boot service from the GitHub repository, using root directory `MedBridge-Phase-1/medbridge/backend`.
2. Provision MySQL and configure the environment variables `DB_URL` (JDBC format), `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (random 32+ UTF-8 bytes), `FRONTEND_URL=https://med-bridge-ruddy.vercel.app`, `SQL_INIT_MODE=always` for initial schema initialization.
3. After successful first start, change `SQL_INIT_MODE=never` to stop SQL initialization on every boot. Do NOT load data.sql into production.
4. Expose port supplied by Railway via `PORT`. Configure public HTTPS domain in Railway.
5. This is Phase 2 backend only; the Vercel frontend is still the Phase 1 starter and does not call auth endpoints yet.

## Local run
Install Java 17, Maven, and MySQL 8. Create the medbridge database and database user. From backend directory set the environment variables in `.env.example` using your shell (Spring Boot does not read .env automatically), then run `mvn spring-boot:run`.

Register donor example:
```bash
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"name":"Demo Donor","email":"donor@example.com","password":"LongDemoPassword123!","role":"DONOR"}'
```

Register NGO example:
```bash
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"name":"NGO Contact","email":"ngo@example.com","password":"LongDemoPassword123!","role":"NGO","orgName":"Example Charity","licenseNo":"TEST-12345","serviceArea":"Coimbatore"}'
```

Login:
```bash
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"donor@example.com","password":"LongDemoPassword123!"}'
```

`GET /api/auth/me` requires `Authorization: Bearer <token>` header.

## Security notes
JWT_SECRET must remain private. No admin account is seeded in production. Public NGO registrations are unverified and cannot receive medicine until a future admin verification process. Application is not approved for live medicine handling; validate applicable regulations before production use. Password strength and token revocation policies should be expanded before processing real user data.
