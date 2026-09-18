<template>
  <div class="legal-container">
    <div class="legal-card">
      <div class="legal-header">
        <router-link to="/login" class="back-link">&larr; Back to Application</router-link>
        <h1>Privacy Policy</h1>
        <p class="subtitle">Effective Date: September 18, 2026 | Last Updated: September 18, 2026</p>
      </div>

      <div class="legal-content">
        <section>
          <h2>1. Introduction & Scope</h2>
          <p>
            <strong>SiteFlow</strong> is an internal construction site material, tool, and equipment logistics management system operated by <strong>[Operating Organization / Company Name]</strong> ("Organization", "we", "us"). This Privacy Policy explains how SiteFlow collects, uses, stores, and protects personal data and user-associated information in connection with construction site operations.
          </p>
          <div class="notice-box">
            <strong>System Context:</strong> SiteFlow is an enterprise operations tool designed exclusively for physical asset tracking, tool borrowing, material requisitions, and warehouse inventory control. It is <em>not</em> an e-commerce platform and does not process consumer transactions, payment data, marketing tracking, advertising pixels, or biometric data.
          </div>
        </section>

        <section>
          <h2>2. Personal Data We Collect</h2>
          <p>We adhere to the principle of <strong>data minimization</strong>, collecting only the minimal information strictly necessary to manage job-site logistics, accountability, and workplace safety:</p>
          <ul>
            <li><strong>Account Identity Information:</strong> Username, full name, job position/title, and role assignment (e.g., Administrator, Warehouse Staff, Field Staff).</li>
            <li><strong>Authentication Data:</strong> Cryptographically salted and hashed passwords (using BCrypt) and temporary, short-lived JSON Web Tokens (JWT).</li>
            <li><strong>Operational & Logistics Records:</strong> Records of tool borrowings, returns, material requests, and purchase orders submitted or approved by your account.</li>
            <li><strong>System & Audit Metadata:</strong> Internal transaction logs recording equipment custody transfers, timestamped inventory movements, and request correlation IDs (<code>X-Request-ID</code>).</li>
          </ul>
          <p>
            <em>Note:</em> SiteFlow does <strong>not</strong> collect personal email addresses, personal phone numbers, home addresses, government identification numbers, geolocation coordinates, or biometric information.
          </p>
        </section>

        <section>
          <h2>3. Purpose of Data Processing</h2>
          <p>We process your data exclusively for legitimate operational business purposes:</p>
          <ul>
            <li>Authenticating authorized personnel and enforcing role-based access control (RBAC).</li>
            <li>Tracking physical custody and return accountability of construction equipment and high-value tools.</li>
            <li>Managing the procurement workflow and material requisitions for active job sites.</li>
            <li>Fulfilling workplace safety, statutory equipment maintenance, and financial inventory audit obligations.</li>
          </ul>
        </section>

        <section>
          <h2>4. Cookies & Client Storage Architecture</h2>
          <p>We believe in privacy by design and zero unnecessary tracking:</p>
          <ul>
            <li><strong>Cookies:</strong> SiteFlow uses <strong>zero tracking or advertising cookies</strong>. No marketing or profiling cookies are ever placed on your device.</li>
            <li><strong>Local Storage:</strong> SiteFlow does not store authentication credentials or tracking tokens in persistent <code>localStorage</code>.</li>
            <li><strong>Session Storage:</strong> A stateless JWT access token is held exclusively in browser <code>sessionStorage</code> for the duration of your active browser session and is automatically purged upon logout or browser tab closure.</li>
            <li><strong>Third-Party Analytics:</strong> No third-party analytics trackers, telemetry collectors, social media pixels (e.g., Meta Pixel), or advertising beacons are loaded.</li>
          </ul>
        </section>

        <section>
          <h2>5. Data Retention & Preservation of Business Records</h2>
          <p>Data retention follows clear functional categories:</p>
          <ul>
            <li><strong>Active Account Data:</strong> Retained while the worker or staff member is actively assigned to construction site projects.</li>
            <li><strong>Historical Transaction & Equipment Records:</strong> Borrowing logs, approval audit trails, material requests, and transaction logs are retained for the statutory duration of construction project compliance and financial auditing (typically 5–7 years).</li>
            <li><strong>Security & Idempotency Records:</strong> Temporary submission deduplication keys are automatically expired and purged within short operational windows.</li>
          </ul>
        </section>

        <section>
          <h2>6. Data Deletion, Anonymization & User Rights</h2>
          <p>
            SiteFlow provides an <strong>Account Deactivation & Data Anonymization</strong> mechanism:
          </p>
          <ul>
            <li>When an account deactivation/data deletion is processed, personal identifying details (full name and job position) are scrubbed and replaced with generic anonymized identifiers (e.g., <em>Anonymized User #[ID]</em>).</li>
            <li>Password credentials are scrambled with cryptographically randomized nonces, permanently preventing subsequent login.</li>
            <li>To preserve regulatory safety, warehouse inventory balance, and statutory audit integrity, historical logistics records (what tools were checked out and returned) remain associated with the anonymized reference identifier.</li>
            <li>Users may request account deactivation directly through the application or by contacting the system administrator at <strong>[Data Governance Contact / Administrator Contact]</strong>.</li>
          </ul>
        </section>

        <section>
          <h2>7. Information Security Practices</h2>
          <p>
            We implement defense-in-depth technical safeguards to protect all stored information:
          </p>
          <ul>
            <li>Stateless HMAC-SHA256 signed tokens with short expiration lifespans.</li>
            <li>Strict role-based and resource-level authorization ensuring workers can only view their own borrow and material requests.</li>
            <li>Server-side rate limiting to mitigate brute-force authentication attempts.</li>
            <li>Pessimistic database row-level locking to ensure transactional inventory integrity.</li>
            <li>Sanitized error responses ensuring internal database queries, credentials, and stack traces are never exposed to clients.</li>
          </ul>
        </section>

        <section>
          <h2>8. Organizational Placeholders & Inquiries</h2>
          <p>
            This policy document describes the technical implementation of SiteFlow version 1.0. Specific organizational, legal, and operational parameters require verification by site management:
          </p>
          <ul>
            <li><strong>Operating Entity:</strong> [Legal Entity / Organization Name]</li>
            <li><strong>Registered Address:</strong> [Business / Office Address]</li>
            <li><strong>Data Governance Contact:</strong> [Designated Privacy / IT Administrator Contact]</li>
            <li><strong>Support / Administrative Inquiries:</strong> [Dedicated Support Contact]</li>
          </ul>
        </section>
      </div>

      <div class="legal-footer">
        <router-link to="/terms" class="legal-nav-link">Terms of Service</router-link>
        <span class="divider">|</span>
        <router-link to="/login" class="legal-nav-link">Return to Login</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.legal-container {
  min-height: 100vh;
  background-color: var(--el-bg-color-page, #f5f7fa);
  padding: 40px 20px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

.legal-card {
  background: var(--el-bg-color, #ffffff);
  max-width: 860px;
  width: 100%;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  padding: 40px;
  box-sizing: border-box;
}

.legal-header {
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  padding-bottom: 24px;
  margin-bottom: 28px;
}

.back-link {
  display: inline-block;
  color: var(--el-color-primary, #2b73c4);
  text-decoration: underline;
  font-size: 14px;
  margin-bottom: 16px;
}

.back-link:hover,
.back-link:focus-visible {
  color: #1a4f8b;
}

h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary, #1f2f3d);
  margin: 0 0 8px 0;
}

.subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary, #595959);
  margin: 0;
}

.legal-content section {
  margin-bottom: 32px;
}

h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  padding-bottom: 6px;
}

p {
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular, #4a5568);
  margin: 0 0 12px 0;
}

ul {
  padding-left: 20px;
  margin: 0 0 16px 0;
}

li {
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular, #4a5568);
  margin-bottom: 6px;
}

code {
  background: var(--el-fill-color-light, #f0f2f5);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #c02d42;
  font-family: monospace;
}

.notice-box {
  background: var(--el-color-warning-light-9, #fdf6ec);
  border-left: 4px solid var(--el-color-warning, #e6a23c);
  padding: 12px 16px;
  border-radius: 0 4px 4px 0;
  font-size: 13px;
  color: var(--el-text-color-primary, #7d5a29);
  line-height: 1.6;
  margin: 16px 0;
}

.legal-footer {
  border-top: 1px solid var(--el-border-color-light, #e4e7ed);
  padding-top: 24px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-secondary, #595959);
}

.legal-nav-link {
  color: var(--el-color-primary, #2b73c4);
  text-decoration: underline;
}

.legal-nav-link:hover,
.legal-nav-link:focus-visible {
  color: #1a4f8b;
}

.divider {
  margin: 0 12px;
  color: var(--el-border-color, #dcdfe6);
}
</style>
