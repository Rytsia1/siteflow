import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveNotificationTarget } from './accessibility-utils.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

test('1. Material Request approval notification deep links directly to /approvals?tab=material for ADMIN', () => {
  const notif = {
    id: 1,
    type: 'MATERIAL_REQUEST_APPROVAL',
    referenceType: 'MATERIAL_REQUEST',
    referenceId: 'MR-2026-014',
    title: 'Material Request MR-2026-014 requires approval',
  }

  const target = resolveNotificationTarget(notif, 'ADMIN')
  assert.strictEqual(target.path, '/approvals')
  assert.strictEqual(target.query.tab, 'material')
  assert.strictEqual(target.query.highlight, 'MR-2026-014')
})

test('2. Material Request rejected notification navigates to /procurement?tab=pending preserving referenceId', () => {
  const notif = {
    id: 4,
    type: 'MATERIAL_REQUEST_REJECTED',
    referenceType: 'MATERIAL_REQUEST',
    referenceId: 'MR-2026-018',
    title: 'Material Request MR-2026-018 was rejected',
  }

  const target = resolveNotificationTarget(notif, 'FIELD_STAFF')
  assert.strictEqual(target.path, '/procurement')
  assert.strictEqual(target.query.tab, 'pending')
  assert.strictEqual(target.query.highlight, 'MR-2026-018')
})

test('3. Low stock notification deep links to /inventory with itemCode query', () => {
  const notif = {
    id: 3,
    type: 'LOW_STOCK',
    referenceType: 'ITEM',
    referenceId: 'MT-1042',
    title: 'Low stock: Safety Gloves',
  }

  const target = resolveNotificationTarget(notif, 'WAREHOUSE_STAFF')
  assert.strictEqual(target.path, '/inventory')
  assert.strictEqual(target.query.itemCode, 'MT-1042')
  assert.strictEqual(target.query.highlight, 'MT-1042')
})

test('4. Borrow Request action routes to /approvals for ADMIN and /borrow for FIELD_STAFF', () => {
  const notif = {
    id: 2,
    type: 'BORROW_REQUEST_ACTION',
    referenceType: 'BORROW_REQUEST',
    referenceId: 'BR-2026-008',
    title: 'Borrow Request BR-2026-008 requires action',
  }

  // Admin routing
  const adminTarget = resolveNotificationTarget(notif, 'ADMIN')
  assert.strictEqual(adminTarget.path, '/approvals')
  assert.strictEqual(adminTarget.query.tab, 'borrow')
  assert.strictEqual(adminTarget.query.highlight, 'BR-2026-008')

  // Field staff routing
  const fieldTarget = resolveNotificationTarget(notif, 'FIELD_STAFF')
  assert.strictEqual(fieldTarget.path, '/borrow')
  assert.strictEqual(fieldTarget.query.tab, 'return')
  assert.strictEqual(fieldTarget.query.requestId, 'BR-2026-008')
})

test('5. Asset overdue notification routes to /assets for WAREHOUSE_STAFF and respects RBAC for FIELD_STAFF', () => {
  const notif = {
    id: 5,
    type: 'ASSET_OVERDUE',
    referenceType: 'ASSET',
    referenceId: 'AST-0042',
    title: 'Asset AST-0042 is overdue',
  }

  // Warehouse staff can access /assets
  const warehouseTarget = resolveNotificationTarget(notif, 'WAREHOUSE_STAFF')
  assert.strictEqual(warehouseTarget.path, '/assets')
  assert.strictEqual(warehouseTarget.query.mode, 'return')
  assert.strictEqual(warehouseTarget.query.code, 'AST-0042')

  // Field staff cannot access /assets (route guard), so they are routed to /borrow return form
  const fieldTarget = resolveNotificationTarget(notif, 'FIELD_STAFF')
  assert.strictEqual(fieldTarget.path, '/borrow')
  assert.strictEqual(fieldTarget.query.tab, 'return')
  assert.strictEqual(fieldTarget.query.requestId, 'AST-0042')
})

