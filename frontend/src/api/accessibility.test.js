import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  getContrastRatio,
  isWcagAaCompliant,
  formatStatusLabel,
  formatStockDisplay,
  resolveNotificationRoute,
  getEmptyStateMessage,
} from './accessibility-utils.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

test('1. Light mode text tokens satisfy WCAG AA contrast (>= 4.5:1)', () => {
  const whiteBg = '#ffffff'

  // Primary text (#1f2f3d)
  const primaryRatio = getContrastRatio('#1f2f3d', whiteBg)
  assert.ok(primaryRatio >= 4.5, `Primary text ratio ${primaryRatio} must be >= 4.5:1`)

  // Regular body text (#4a5568)
  const regularRatio = getContrastRatio('#4a5568', whiteBg)
  assert.ok(regularRatio >= 4.5, `Regular text ratio ${regularRatio} must be >= 4.5:1`)

  // Secondary text (#595959)
  const secondaryRatio = getContrastRatio('#595959', whiteBg)
  assert.ok(secondaryRatio >= 4.5, `Secondary text ratio ${secondaryRatio} must be >= 4.5:1`)

  // High-contrast primary link (#2b73c4)
  const linkRatio = getContrastRatio('#2b73c4', whiteBg)
  assert.ok(linkRatio >= 4.5, `Link ratio ${linkRatio} must be >= 4.5:1`)
})

test('2. Failing color contrast is correctly identified (Element Plus muted gray #909399 fails on white)', () => {
  const whiteBg = '#ffffff'
  const failingRatio = getContrastRatio('#909399', whiteBg)
  assert.ok(failingRatio < 4.5, `Default #909399 has ratio ${failingRatio} and fails WCAG AA on white`)
  assert.strictEqual(isWcagAaCompliant('#909399', whiteBg), false)
})

test('3. Dark mode text tokens satisfy WCAG AA contrast (>= 4.5:1)', () => {
  const darkBg = '#141414'

  // Dark mode primary text (#f0f2f5)
  const darkPrimaryRatio = getContrastRatio('#f0f2f5', darkBg)
  assert.ok(darkPrimaryRatio >= 4.5, `Dark primary ratio ${darkPrimaryRatio} must be >= 4.5:1`)

  // Dark mode regular text (#dcdfe6)
  const darkRegularRatio = getContrastRatio('#dcdfe6', darkBg)
  assert.ok(darkRegularRatio >= 4.5, `Dark regular ratio ${darkRegularRatio} must be >= 4.5:1`)

  // Dark mode secondary text (#a0a8b4)
  const darkSecondaryRatio = getContrastRatio('#a0a8b4', darkBg)
  assert.ok(darkSecondaryRatio >= 4.5, `Dark secondary ratio ${darkSecondaryRatio} must be >= 4.5:1`)
})

test('4. Status indicators do not rely on color alone (includes text and symbol)', () => {
  const testStatuses = [
    { status: 'APPROVED', expectedSymbol: '✓', expectedLabel: 'Approved' },
    { status: 'REJECTED', expectedSymbol: '✕', expectedLabel: 'Rejected' },
    { status: 'PENDING', expectedSymbol: '⏳', expectedLabel: 'Pending Approval' },
    { status: 'SUBMITTED', expectedSymbol: '⏳', expectedLabel: 'Pending Approval' },
    { status: 'RETURNED', expectedSymbol: '✓', expectedLabel: 'Returned' },
    { status: 'BORROWED', expectedSymbol: '📦', expectedLabel: 'Borrowed' },
    { status: 'GOOD', expectedSymbol: '✓', expectedLabel: 'Good Condition' },
    { status: 'NEEDS_REPAIR', expectedSymbol: '⚠️', expectedLabel: 'Needs Repair' },
    { status: 'BROKEN', expectedSymbol: '✕', expectedLabel: 'Broken' },
  ]

  for (const item of testStatuses) {
    const formatted = formatStatusLabel(item.status)
    assert.strictEqual(formatted.symbol, item.expectedSymbol, `Symbol for ${item.status}`)
    assert.strictEqual(formatted.label, item.expectedLabel, `Label for ${item.status}`)
    assert.ok(formatted.fullText.includes(item.expectedSymbol))
    assert.ok(formatted.fullText.includes(item.expectedLabel))
  }
})

