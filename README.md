# VAPT Management

A modular monolith for managing vulnerability assessment reports, CMDB datasets, enrichment, finding reconciliation, remediation workflows, and reporting.

## Technology

- Backend: Java 21, Spring Boot, Spring Security, JPA, Flyway, and H2 for local development.
- Frontend: React, TypeScript, and Vite.

## Local development

1. Set `VAPT_ADMIN_INITIAL_PASSWORD` in your shell before the first backend startup. The application does not contain a default administrator password.
2. From `backend`, run `./mvnw spring-boot:run` (PowerShell: `./mvnw.cmd spring-boot:run`).
3. From `frontend`, run `npm install` and then `npm run dev`.
4. Open `http://localhost:3000`.

The Vite development server proxies API calls to the backend at `http://localhost:8080`. Local H2 data and uploaded files are stored under `data/` and are intentionally excluded from source control.

## Verification

- Backend tests: `./mvnw test` from `backend`.
- Frontend production build: `npm run build` from `frontend`.

See [ARCHITECTURE.md](ARCHITECTURE.md), [PRD.md](PRD.md), [IMPLEMENTATION.md](IMPLEMENTATION.md), and [HANDOFF.md](HANDOFF.md) for design, requirements, implementation status, and manual UAT notes.

## Sensitive data

Do not commit report uploads, CMDB source files, local databases, logs, credentials, or customer data. These are excluded by `.gitignore`.