test('6. Purchase Order received notification deep links to /procurement?tab=pos with poNumber', () => {
  const notif = {
    id: 6,
    type: 'PURCHASE_ORDER_RECEIVED',
    referenceType: 'PURCHASE_ORDER',
    referenceId: 'PO-2026-021',
    title: 'Purchase Order PO-2026-021 has been received',
  }

  const target = resolveNotificationTarget(notif, 'PROCUREMENT')
  assert.strictEqual(target.path, '/procurement')
  assert.strictEqual(target.query.tab, 'pos')
  assert.strictEqual(target.query.poNumber, 'PO-2026-021')
})

test('7. App.vue notification center supports read/unread persistence and full data model', () => {
  const appVuePath = path.resolve(__dirname, '../App.vue')
  const appVueContent = fs.readFileSync(appVuePath, 'utf8')

  // Verification of data model fields in App.vue
  assert.ok(appVueContent.includes('referenceType:'), 'Notification items must declare referenceType')
  assert.ok(appVueContent.includes('referenceId:'), 'Notification items must declare referenceId')
  assert.ok(appVueContent.includes('read:'), 'Notification items must track read state')
  assert.ok(appVueContent.includes('createdAt:'), 'Notification items must provide createdAt')

  // Verification of session storage persistence
  assert.ok(appVueContent.includes('siteflow.notifications.read'), 'Must persist read notifications in session storage')
  assert.ok(appVueContent.includes('markAllAsRead'), 'Must provide markAllAsRead operation')
  assert.ok(appVueContent.includes('handleNotificationClick'), 'Must handle notification click interactions')

  // Verification of states in template
  assert.ok(appVueContent.includes('sf-notif-loading'), 'Must support notification loading state')
  assert.ok(appVueContent.includes('sf-notif-error'), 'Must support notification error state')
  assert.ok(appVueContent.includes('sf-notif-empty'), 'Must support notification empty state')
})

test('8. Operational views support contextual record highlighting upon deep link navigation', () => {
  // 1. ApprovalDashboard.vue
  const approvalsContent = fs.readFileSync(path.resolve(__dirname, '../views/ApprovalDashboard.vue'), 'utf8')
  assert.ok(approvalsContent.includes('checkDeepLink'), 'ApprovalDashboard must parse route queries')
  assert.ok(approvalsContent.includes('sf-card-highlight'), 'ApprovalDashboard must highlight matched card')
  assert.ok(approvalsContent.includes('sf-row-highlight'), 'ApprovalDashboard must highlight matched table row')

  // 2. InventoryList.vue
  const inventoryContent = fs.readFileSync(path.resolve(__dirname, '../views/InventoryList.vue'), 'utf8')
  assert.ok(inventoryContent.includes('checkDeepLink'), 'InventoryList must parse route queries')
  assert.ok(inventoryContent.includes('sf-row-highlight'), 'InventoryList must highlight matched item')

  // 3. BorrowForm.vue
  const borrowContent = fs.readFileSync(path.resolve(__dirname, '../views/BorrowForm.vue'), 'utf8')
  assert.ok(borrowContent.includes('checkDeepLink'), 'BorrowForm must parse route queries')
  assert.ok(borrowContent.includes('returnForm.requestId'), 'BorrowForm must populate return request ID')

  // 4. ProcurementView.vue
  const procurementContent = fs.readFileSync(path.resolve(__dirname, '../views/ProcurementView.vue'), 'utf8')
  assert.ok(procurementContent.includes('checkDeepLink'), 'ProcurementView must parse route queries')
  assert.ok(procurementContent.includes('sf-row-highlight'), 'ProcurementView must highlight matched requisition or PO')

  // 5. AssetScanner.vue
  const scannerContent = fs.readFileSync(path.resolve(__dirname, '../views/AssetScanner.vue'), 'utf8')
  assert.ok(scannerContent.includes('checkDeepLink'), 'AssetScanner must parse route queries')
  assert.ok(scannerContent.includes('scanValue'), 'AssetScanner must pre-fill scanned code')
})
