# SiteFlow Privacy Policy & Data Governance

**Effective Date:** September 18, 2026  
**Last Updated:** September 18, 2026  

---

## 1. System Context & Application Scope

**SiteFlow** is a specialized, full-stack material and tool logistics management system designed for construction sites. It tracks physical tools, consumables, inventory locations, borrowing lifecycles, procurement pipelines, and warehouse transactions.

### What SiteFlow Is NOT
To maintain data minimization and protect personnel privacy, SiteFlow is intentionally scoped. The application **does not** contain:
- Consumer payment processing or billing systems
- Customer subscription management
- Marketing automation or customer relationship management (CRM)
- Social media tracking, analytics beacons, or advertising pixels (e.g., Meta Pixel, Google Tag Manager)
- Artificial intelligence or automated profiling engines
- Biometric or facial recognition authentication
- Public user registration or social login mechanisms

---

## 2. Data Inventory & Data Minimization

SiteFlow operates on the principle of strict **data minimization**: we collect only the information required to guarantee accountability for physical site equipment, ensure operational continuity, and meet statutory workplace safety and inventory audit obligations.

### Data Categories

| Category | Specific Data Elements | Purpose & Justification | Storage Location | Access Controls |
|---|---|---|---|---|
| **Account Identity** | `username`, `full_name`, `job_position`, `role_id` | Identifies authorized workers and maps permissions for job-site actions | MySQL `users` table | Authenticated user (`/me`), Administrators |
| **Authentication Credentials** | `password_hash` (BCrypt, 10 rounds), `id` | Verifies user authenticity during login | MySQL `users` table | Spring Security authentication provider only; **never exposed via API** |
| **Active Session Token** | Stateless JWT (HMAC-SHA256, `sub`, `userId`, `role`) | Maintains session state across API requests | Client `sessionStorage` (`siteflow.auth`) | Browser runtime; cleared on logout/close |
| **Tool Custody & Borrowing** | `user_id`, `location_id`, `request_date`, `status`, `approved_by` | Tracks who has physical possession of tools and who authorized checkout | MySQL `borrow_requests`, `borrow_items` | Request owner, Warehouse Staff, Administrators |
| **Procurement & Material Requests** | `requested_by`, `justification`, line items, `approved_by` | Tracks material requisitions and purchase orders for site construction | MySQL `material_requests`, `purchase_orders` | Request author, Procurement officers, Administrators |
| **Inventory Ledger & Audit** | `item_id`, `location_id`, `user_id`, `qty_change`, `timestamp` | Immutable audit trail for equipment returns and warehouse adjustments | MySQL `transaction_logs` | Administrators (aggregate analytics only) |
| **Submission Idempotency** | `key_value`, `user_id`, `endpoint`, `status` | Prevents accidental duplicate requests during network retries | MySQL `idempotency_keys` | Internal backend service (`IdempotencyService`) |

SiteFlow **never** collects: personal email addresses, phone numbers, home addresses, national identity/tax numbers, GPS tracking coordinates, or user profile pictures.

---

## 3. Sensitive Data Protection & API Defense

1. **Zero Secret / Hash Exposure:**
   - Password hashes (`password_hash`), passwords, database credentials, and JWT signing secrets are strictly filtered out of all API endpoints and DTO envelopes.
   - Dedicated tests verify that responses to `/api/auth/login`, `/api/auth/me`, `/api/borrow-requests/*`, and `/api/procurement/*` never leak credentials or password hashes.
2. **Privacy-Preserving Authorization (IDOR Defense):**
   - Ordinary workers (`FIELD_STAFF`) can only view their own borrow requests (`/api/borrow-requests/my` or `/api/borrow-requests/{id}`) and material requests.
   - Attempting to access another worker's request ID returns HTTP `403 Forbidden`.
   - Administrative dashboards (`/api/approvals/*`, `/api/analytics/*`) are strictly guarded by `@PreAuthorize("hasRole('ADMIN')")`.
3. **Centralized Error Masking:**
   - Unhandled exceptions, SQL syntax errors, or database constraint violations are logged internally with distributed correlation IDs (`X-Request-ID`), while clients receive only sanitized, user-friendly envelopes without stack traces or SQL snippets.

