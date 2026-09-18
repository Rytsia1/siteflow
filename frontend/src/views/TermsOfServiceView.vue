<template>
  <div class="legal-container">
    <div class="legal-card">
      <div class="legal-header">
        <router-link to="/login" class="back-link">&larr; Back to Application</router-link>
        <h1>Terms of Service</h1>
        <p class="subtitle">Effective Date: September 18, 2026 | Last Updated: September 18, 2026</p>
      </div>

      <div class="legal-content">
        <section>
          <h2>1. Acceptance of Terms</h2>
          <p>
            By accessing or using the <strong>SiteFlow</strong> material and tool logistics management platform ("Service"), you agree to abide by these Terms of Service. This system is provisioned exclusively for authorized construction personnel, warehouse managers, procurement officers, and project administrators affiliated with <strong>[Operating Organization / Company Name]</strong>.
          </p>
        </section>

        <section>
          <h2>2. Authorized Use & Account Responsibilities</h2>
          <ul>
            <li><strong>Credential Confidentiality:</strong> You are responsible for safeguarding your login credentials. Do not share user accounts or passwords with other site workers.</li>
            <li><strong>Role-Based Authority:</strong> You agree to operate strictly within the boundaries of your assigned role (e.g. Field Staff, Warehouse Staff, Procurement, Administrator).</li>
            <li><strong>Physical Asset Accountability:</strong> When borrowing physical tools or equipment recorded in SiteFlow, you acknowledge physical custody and responsibility for returning items in good working condition within project timelines.</li>
            <li><strong>Data Accuracy:</strong> All quantities, item codes, location selections, and procurement justifications entered into SiteFlow must accurately reflect physical job-site reality.</li>
          </ul>
        </section>

        <section>
          <h2>3. Prohibited Activities</h2>
          <p>Users of the Service shall not:</p>
          <ul>
            <li>Attempt to bypass or tamper with role-based access controls, resource-level ownership checks, or rate limiting systems.</li>
            <li>Submit fraudulent material requests, false tool return records, or unauthorized stock adjustments.</li>
            <li>Attempt unauthorized access to other workers' requests or system audit records.</li>
            <li>Inject malicious scripts, exploit vulnerabilities, or disrupt server operations.</li>
          </ul>
        </section>

        <section>
          <h2>4. System Availability & Service Modifications</h2>
          <p>
            SiteFlow is maintained to support daily site operations. Maintenance windows, software updates, or database optimizations may temporarily restrict access. The Organization reserves the right to update or modify features to enhance security, compliance, or operational efficiency.
          </p>
        </section>

        <section>
          <h2>5. Account Suspension & Termination</h2>
          <p>
            The Organization reserves the right to suspend or deactivate user accounts upon employment conclusion, project completion, or violation of these terms. Upon account deactivation, personal identifying details are anonymized in accordance with our Privacy Policy, while historical logistics records are retained for statutory audit compliance.
          </p>
        </section>

        <section>
          <h2>6. Intellectual Property</h2>
          <p>
            The SiteFlow software, architecture, database schemas, interfaces, and documentation are proprietary assets of <strong>[Operating Organization / Platform Owner]</strong>.
          </p>
        </section>

        <section>
          <h2>7. Contact & Administration</h2>
          <p>
            For questions regarding these Terms of Service or to report discrepancies in site equipment records, contact the site administration team:
          </p>
          <ul>
            <li><strong>Operating Organization:</strong> [Legal Entity / Organization Name]</li>
            <li><strong>Administrative Contact:</strong> [Designated Project Administrator Contact]</li>
            <li><strong>Office Location:</strong> [Business / Site Office Address]</li>
          </ul>
        </section>
      </div>

      <div class="legal-footer">
        <router-link to="/privacy" class="legal-nav-link">Privacy Policy</router-link>
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
