# Third-Party Software Notices and Asset Licenses

This document lists the third-party open-source software libraries, frameworks, visual assets, and fonts utilized by **SiteFlow**, along with their respective licenses, operational roles, data handling practices, and legal distribution considerations.

---

## 1. Executive Summary & Distribution Context

SiteFlow is an internal material and equipment logistics platform. In its standard deployment model:
- The **Backend** runs as an internal Java web service.
- The **Frontend** runs as a static single-page application (SPA) communicating exclusively with the backend API.
- **Zero third-party tracking, analytics, telemetry, or remote CDN services** are integrated.
- All dependencies are licensed under permissive open-source licenses (**MIT**, **Apache-2.0**) with the exception of the MySQL database driver, which is governed by **GPL-2.0 with the Universal FOSS Exception**.

---

## 2. Backend Dependency Inventory (`pom.xml`)

| Package / Artifact | Version | License | Direct / Transitive | Runtime Critical | Processes User Data | Outgoing Network Calls |
|---|---|---|---|---|---|---|
| **Spring Boot Starter Web** (`org.springframework.boot`) | 3.5.0 | Apache-2.0 | Direct | Yes | Yes (HTTP requests) | None |
| **Spring Boot Starter Validation** (`org.springframework.boot`) | 3.5.0 | Apache-2.0 | Direct | Yes | Yes (DTO validation) | None |
| **Spring Boot Starter Cache** (`org.springframework.boot`) | 3.5.0 | Apache-2.0 | Direct | Yes | No (cache metadata) | None |
| **Spring Boot Starter Actuator** (`org.springframework.boot`) | 3.5.0 | Apache-2.0 | Direct | Yes | No (health indicators) | None |
| **Spring Boot Starter Security** (`org.springframework.boot`) | 3.5.0 | Apache-2.0 | Direct | Yes | Yes (auth credentials) | None |
| **MyBatis Spring Boot Starter** (`org.mybatis.spring.boot`) | 3.0.4 | Apache-2.0 | Direct | Yes | Yes (SQL persistence) | None (Local DB only) |
| **MySQL Connector/J** (`com.mysql:mysql-connector-j`) | 9.2.0 | GPL-2.0 with FOSS Exception | Direct | Yes | Yes (transmits queries) | Local / network MySQL |
| **Flyway Core & MySQL** (`org.flywaydb`) | 11.7.2 | Apache-2.0 | Direct | Yes | Yes (schema migrations) | Local / network MySQL |
| **Project Lombok** (`org.projectlombok:lombok`) | 1.18.38 | MIT | Direct | No (compile-time) | No | None |
| **JJWT** (`io.jsonwebtoken:jjwt-api`, `impl`, `jackson`) | 0.12.6 | Apache-2.0 | Direct | Yes | Yes (token claims) | None |
| **Spring Boot Starter Test** (`org.springframework.boot`) | 3.5.0 | Apache-2.0 | Direct | No (test only) | No (mock data) | None |
| **Spring Security Test** (`org.springframework.security`) | 6.5.0 | Apache-2.0 | Direct | No (test only) | No (mock data) | None |
| **HikariCP** (`com.zaxxer:HikariCP`) | 6.3.0 | Apache-2.0 | Transitive | Yes | No (connection pooling) | Local / network MySQL |
| **Jackson Databind** (`com.fasterxml.jackson.core`) | 2.19.0 | Apache-2.0 | Transitive | Yes | Yes (JSON parsing) | None |
| **Logback Classic** (`ch.qos.logback:logback-classic`) | 1.5.18 | EPL-1.0 / LGPL-2.1 | Transitive | Yes | Yes (sanitized logs) | None |

---

## 3. Frontend Dependency Inventory (`frontend/package.json`)

| Package Name | Version | License | Direct / Transitive | Runtime Critical | Processes User Data | Outgoing Network Calls |
|---|---|---|---|---|---|---|
| **vue** | 3.5.13 (installed: 3.5.42) | MIT | Direct | Yes | Yes (UI state) | None |
| **vue-router** | 4.5.0 (installed: 4.6.4) | MIT | Direct | Yes | Yes (route access) | None |
| **element-plus** | 2.9.1 | MIT | Direct | Yes | Yes (forms & tables) | None |
| **axios** | 1.7.9 | MIT | Direct | Yes | Yes (API transport) | Internal `/api` only |
| **chart.js** | 4.5.1 | MIT | Direct | No (analytics only) | No (aggregate counts) | None |
| **vue-chartjs** | 5.3.4 | MIT | Direct | No (analytics only) | No (aggregate counts) | None |
| **vite** | 6.0.7 (installed: 6.4.3) | MIT | Direct | No (build / dev) | No | None |
| **@vitejs/plugin-vue** | 5.2.1 (installed: 5.2.4) | MIT | Direct | No (build / dev) | No | None |

