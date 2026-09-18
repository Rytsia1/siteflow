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
13. [Accessibility & UX Compliance (WCAG 2.1 AA)](#13-accessibility--ux-compliance-wcag-21-aa)
14. [Software Supply Chain, Dependency & Asset Compliance](#14-software-supply-chain-dependency--asset-compliance)
15. [Audit Trail, Accountability & Security Logging](#15-audit-trail-accountability--security-logging)
16. [Operational Gotchas & Future Roadmap](#16-operational-gotchas--future-roadmap)

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
- **100% test-backed reliability** (322 backend tests, 32 frontend tests).

---

## 2. Tech Stack & System Requirements

### Backend
- **Runtime**: Java 21 / 25+ (Target Release 25)
- **Framework**: Spring Boot 3.5.16
  - `spring-boot-starter-web` (REST controllers)
  - `spring-boot-starter-security` (Spring Security 6 with stateless filter chain)
  - `spring-boot-starter-validation` (Jakarta Bean Validation)
  - `spring-boot-starter-actuator` (Production health & observability probes)
- **Persistence**: MyBatis 3.0.5 + MySQL Connector/J 9.7.0
- **Connection Pool**: HikariCP (Max pool size: 20, connection timeout: 20s)
- **Database Migrations**: Flyway Core (15 versioned migrations, V1 through V15)
- **Security & Tokens**: `jjwt-api` / `jjwt-impl` / `jjwt-jackson` (0.12.7, HMAC-SHA256)
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
| `V3__seed_users.sql` | Legacy seed deprecation | Deprecated from production migration chain; demo fixtures isolated to dev profile `db/dev-seed/` |
| `V4__seed_sample_data.sql` | Initial demo inventory | Seeded tools (`TOOL`), consumables (`CONSUMABLE`), and lifting gear |
| `V5__v2_management_schema.sql` | Advanced management | `material_requests`, `material_request_items`, `purchase_orders` |
| `V6__improve_schema_integrity_and_indexes.sql` | Foreign key constraints | Cascade and restrict rules on relational joins |
| `V7__material_request_approval_audit.sql` | Approval audit fields | Adds `approved_by`, `approval_note`, and timestamps to procurement requests |
| `V8__item_instance_borrow_tracking.sql` | Individual tool tracking | `item_instances` with QR codes, serial numbers, and condition states |
| `V9__duplicate_prevention_and_integrity.sql` | Idempotency engine | `idempotency_keys` table with expiration timestamps |
| `V10__performance_optimization_indexes.sql` | Composite query indexes | Composite indexes on `borrow_requests`, `material_requests`, and `transaction_logs` |
| `V11__user_lifecycle_and_governance.sql` | Privacy & lifecycle governance | Adds `is_active`, `deactivated_at`, and `idx_users_is_active` to `users` |
| `V12__audit_trail_and_accountability.sql` | Audit logging engine | Adds audit columns, decoupled foreign keys, and indexes on `transaction_logs` |
| `V13__disable_default_seed_credentials.sql` | Default credential neutralization | Deactivates legacy demo accounts and scrambles password hashes in existing DBs |

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
- **Frontend**: **28 automated tests** covering HTTP client error interception, idempotency guards, token injection, WCAG AA contrast verification, non-color statuses, notification routes, CSS focus rules, lockfile verification, and absence of external CDNs/secrets:
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
- **LocalStorage**: Limited exclusively to user interface theme preference (`theme: 'dark' | 'light'`).
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

## 13. Accessibility & UX Compliance (WCAG 2.1 AA)

### A. Semantic HTML Landmarks & Skip Links
- **Landmarks**: Layout strictly structured with `<header role="banner">`, `<nav aria-label="Main Navigation">`, `<main id="main-content" role="main" tabindex="-1">`, and `<footer role="contentinfo">`.
- **Skip Link**: `<a href="#main-content" class="skip-link">Skip to main content</a>` positioned as the first focusable element, bypassing navigation on `Tab`.
- **Heading Hierarchy**: Distinct `<h1>` per view, followed by semantic `<h2>` section headings and `<h3 class="stat-card-title">` labels.

### B. Visible Focus States & Keyboard Operability
- **Focus Rings**: Universal `:focus-visible` ring across all interactive controls (`outline: 2px solid var(--el-color-primary); outline-offset: 2px`).
- **Modal Dialogs**: All Element Plus dialogs configured with `aria-modal="true"`, accessible headings, and Escape key dismissal.
- **Data Tables**: Wide tables wrapped in `.accessible-table-container` with `tabindex="0"`, `role="region"`, and descriptive `aria-label` for smooth keyboard scrollability.

### C. Color Contrast & Multi-Modal Status Communication
- **Contrast Ratios**: Default muted grays (`#909399`) elevated to high-contrast tokens (`#595959` / `#4a5568` on light mode; `#a0a8b4` / `#dcdfe6` on dark mode), satisfying the WCAG AA $\ge 4.5:1$ threshold.
- **Non-Color Indicators**: Statuses never communicate state by color alone:
  - `Approved`: `✓ Approved` (Green)
  - `Rejected`: `✕ Rejected` (Red)
  - `Pending / Submitted`: `⏳ Pending Approval` (Orange)
  - `Borrowed`: `📦 Borrowed` (Blue)
  - `Low Stock`: `⚠️ X (Low Stock)` (Red) vs `✓ X` (Normal)
  - `Tool Condition`: `✓ Good Condition`, `⚠️ Needs Repair`, `✕ Broken`

### D. In-App Notification Center
- Integrated header popover with unread badge counter, keyboard focusability, and `aria-expanded` state.
- Notifications are actionable: clicking or activating an item navigates directly to the target operational route (`/borrow`, `/procurement`, `/assets`, `/inventory`) without bypassing backend authorization.

### E. Accessible Data Visualizations
- Canvas-rendered Chart.js line charts in `AnalyticsDashboard.vue` are accompanied by a `.sr-only` semantic data table (`<caption>`, `<th>`, `<td>`), enabling screen readers to access exact outflow figures.
- Progress bars provide full textual descriptions of equipment deployment: `${tool.currentlyOut} of ${tool.totalOwned} deployed (${utilizationPercent(tool)}%)`.

### F. Dark Mode Architecture
- Official Element Plus dark theme integration via `'element-plus/theme-chalk/dark/css-vars.css'`.
- Light/Dark mode toggle in the navigation header with `aria-label="Switch to dark mode"` / `aria-label="Switch to light mode"`.
- Persisted locally via `localStorage.getItem('theme')`.

---

## 14. Software Supply Chain, Dependency & Asset Compliance

### A. Dependency Locking & Version Determinism
- **Backend (`pom.xml`)**: All direct dependencies are either pinned to explicit releases (`0.12.7`, `3.0.5`) or curated by Spring Boot BOM `spring-boot-starter-parent:3.5.16`. No dynamic or unbounded version ranges are permitted.
- **Frontend (`package-lock.json`)**: Committed lockfile enforces reproducible installs via `npm ci` or `npm install`.

### B. Secrets & Credential Isolation
- Zero committed database passwords, tokens, or private keys across all production and test source code.
- Test suites dynamically read `DB_PASSWORD` from the process environment (`System.getenv("DB_PASSWORD")`).
- Frontend exposure is restricted strictly to non-sensitive `VITE_*` configuration (proxy target and timeout).

### C. Third-Party Network Isolation
- **0 Outgoing Third-Party APIs**: Backend connects only to the local/internal MySQL database; Frontend connects only to the relative `/api` route.
- **0 Trackers / Telemetry**: No Google Analytics, Meta Pixel, or telemetry SDKs.
- **0 Remote CDNs**: All JavaScript, CSS, and assets are locally bundled and served from the application host.
- **Assets & Fonts**: Native OS system fonts (`-apple-system`, `Roboto`, `sans-serif`) and Unicode symbols are used exclusively, eliminating third-party font/icon licensing liabilities.

### D. Open-Source License Management
- Permissive licenses (**MIT**, **Apache-2.0**) cover Spring Boot, MyBatis, Flyway, JJWT, Vue 3, Element Plus, Axios, and Chart.js.
- **MySQL Connector/J** (`com.mysql:mysql-connector-j` 9.2.0) is licensed under **GPL-2.0 with Universal FOSS Exception**:
  - *Internal / SaaS model*: Permissible without source code disclosure obligations.
  - *On-premise commercial appliance*: Requires legal review before distributing closed-source binaries bundled with the GPL driver (or replace with MariaDB Java Client under LGPL).
- Detailed inventory: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

### E. Automated Vulnerability Scanning
- **Dependabot**: `.github/dependabot.yml` monitors both Maven and npm dependencies on a weekly schedule.
- **Frontend Audits**: `npm audit` integrated in test lifecycle (0 vulnerabilities).
- **Automated Supply Chain Tests**: `frontend/src/api/dependency-compliance.test.js` enforces lockfile integrity, zero CDN links, and absence of secrets.

---

## 15. Audit Trail, Accountability & Security Logging

### A. Architectural Approach & Ledger Evolution
SiteFlow extends its native `transaction_logs` relational table rather than introducing heavyweight external infrastructure (e.g. Kafka, Elasticsearch, or external SIEM clusters). 
In Flyway migration `V12__audit_trail_and_accountability.sql`, the ledger is evolved with:
- Decoupled foreign key constraints (`ON DELETE RESTRICT` dropped for `user_id`, `item_id`, `location_id`) to ensure audit records remain permanent even during user deactivation, data anonymization, or seed cleanups.
- Expanded audit fields: `action` (VARCHAR 64), `resource_type` (VARCHAR 64), `resource_id` (BIGINT), `actor_username` (VARCHAR 100), `status` (VARCHAR 32), `before_state` (TEXT), `after_state` (TEXT), `details` (TEXT), and `ip_address` (VARCHAR 45).
- Composite indexes supporting fast administrative time-series and resource searches.

### B. Audit Event Model & Controlled Vocabulary
All audit records are strongly typed via `com.siteflow.domain.enums.AuditEventType`:
- **Authentication**: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `USER_DEACTIVATED`.
- **Borrowing & Custody**: `BORROW_REQUEST_CREATED`, `BORROW_REQUEST_APPROVED`, `BORROW_REQUEST_REJECTED`, `BORROW_REQUEST_CANCELLED`, `ITEM_BORROWED`, `ITEM_RETURNED`.
- **Inventory Control**: `STOCK_ADJUSTED`.
- **Procurement Pipeline**: `MATERIAL_REQUEST_CREATED`, `MATERIAL_REQUEST_APPROVED`, `MATERIAL_REQUEST_REJECTED`, `MATERIAL_REQUEST_CANCELLED`, `PURCHASE_ORDER_CREATED`.

### C. Anti-Spoofing & Actor Resolution
- **Backend Context Extraction**: `AuditService` extracts identity exclusively from `SecurityContextHolder.getContext().getAuthentication()`. Client-supplied user IDs in query parameters or request bodies are ignored.
- **System Actor**: Non-interactive or background automated processes record the actor as `SYSTEM`.
- **Credential Protection**: Login failures record only sanitized username identifiers; raw credentials, passwords, hashes, and JWTs are never recorded.

### D. Transactional Atomicity & Differential State
- **Transactional Consistency**: Domain operations and their corresponding audit records execute within the identical `@Transactional` method. If an inventory decrement or request creation encounters an error and rolls back, the audit record rolls back with it, eliminating phantom "SUCCESS" records.
- **State Transitions**: Mutations capture both `beforeState` and `afterState` (e.g., `PENDING_APPROVAL` $\to$ `APPROVED`, or `Stock: 20` $\to$ `Stock: 15`), while rejection reasons and approval notes are stored in `details`.

### E. Security Logging & Tracing
- **Correlation Propagation**: Every HTTP request receives an `X-Request-ID` via `CorrelationIdFilter`, bound to SLF4J MDC (`requestId`).
- **Authorization Failures**: `GlobalExceptionHandler` logs `AccessDeniedException` with correlation ID, authenticated user, HTTP method, and requested URI.
- **Safe Responses**: Detailed error envelopes returned to clients omit internal stack traces and server internals.

### F. Read-Only Administrative Audit API
- `GET /api/audit-logs`: Secured with `@PreAuthorize("hasRole('ADMIN')")`.
- **Server-Side Pagination**: Enforces a strict default size of 20 and maximum cap of 100 with `X-Total-Count`, `X-Page-Number`, and `X-Page-Size` response headers.
- **Dynamic Multi-Filter**: Filterable by `action`, `resourceType`, `resourceId`, `actor`, `status`, and date ranges (`startDate`, `endDate`).
- **Append-Only Immutability**: No `POST`, `PUT`, `PATCH`, or `DELETE` endpoints exist on the audit controller.

### G. Retention & Performance
- Records are preserved indefinitely in relational storage to satisfy occupational safety and equipment custody accountability.
- High-volume queries are backed by composite indexes: `(action, created_at)`, `(resource_type, resource_id)`, `(user_id, created_at)`, and `(created_at)`.

> [!IMPORTANT]
> **Developer Rule**: *Any new security-sensitive or business-critical state-changing operation must define whether it requires an audit event.*

---

## 16. Operational Gotchas & Future Roadmap

### Operational Gotchas
1. **Hikari Connection Deadlocks with `REQUIRES_NEW`**: Maintain `spring.datasource.hikari.maximum-pool-size` at $\ge 20$.
2. **Rate Limiting in Tests**: Use `RateLimiterService.resetAll()` in test fixtures if testing rapid authentication.
3. **Sole Admin Protection**: `UserService` prevents deactivating the sole remaining active `ADMIN` to avoid platform lockout.
4. **Contrast Tokens on Upgrades**: Ensure custom Element Plus theme overrides do not reset text colors to default `#909399`.
5. **Local Test Database Credentials**: Ensure `DB_PASSWORD` is set in the local environment if running `mvn test` against a password-protected MySQL database.
6. **GPL Driver Review**: Do not distribute compiled closed-source binaries bundling MySQL Connector/J without legal review of the GPL-2.0 FOSS Exception.
7. **Decoupled Audit Ledger**: Historical `transaction_logs` do not enforce foreign key cascading to allow permanent operational audit retention across user deactivation and data anonymization.

---
*Document maintained by the SiteFlow Engineering Team.*