---

## 4. Cookies & Client-Side Storage Policy

- **Cookies:** SiteFlow uses **0 cookies**. No session cookies, tracking cookies, or third-party cookies are set or read.
- **Local Storage:** `localStorage` is not used.
- **Session Storage:** A single entry `siteflow.auth` holds `{ username, token, role }` in `sessionStorage` strictly to maintain the active authenticated session. This data is destroyed when the user clicks "Log Out" or closes the browser tab.
- **Third-Party Trackers:** No remote analytics libraries, fonts from external CDNs, or telemetry scripts are embedded. All assets and libraries (Vue 3, Element Plus, Chart.js) are bundled locally.

---

## 5. Data Retention Principles

Data retention is strictly aligned with construction project governance and statutory audit requirements:

1. **Account Data:** Retained while the worker is actively employed or assigned to the project. Upon deactivation, personal details are scrubbed while preserving relational integrity.
2. **Transactional & Equipment Records:** Borrowing records, return receipts, material requests, and purchase orders are retained for the active duration of the project plus statutory retention periods (typically 5–7 years) to support equipment warranties, insurance claims, and tax audits.
3. **Internal Transaction Logs:** Permanent append-only ledger (`transaction_logs`) maintained for physical inventory reconciliation.
4. **Idempotency Keys:** Ephemeral records purged periodically after the deduplication window has lapsed.

---

## 6. Account Deactivation & Data Anonymization Workflow

Under SiteFlow's privacy governance model, users may request the deletion or deactivation of their account:

### Why Not Hard-Delete?
Hard-deleting a user record would cascade or fail against foreign key constraints (`ON DELETE RESTRICT`) on historical borrowing records, tool loss reports, and audit logs. Removing the user record would destroy physical asset accountability and violate safety audit compliance.

### The Deactivate & Anonymize Mechanism
When an account is deactivated (via self-service `POST /api/users/me/deactivate` or administrator action `POST /api/users/{id}/deactivate`):
1. **Account Deactivated:** `users.is_active` is set to `FALSE` and `deactivated_at` is timestamped.
2. **Personal Data Scrubbed:** `full_name` is replaced with an anonymous placeholder (`Anonymized User #[id]`) and `job_position` is set to `NULL`.
3. **Credentials Invalidated:** `password_hash` is overwritten with an unmatchable randomized nonce (`DEACTIVATED_[UUID]`).
4. **Authentication Blocked:** Spring Security's `DbUserDetailsService` immediately rejects login attempts with `401 Unauthorized` (`User account is deactivated.`).
5. **Business & Safety Records Preserved:** Past tool borrowing transactions and approval notes remain intact, linked to the anonymized user ID.

---

## 7. Third-Party Dependency Privacy Audit

All backend and frontend dependencies have been audited for data transmission:

| Package / Module | Ecosystem | Purpose | External Data Sent? |
|---|---|---|---|
| `spring-boot-starter-web` | Maven / Java | REST API controllers | None |
| `spring-boot-starter-security` | Maven / Java | Stateless JWT filter chain | None |
| `jjwt` (io.jsonwebtoken) | Maven / Java | Token generation & verification | None |
| `mybatis` / `mysql-connector-j` | Maven / Java | Database persistence | None |
| `vue` / `vue-router` | npm / JS | SPA interface & routing | None |
| `element-plus` | npm / JS | UI components & styling | None (bundled locally) |
| `chart.js` / `vue-chartjs` | npm / JS | Dashboard charts | None |
| `axios` | npm / JS | HTTP client | None (communicates only with `/api`) |

---

## 8. Organizational Placeholders Requiring Confirmation

The technical implementation described in this document is fully operational in the SiteFlow codebase. However, organizational and legal governance policies require review and confirmation by authorized site management:

- `[Operating Organization / Legal Contractor Name]`
- `[Designated Data Governance Officer / IT Administrator]`
- `[Contact Email / Administrative Channel]`
- `[Physical Business Address / Site Headquarters]`
- `[Specific Statutory Record Retention Period (e.g. 5 vs 7 years under local construction law)]`
