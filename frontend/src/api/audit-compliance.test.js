import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const frontendRoot = path.resolve(__dirname, '../..')

test('1. AuditLogView.vue exists and enforces accessible structure', () => {
  const auditViewPath = path.join(frontendRoot, 'src/views/AuditLogView.vue')
  assert.ok(fs.existsSync(auditViewPath), 'AuditLogView.vue must exist in src/views')

  const content = fs.readFileSync(auditViewPath, 'utf8')

  // Accessible landmarks and semantic structure
  assert.ok(content.includes('id="main-content"'), 'Must have id="main-content" for keyboard skip-link')
  assert.ok(content.includes('role="main"'), 'Must specify role="main"')
  assert.ok(content.includes('<h1'), 'Must declare a single h1 heading')
  assert.ok(content.includes('aria-label='), 'Must define accessible aria labels')

  // Non-color-reliant status symbols (Step 8 compliance)
  assert.ok(content.includes('✓'), 'Success status must include checkmark symbol')
  assert.ok(content.includes('✕'), 'Rejected status must include cross symbol')
  assert.ok(content.includes('⚠'), 'Failure status must include warning symbol')

  // State transitions and inspection drawer
  assert.ok(content.includes('state-transition'), 'Must render state transition visualizer')
  assert.ok(content.includes('el-drawer'), 'Must provide an inspection drawer for detailed audit event context')
  assert.ok(content.includes('el-pagination'), 'Must include pagination controls')
})

test('2. Router protects /audit route with ADMIN role metadata', () => {
  const routerPath = path.join(frontendRoot, 'src/router/index.js')
  const content = fs.readFileSync(routerPath, 'utf8')

  assert.ok(content.includes("path: '/audit'"), 'Route /audit must be defined')
  assert.ok(content.includes("AuditLogView"), 'Route must reference AuditLogView component')
  assert.ok(
    content.includes("meta: { roles: ['ADMIN'] }") || content.includes("roles: ['ADMIN']"),
    '/audit route must be strictly restricted to ADMIN role'
  )
})

test('3. App.vue navigation provides Audit Trail link visible only to ADMIN', () => {
  const appPath = path.join(frontendRoot, 'src/App.vue')
  const content = fs.readFileSync(appPath, 'utf8')

  assert.ok(
    content.includes('index="/audit"'),
    'App navigation menu must include Audit Trail link (/audit)'
  )
  assert.ok(
    content.includes("auth.role === 'ADMIN'"),
    'Audit Trail link must be guarded by auth.role === ADMIN'
  )
})

test('4. Audit views are strictly read-only and contain no secrets or arbitrary mutation operations', () => {
  const auditViewPath = path.join(frontendRoot, 'src/views/AuditLogView.vue')
  const content = fs.readFileSync(auditViewPath, 'utf8')

  // Verify no mutation endpoints or mutation forms exist
  assert.strictEqual(content.includes('http.delete'), false, 'Audit view must not expose delete operations')
  assert.strictEqual(content.includes('http.put'), false, 'Audit view must not expose update operations')
  assert.strictEqual(content.includes('http.patch'), false, 'Audit view must not expose patch operations')

  // Verify no credentials or sensitive tokens are rendered
  const forbiddenSensitiveFields = ['passwordHash', 'jwtSecret', 'rawPassword', 'accessToken']
  for (const field of forbiddenSensitiveFields) {
    assert.strictEqual(
      content.toLowerCase().includes(field.toLowerCase()),
      false,
      `Audit view must never reference or render ${field}`
    )
  }
})
