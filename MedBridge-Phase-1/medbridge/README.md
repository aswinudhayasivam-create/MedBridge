# MedBridge application source

This directory contains the completed React/Vite frontend and Java 17/Spring Boot backend. It is no longer a Phase 1 starter.

- [Main project README](../../README.md): features, screenshots, fictional local logins and status.
- [Local setup](../../DEMO-SETUP.md) and [viva walkthrough](../../DEMO-WALKTHROUGH.md).
- [Architecture](../../SYSTEM-ARCHITECTURE.md), [API](../../API-DOCUMENTATION.md), [deployment preparation](../../DEPLOYMENT-GUIDE.md).
- [Security boundaries](../../SECURITY-AND-DEMO-LIMITATIONS.md) and [actual verification results](../../PROJECT-STATUS.md).

`frontend/` holds the existing responsive donor/NGO/admin components. `backend/` holds authentication, shared transactional workflows, local isolation and separately guarded review mode. MySQL stores actual records and synthetic label images.

Public registration remains disabled. Do not run legacy `data.sql`, restore production data, use old Phase 2 deployment instructions, or treat historical phase-specific documents as the current deployment procedure. Hosted review is pending explicit infrastructure and exposure approval.