---

## 4. Visual Assets, Fonts & Icon Licensing

### A. Typography & Fonts
- **Font Stack**: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif, monospace`.
- **Hosting Model**: 100% native client-side system fonts.
- **External Requests**: Zero network calls to Google Fonts, Adobe Typekit, or external font CDNs.
- **License Status**: Native operating system fonts carry no distribution or commercial licensing liability for web user interfaces.

### B. Icons & Graphical Embellishments
- **Icon Implementation**: Native Unicode emojis and standard characters (`🔔`, `☀️`, `🌙`, `✓`, `⚠️`, `✕`, `⏳`, `📦`).
- **Hosting Model**: Built into standard Unicode character sets rendered by the client device.
- **License Status**: Public standard under the Unicode Consortium; zero proprietary asset licensing overhead.

### C. Images & Illustrations
- **Image Files**: The application intentionally does not ship or download static stock photos, marketing illustrations, or raster logo images.
- **Brand Elements**: "SiteFlow" branding is rendered purely via CSS and typography.
- **License Status**: Zero proprietary or copyrighted external imagery.

---

## 5. Third-Party Network Services & Privacy Review

A comprehensive audit of the frontend and backend source code confirms:

1. **Zero Third-Party APIs**: Neither the backend nor the frontend initiates requests to external SaaS APIs, payment gateways, map providers, or social platforms.
2. **Zero Analytics / Telemetry SDKs**: No Google Analytics, Mixpanel, Segment, Meta Pixel, Sentry, Datadog, or telemetry collectors are loaded or executed.
3. **Zero Content Delivery Networks (CDNs)**: All CSS, JavaScript, and fonts are compiled, bundled, and served locally from the application bundle.
4. **Internal Network Boundary**:
   - Frontend communicates exclusively with the relative endpoint `/api` on the backend.
   - Backend communicates exclusively with the local MySQL database instance on port 3306.

---

## 6. Open-Source License Compatibility Analysis

### Permissive Licenses (MIT & Apache-2.0)
- **Status**: Fully compatible with proprietary or internal enterprise distribution.
- **Requirements**: Retain copyright notices and disclaimers in source headers. No requirement to open-source proprietary SiteFlow application code.

### MySQL Connector/J (GPL-2.0 with Universal FOSS Exception)
- **Current Model (Internal / SaaS)**: SiteFlow operates as a network-accessible server application. Under standard GPL-2.0, network execution does not trigger copyleft source-distribution requirements.
- **Commercial On-Premise Distribution**: If SiteFlow is ever compiled and distributed as a closed-source binary or virtual appliance to third-party customers, bundling `mysql-connector-j` triggers legal review under the GPL-2.0 Universal FOSS Exception.
- **Recommendation**:
  > *Requires legal/license review before commercial closed-source redistribution.* Alternatively, switch to an LGPL/Apache-compatible driver such as `org.mariadb.jdbc:mariadb-java-client`.

---

## 7. Supply Chain & Vulnerability Management Policy

1. **Automated Dependabot Monitoring**: `.github/dependabot.yml` is configured to run weekly dependency audits across both `maven` and `npm`.
2. **Deterministic Locking**:
   - `frontend/package-lock.json` is committed and strictly enforces subdependency integrity.
   - `pom.xml` pins dependencies via `spring-boot-starter-parent:3.5.0` without floating or dynamic version ranges.
3. **Local Vulnerability Audits**:
   - Frontend: `npm audit` (verified: 0 vulnerabilities).
   - Backend: Periodic Maven dependency analysis and OWASP Dependency-Check.
4. **Secrets Policy**: Zero credentials, passwords, or JWT secrets are permitted in source code, configuration files, or test fixtures. All credentials must be injected at runtime via environment variables (`DB_PASSWORD`, `JWT_SECRET`).
