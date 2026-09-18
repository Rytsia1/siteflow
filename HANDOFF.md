# SiteFlow Engineering Handoff Document (`HANDOFF.md`)

Welcome to the **SiteFlow** codebase. This handoff document provides a comprehensive technical overview of the system architecture, security implementation, data pipeline, reliability and observability features, disaster recovery runbooks, privacy and data governance, and guidelines for maintaining and extending the platform.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Tech Stack & System Requirements](#2-tech-stack--system-requirements)
3. [Repository Structure](#3-repository-structure)
4. [Authentication & Security Architecture](#4-authentication--security-architecture)
5. [Reliability & Observability](#5-reliability--observability)
6. [Data Integrity & Concurrency Control](#6-data-integrity--concurrency-control)
7. [Database Schema & Migrations](#7-database-schema--migrations)
8. [Performance & Query Optimizations](#8-performance--query-optimizations)
9. [Disaster Recovery & Backup/Restore Runbook](#9-disaster-recovery--backuprestore-runbook)
10. [Local Development & Deployment Guide](#10-local-development--deployment-guide)
11. [Testing & Verification Suite](#11-testing--verification-suite)
12. [Privacy & Data Governance](#12-privacy--data-governance)
13. [Operational Gotchas & Future Roadmap](#13-operational-gotchas--future-roadmap)

---

## 1. Executive Summary

**SiteFlow** is a full-stack material and tool logistics management platform designed for construction projects. It tracks physical tools, consumables, inventory locations, borrowing lifecycles, procurement pipelines, and warehouse transactions.

The system replaces manual paper logbooks and error-prone spreadsheets with:
- **Stateless JWT Authentication & Granular RBAC** with resource-level ownership guards.
- **Transaction-safe stock allocation** preventing negative quantities and race conditions.
- **Centralized error handling & distributed correlation ID tracing (`X-Request-ID`)**.
- **In-memory token bucket rate limiting** defending against brute-force and endpoint abuse.
- **Privacy by Design & Data Governance** with account deactivation, data anonymization, zero cookie tracking, and IDOR protection.
- **Repeatable disaster recovery scripts** with automated schema and data integrity verification.
- **100% test-backed reliability** (309 backend tests, 14 frontend tests).

---

## 2. Tech Stack & System Requirements

### Backend
- **Runtime**: Java 21 / 25+ (Target Release 25)
- **Framework**: Spring Boot 3.5.0
  - `spring-boot-starter-web` (REST controllers)
  - `spring-boot-starter-security` (Spring Security 6 with stateless filter chain)
  - `spring-boot-starter-validation` (Jakarta Bean Validation)
  - `spring-boot-starter-actuator` (Production health & observability probes)
- **Persistence**: MyBatis 3.0.4 + MySQL Connector/J 8.0
- **Connection Pool**: HikariCP (Max pool size: 20, connection timeout: 20s)
- **Database Migrations**: Flyway Core (11 versioned migrations, V1 through V11)
- **Security & Tokens**: `jjwt-api` / `jjwt-impl` / `jjwt-jackson` (0.12.6, HMAC-SHA256)
- **Build Tool**: Apache Maven 3.9+

### Frontend
- **Framework**: Vue 3 (Composition API, `<script setup>`)
- **Router**: Vue Router 4 (HTML5 history mode with navigation auth guards, public `/privacy` and `/terms` routes)
- **HTTP Client**: Axios with global request/response interceptors (Bearer token injection, 15s timeout, standard error envelope parsing, and `X-Request-ID` handling)
- **Build Tool**: Vite 6.4.3
- **CSS**: Scoped Vanilla CSS & modern CSS design tokens

### Database & Infrastructure
- **Database**: MySQL 8.0+ (utf8mb4 charset, utf8mb4_unicode_ci collation)
- **CLI Utilities**: `mysql`, `mysqldump` (in system PATH)
- **Scripting**: PowerShell 7+ / Windows PowerShell 5.1 & POSIX Bash

---

## 3. Repository Structure

```text
siteflow/
├── .github/                     # GitHub Actions CI workflows
├── backups/                     # Default storage for mysqldump exports (gitignored)
├── frontend/                    # Vue 3 Single Page Application (SPA)
│   ├── src/
│   │   ├── api/                 # Axios client, interceptors, and API abstractions
│   │   ├── components/          # Reusable UI components
│   │   ├── router/              # Route definitions and navigation guards
│   │   ├── views/               # Page views (Dashboard, Borrow, Procurement, Privacy, Terms, etc.)
│   │   └── auth.js              # Reactive auth state & token storage (sessionStorage)
│   ├── package.json
│   └── vite.config.js
├── pom.xml                      # Maven root build configuration
├── scripts/                     # Repeatable ops & disaster recovery scripts
│   ├── backup.ps1               # PowerShell backup script
│   ├── backup.sh                # Bash backup script
│   ├── restore.ps1              # PowerShell restore script (with isolated test db support)
│   └── restore.sh               # Bash restore script
├── src/
│   ├── main/
│   │   ├── java/com/siteflow/
│   │   │   ├── config/          # Cache, Async, WebMvc configurations
│   │   │   ├── controller/      # REST API controllers (including UserController)
│   │   │   ├── domain/          # Entities, Enums, and View DTOs
│   │   │   ├── mapper/          # MyBatis mapper interfaces & SQL annotations
│   │   │   ├── security/        # JWT filter, Correlation filter, Rate limiter, RBAC
│   │   │   ├── service/         # Transactional domain logic, Idempotency, UserService
│   │   │   └── web/             # Centralized exception handler & error response DTOs
│   │   └── resources/
│   │       ├── application.yml  # Application properties (ports, datasource, actuator, logging)
│   │       └── db/migration/    # Flyway SQL migrations (V1__init.sql ... V11__user_lifecycle_and_governance.sql)
│   └── test/                    # 309 JUnit 5 test cases across unit, integration, and load
│       └── java/com/siteflow/
│           ├── controller/      # Controller layer mock tests
│           ├── reliability/     # Actuator, Concurrency, Load benchmarks, Backup/Restore
│           ├── security/        # JWT, RBAC, Rate Limiting, IDOR, DataPrivacyAndGovernanceTest
│           └── service/         # Idempotency, stock allocation, and race conditions
├── HANDOFF.md                   # This document
├── PRIVACY.md                   # Complete Privacy Policy & Data Governance Documentation
└── TERMS.md                     # Terms of Service & Acceptable Use Documentation
```

---

## 4. Authentication & Security Architecture

### A. Stateless JWT Authentication
1. **Login Flow (`POST /api/auth/login`)**:
   - Accepts username and password.
   - Verified via BCrypt password hashing against the `users` table.
   - Deactivated accounts are rejected with HTTP 401 Unauthorized (`User account is deactivated.`).
   - Generates an HMAC-SHA256 signed JWT with claims: `sub` (username), `userId`, and `role`.
   - Secret key is loaded dynamically from `${JWT_SECRET}` (minimum 256 bits; fallback only in test profile).
2. **Filter Chain Execution Order**:
   ```text
   HTTP Request
        ↓
   [CorrelationIdFilter]                   -> Generates/validates X-Request-ID, pushes to MDC
        ↓
   [JwtAuthenticationFilter]               -> Parses 'Authorization: Bearer <token>', builds SecurityContext
        ↓
   [RateLimitingFilter]                    -> Enforces in-memory token bucket limits per IP/client
        ↓
   [UsernamePasswordAuthenticationFilter]
        ↓
   Controller / Business Logic
   ```
3. **Token Expiration**: Default 1 hour (`3600000 ms`), configurable via `${JWT_EXPIRATION_MS}`.

### B. Role-Based Access Control (RBAC)
Four distinct roles are supported:
- **`ADMIN`**: Full platform authority (Approvals, Procurement PO generation, Stock adjustments, Analytics, User lifecycle).
- **`PROCUREMENT`**: Dedicated purchasing and Material Request review authority.
- **`WAREHOUSE_STAFF`**: Inventory management, check-in, check-out, and stock viewing.
- **`FIELD_STAFF`**: Worker profile; submit borrow requests, submit material requests, view personal history.

All controller endpoints enforce declarative security using `@PreAuthorize`.

### C. Resource-Level Authorization & Ownership (IDOR Defense)
- Workers (`FIELD_STAFF`) are strictly constrained to their own records:
  - `GET /api/borrow-requests/my` returns only requests authored by the authenticated `userId`.
  - `GET /api/borrow-requests/{id}` enforces ownership checks in `BorrowService`; accessing another user's request returns `403 Forbidden`.
  - `GET /api/procurement/material-requests/my` filters strictly by `requested_by = principal.getUserId()`.
  - `GET /api/procurement/material-requests/{id}` enforces ownership in `ProcurementService`.
  - Cancellation endpoints verify that `principal.getUserId() == resource.getOwnerId()` unless the user holds the `ADMIN` role.

### D. Server-Side Rate Limiting
- Configured in `application.yml` via [`RateLimitProperties`](src/main/java/com/siteflow/security/ratelimit/RateLimitProperties.java):
  - **Login Limit**: 5 requests / 60-second sliding window per client IP.
  - **Sensitive Endpoints**: 30 requests / 60 seconds.
  - **General API Limit**: 100 requests / 60 seconds.
- Returns HTTP `429 Too Many Requests` with `Retry-After: <seconds>` when quotas are breached.
- Reverse proxy header spoofing protection: `trust-proxy: false` by default, ignoring forged `X-Forwarded-For` headers unless explicitly enabled.

---

## 5. Reliability & Observability

### A. Application Health & Uptime Monitoring
- **Endpoint**: `GET /actuator/health`
- **Access**: Publicly accessible for uptime monitors and Docker health checks.
- **Safety**: Configured with `show-details: never` and `show-components: never`.

### B. Distributed Request Tracing (`X-Request-ID`)
- Every request entering SiteFlow is assigned a unique correlation ID:
  - Pushed to SLF4J `MDC` (`requestId`).
  - Always echoed back in HTTP response header `X-Request-ID`.

### C. Centralized Exception Handling & Safe Responses
- Handled by [`GlobalExceptionHandler`](src/main/java/com/siteflow/web/GlobalExceptionHandler.java).
- Stack traces, SQL exceptions, database usernames, and passwords are **never** returned in client error responses.

---

## 6. Data Integrity & Concurrency Control

### A. Preventing Negative Inventory & Race Conditions
- Stock updates employ **pessimistic row locking** via `ItemStockMapper.findByItemIdAndLocationIdForUpdate()` (`SELECT ... FOR UPDATE`).
- Final stock mutation is guarded by an atomic compare-and-swap SQL statement:
  ```sql
  UPDATE item_stocks 
  SET current_qty = current_qty + #{qtyDelta} 
  WHERE id = #{id} AND current_qty + #{qtyDelta} >= 0;
  ```

### B. Idempotency & Duplicate Submission Protection
- High-value mutation endpoints accept an optional `Idempotency-Key` header handled by `IdempotencyService` on table `idempotency_keys` with `REQUIRES_NEW` transactions.

---

## 7. Database Schema & Migrations

All schema changes are versioned using Flyway in `src/main/resources/db/migration`:

| Migration | Description | Key Tables / Objects |
|---|---|---|
| `V1__init_schema.sql` | Baseline MVP schema | `roles`, `users`, `locations`, `items`, `item_stocks`, `borrow_requests`, `borrow_items`, `transaction_logs` |
| `V2__seed_reference_data.sql` | Core reference data | Default roles (`ADMIN`, `WAREHOUSE_STAFF`, `FIELD_STAFF`) and warehouse locations |
| `V3__seed_users.sql` | Initial user accounts | Seeded admin, warehouse staff, and field staff accounts with hashed passwords |
| `V4__seed_sample_data.sql` | Initial demo inventory | Seeded tools (`TOOL`), consumables (`CONSUMABLE`), and lifting gear |
| `V5__v2_management_schema.sql` | Advanced management | `material_requests`, `material_request_items`, `purchase_orders` |
| `V6__improve_schema_integrity_and_indexes.sql` | Foreign key constraints | Cascade and restrict rules on relational joins |
| `V7__material_request_approval_audit.sql` | Approval audit fields | Adds `approved_by`, `approval_note`, and timestamps to procurement requests |
| `V8__item_instance_borrow_tracking.sql` | Individual tool tracking | `item_instances` with QR codes, serial numbers, and condition states |
| `V9__duplicate_prevention_and_integrity.sql` | Idempotency engine | `idempotency_keys` table with expiration timestamps |
| `V10__performance_optimization_indexes.sql` | Composite query indexes | Composite indexes on `borrow_requests`, `material_requests`, and `transaction_logs` |
| `V11__user_lifecycle_and_governance.sql` | Privacy & lifecycle governance | Adds `is_active`, `deactivated_at`, and `idx_users_is_active` to `users` |

---

## 8. Performance & Query Optimizations

- **Composite Database Indexes (V10)**: Query optimization on `borrow_requests`, `material_requests`, `transaction_logs`.
- **Elimination of N+1 Queries**: Batch fetching in `AnalyticsService.generateReorderRecommendations()`.
- **Database-Level Pagination & Injection Defense**: Standardized `LIMIT/OFFSET` with column whitelisting.
- **Reference Data Caching**: In-memory cache for static locations.

---

## 9. Disaster Recovery & Backup/Restore Runbook

- Logical database backups via `mysqldump` in `scripts/backup.ps1` and `scripts/backup.sh`.
- Isolated test restore in `scripts/restore.ps1` and `scripts/restore.sh`.
- Validated via automated test `DatabaseBackupRestoreTest.java` asserting 11 Flyway migrations and referential integrity.

---

## 10. Local Development & Deployment Guide

### A. Environment Configuration
Copy `.env.example` to `.env` or set environment variables:
- `SERVER_PORT`: `8080`
- `DB_HOST`: `localhost`, `DB_PORT`: `3306`, `DB_NAME`: `siteflow`
- `JWT_SECRET`: 256-bit secret key
- `JWT_EXPIRATION_MS`: `3600000` (1 hour)

### B. Starting Backend & Frontend
```bash
# Backend
mvn spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

---

## 11. Testing & Verification Suite

- **Backend**: **309 automated tests** covering unit logic, integration endpoints, security policies, IDOR protection, data governance, concurrency, and disaster recovery:
  ```bash
  mvn test
  ```
- **Frontend**: **14 automated tests** covering HTTP client error interception, idempotency guards, and token injection:
  ```bash
  cd frontend
  npm test
  npm run build
  ```

---

## 12. Privacy & Data Governance

### A. Data Minimization
- Only operational business data is stored: `username`, `full_name`, `job_position`, role, borrow requests, material requests, and audit logs.
- Zero collection of emails, phone numbers, home addresses, government IDs, coordinates, or biometrics.

### B. Sensitive Data Protection
- Zero exposure of `password_hash`, passwords, database credentials, or JWT secrets across all API responses.
- IDOR defense on all personal request endpoints.
- Error responses masked to prevent stack trace and SQL query leakage.

### C. Cookie & Storage Architecture
- **Cookies**: 0 cookies used.
- **LocalStorage**: None.
- **SessionStorage**: `siteflow.auth` stores `{ username, token, role }` solely for the active tab session; purged on logout.
- **Third-Party Trackers**: Zero tracking, analytics, or external telemetry libraries. Locally bundled UI assets.

### D. Account Deactivation & Data Anonymization
- To prevent breaking `ON DELETE RESTRICT` constraints on historical tool borrowing and audit ledgers, accounts are **deactivated and anonymized** rather than destroyed:
  - `POST /api/users/me/deactivate`: User-initiated with explicit confirmation.
  - `POST /api/users/{id}/deactivate`: Admin-initiated during staff offboarding.
  - Account marked `is_active = FALSE`.
  - `full_name` anonymized (`Anonymized User #[id]`), `job_position` cleared.
  - `password_hash` scrambled (`DEACTIVATED_[UUID]`), permanently blocking login attempts (`401 Unauthorized`).
  - Historical business records and transaction logs remain consistent.

### E. Legal & Policy Documentation
- Frontend routes: `/privacy` (Privacy Policy) and `/terms` (Terms of Service).
- Documentation files: [`PRIVACY.md`](PRIVACY.md) and [`TERMS.md`](TERMS.md).
- Clear placeholders for organizational confirmation (`[Operating Organization]`, `[Data Governance Contact]`, etc.).

---

## 13. Operational Gotchas & Future Roadmap

### Operational Gotchas
1. **Hikari Connection Deadlocks with `REQUIRES_NEW`**: Maintain `spring.datasource.hikari.maximum-pool-size` at $\ge 20$.
2. **Rate Limiting in Tests**: Use `RateLimiterService.resetAll()` in test fixtures if testing rapid authentication.
3. **Sole Admin Protection**: `UserService` prevents deactivating the sole remaining active `ADMIN` to avoid platform lockout.

---
*Document maintained by the SiteFlow Engineering Team.*