test('5. Low stock alerts include non-color indicators (warning symbol and text)', () => {
  // Low stock scenario (total 3 <= threshold 10)
  const lowStock = formatStockDisplay(3, 10)
  assert.strictEqual(lowStock.isLowStock, true)
  assert.strictEqual(lowStock.symbol, '⚠️')
  assert.ok(lowStock.display.includes('(Low Stock)'), 'Display must contain explicit (Low Stock) text')
  assert.ok(lowStock.ariaLabel.includes('Warning: Low stock'))

  // Normal stock scenario (total 15 > threshold 10)
  const normalStock = formatStockDisplay(15, 10)
  assert.strictEqual(normalStock.isLowStock, false)
  assert.strictEqual(normalStock.symbol, '✓')
  assert.strictEqual(normalStock.display, '✓ 15')
  assert.ok(normalStock.ariaLabel.includes('Stock level is normal'))
})

test('6. In-App notifications resolve to correct application routes', () => {
  assert.strictEqual(resolveNotificationRoute('BORROW_APPROVED'), '/borrow')
  assert.strictEqual(resolveNotificationRoute('BORROW_OVERDUE'), '/borrow')
  assert.strictEqual(resolveNotificationRoute('MATERIAL_REQUEST_APPROVED'), '/procurement')
  assert.strictEqual(resolveNotificationRoute('PO_GENERATED'), '/procurement')
  assert.strictEqual(resolveNotificationRoute('ASSET_SCANNED'), '/assets')
  assert.strictEqual(resolveNotificationRoute('LOW_STOCK_ALERT'), '/inventory')
  assert.strictEqual(resolveNotificationRoute('UNKNOWN_EVENT'), '/')
})

test('7. Empty states clearly distinguish between empty data, filtered results, loading, and error', () => {
  // 1. Loading state
  const loadingState = getEmptyStateMessage({ loading: true, resourceName: 'inventory items' })
  assert.ok(loadingState.title.includes('Loading'))

  // 2. Error state
  const errorState = getEmptyStateMessage({ error: new Error('Network failure'), resourceName: 'inventory items' })
  assert.ok(errorState.title.includes('Unable to load'))

  // 3. Filtered zero results
  const filteredState = getEmptyStateMessage({ hasFilter: true, resourceName: 'inventory items' })
  assert.ok(filteredState.title.includes('match the current filter'))
  assert.ok(filteredState.description.includes('adjusting or clearing'))

  // 4. Naturally empty
  const emptyState = getEmptyStateMessage({ resourceName: 'borrow requests' })
  assert.ok(emptyState.title.includes('No borrow requests found'))
})

test('8. Global CSS includes mandatory accessibility mechanisms (focus-visible, skip-link, sr-only)', () => {
  const cssPath = path.join(__dirname, '../style.css')
  assert.ok(fs.existsSync(cssPath), 'style.css must exist')

  const cssContent = fs.readFileSync(cssPath, 'utf8')

  // Focus visible outline
  assert.ok(cssContent.includes(':focus-visible'), 'CSS must specify :focus-visible rules')
  assert.ok(cssContent.includes('outline:'), 'Focus indicator must have visible outline')
  assert.ok(cssContent.includes('outline-offset:'), 'Focus indicator must have offset')

  // Skip link
  assert.ok(cssContent.includes('.skip-link'), 'CSS must define .skip-link styles')
  assert.ok(cssContent.includes('position: absolute'), 'Skip link should be positioned offscreen by default')
  assert.ok(cssContent.includes('.skip-link:focus'), 'Skip link must become visible when focused')

  // Screen reader utility (.sr-only)
  assert.ok(cssContent.includes('.sr-only'), 'CSS must define .sr-only utility class')
  assert.ok(cssContent.includes('clip: rect(0, 0, 0, 0)'), '.sr-only must properly clip visually')

  // Accessible table container
  assert.ok(cssContent.includes('.accessible-table-container'), 'CSS must define .accessible-table-container')
  assert.ok(cssContent.includes('overflow-x: auto'), 'Table container must allow horizontal scrolling')
})
