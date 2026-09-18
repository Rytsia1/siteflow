/**
 * SiteFlow Accessibility & UX Compliance Utilities (WCAG 2.1 AA)
 */

/**
 * Parses a hex color (3 or 6 characters) to RGB object.
 * @param {string} hex
 * @returns {{r: number, g: number, b: number}}
 */
export function hexToRgb(hex) {
  let cleaned = hex.replace('#', '').trim()
  if (cleaned.length === 3) {
    cleaned = cleaned.split('').map((c) => c + c).join('')
  }
  const num = parseInt(cleaned, 16)
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255,
  }
}

/**
 * Calculates relative luminance according to WCAG 2.1 specs.
 * @param {string} hex
 * @returns {number}
 */
export function getRelativeLuminance(hex) {
  const { r, g, b } = hexToRgb(hex)
  const [rs, gs, bs] = [r, g, b].map((c) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
}

/**
 * Calculates contrast ratio between two hex colors (1:1 to 21:1).
 * @param {string} color1
 * @param {string} color2
 * @returns {number}
 */
export function getContrastRatio(color1, color2) {
  const lum1 = getRelativeLuminance(color1)
  const lum2 = getRelativeLuminance(color2)
  const brightest = Math.max(lum1, lum2)
  const darkest = Math.min(lum1, lum2)
  return Number(((brightest + 0.05) / (darkest + 0.05)).toFixed(2))
}

/**
 * Checks if a contrast ratio satisfies WCAG AA criteria.
 * @param {string} foreground
 * @param {string} background
 * @param {boolean} isLargeText - True if text is >= 18pt or >= 14pt bold
 * @returns {boolean}
 */
export function isWcagAaCompliant(foreground, background, isLargeText = false) {
  const ratio = getContrastRatio(foreground, background)
  const threshold = isLargeText ? 3.0 : 4.5
  return ratio >= threshold
}

/**
 * Formats a business status with non-color indicators (symbol and descriptive text).
 * Prevents reliance on color alone.
 * @param {string} status
 * @returns {{ symbol: string, label: string, fullText: string }}
 */
export function formatStatusLabel(status) {
  const normalized = (status || '').toUpperCase()
  switch (normalized) {
    case 'APPROVED':
      return { symbol: '✓', label: 'Approved', fullText: '✓ Approved' }
    case 'REJECTED':
      return { symbol: '✕', label: 'Rejected', fullText: '✕ Rejected' }
    case 'PENDING':
    case 'SUBMITTED':
      return { symbol: '⏳', label: 'Pending Approval', fullText: '⏳ Pending Approval' }
    case 'RETURNED':
      return { symbol: '✓', label: 'Returned', fullText: '✓ Returned' }
    case 'BORROWED':
      return { symbol: '📦', label: 'Borrowed', fullText: '📦 Borrowed' }
    case 'GOOD':
      return { symbol: '✓', label: 'Good Condition', fullText: '✓ Good Condition' }
    case 'NEEDS_REPAIR':
      return { symbol: '⚠️', label: 'Needs Repair', fullText: '⚠️ Needs Repair' }
    case 'BROKEN':
      return { symbol: '✕', label: 'Broken', fullText: '✕ Broken' }
    case 'LOW_STOCK':
      return { symbol: '⚠️', label: 'Low Stock', fullText: '⚠️ Low Stock' }
    default:
      return { symbol: '•', label: status || 'Unknown', fullText: `• ${status || 'Unknown'}` }
  }
}

/**
 * Formats inventory stock counts with non-color indicator for low stock.
 * @param {number} totalQty
 * @param {number} minStockThreshold
 * @returns {{ isLowStock: boolean, symbol: string, display: string, ariaLabel: string }}
 */
export function formatStockDisplay(totalQty, minStockThreshold) {
  const isLowStock = totalQty <= minStockThreshold
  if (isLowStock) {
    return {
      isLowStock: true,
      symbol: '⚠️',
      display: `⚠️ ${totalQty} (Low Stock)`,
      ariaLabel: `${totalQty} units available. Warning: Low stock below minimum threshold of ${minStockThreshold}`,
    }
  }
  return {
    isLowStock: false,
    symbol: '✓',
    display: `✓ ${totalQty}`,
    ariaLabel: `${totalQty} units available. Stock level is normal`,
  }
}

/**
 * Maps notification types to their target application routes.
 * Ensures notifications are actionable without bypassing authorization.
 * @param {string} notificationType
 * @returns {string}
 */
export function resolveNotificationRoute(notificationType) {
  const type = (notificationType || '').toUpperCase()
  if (type.includes('BORROW')) return '/borrow'
  if (type.includes('MATERIAL') || type.includes('PO') || type.includes('PROCUREMENT')) return '/procurement'
  if (type.includes('ASSET') || type.includes('SCAN')) return '/assets'
  if (type.includes('STOCK') || type.includes('INVENTORY')) return '/inventory'
  return '/'
}

/**
 * Resolves a full deep-linking destination (path + query parameters) for a notification.
 * Enables direct contextual navigation (Notification -> Relevant record -> Action).
 *
 * @param {object} notification
 * @param {string} [notification.type]
 * @param {string} [notification.referenceType] - 'MATERIAL_REQUEST' | 'BORROW_REQUEST' | 'ITEM' | 'ASSET' | 'PURCHASE_ORDER'
 * @param {string|number} [notification.referenceId] - e.g. 'MR-2026-014', 'MT-1042', 'TL-00482'
 * @param {string} [userRole] - 'ADMIN' | 'WAREHOUSE_STAFF' | 'FIELD_STAFF' | 'PROCUREMENT'
 * @returns {{ path: string, query?: Record<string, string> }}
 */
export function resolveNotificationTarget(notification, userRole = '') {
  if (!notification) return { path: '/' }

  const type = String(notification.type || '').toUpperCase()
  const refType = String(notification.referenceType || '').toUpperCase()
  const refId = notification.referenceId != null ? String(notification.referenceId) : ''
  const role = String(userRole || '').toUpperCase()

  // 1. Material Requests & Requisitions
  if (refType === 'MATERIAL_REQUEST' || type.includes('MATERIAL')) {
    if (type.includes('APPROVAL') && role === 'ADMIN') {
      return {
        path: '/approvals',
        query: { tab: 'material', ...(refId ? { highlight: refId } : {}) },
      }
    }
    if (type.includes('READY_PO') || type.includes('APPROVED')) {
      return {
        path: '/procurement',
        query: { tab: 'approved', ...(refId ? { highlight: refId } : {}) },
      }
    }
    return {
      path: '/procurement',
      query: { tab: 'pending', ...(refId ? { highlight: refId } : {}) },
    }
  }

  // 2. Borrow Requests & Equipment Custody
  if (refType === 'BORROW_REQUEST' || type.includes('BORROW')) {
    if ((type.includes('APPROVAL') || type.includes('ACTION')) && role === 'ADMIN') {
      return {
        path: '/approvals',
        query: { tab: 'borrow', ...(refId ? { highlight: refId } : {}) },
      }
    }
    if (type.includes('OVERDUE') || type.includes('RETURN') || type.includes('ACTION')) {
      return {
        path: '/borrow',
        query: { tab: 'return', ...(refId ? { requestId: refId } : {}) },
      }
    }
    return {
      path: '/borrow',
      query: { tab: 'new', ...(refId ? { highlight: refId } : {}) },
    }
  }

  // 3. Inventory Items & Stock Alerts
  if (refType === 'ITEM' || type.includes('STOCK') || type.includes('INVENTORY')) {
    return {
      path: '/inventory',
      query: { ...(refId ? { itemCode: refId, highlight: refId } : {}) },
    }
  }

  // 4. Physical Tool Instances & Scanner Operations
  if (refType === 'ASSET' || type.includes('ASSET') || type.includes('SCAN')) {
    if (role === 'ADMIN' || role === 'WAREHOUSE_STAFF' || !role) {
      return {
        path: '/assets',
        query: {
          mode: type.includes('RETURN') || type.includes('OVERDUE') ? 'return' : 'checkout',
          ...(refId ? { code: refId, highlight: refId } : {}),
        },
      }
    }
    // Non-warehouse roles (FIELD_STAFF) process returns/checkouts via Borrow view
    return {
      path: '/borrow',
      query: { tab: 'return', ...(refId ? { requestId: refId } : {}) },
    }
  }

  // 5. Purchase Orders
  if (refType === 'PURCHASE_ORDER' || type.includes('PO') || type.includes('PURCHASE')) {
    return {
      path: '/procurement',
      query: { tab: 'pos', ...(refId ? { poNumber: refId, highlight: refId } : {}) },
    }
  }

  // Fallback to base route resolver
  const basePath = resolveNotificationRoute(notification.type)
  return { path: basePath, query: refId ? { highlight: refId } : {} }
}

/**
 * Generates an accurate, accessible empty state message distinguishing between
 * empty data, active filters, and loading/error states.
 * @param {object} params
 * @param {boolean} params.loading
 * @param {any} params.error
 * @param {boolean} params.hasFilter
 * @param {string} params.resourceName
 * @returns {{ title: string, description: string }}
 */
export function getEmptyStateMessage({ loading = false, error = null, hasFilter = false, resourceName = 'items' }) {
  if (loading) {
    return {
      title: `Loading ${resourceName}...`,
      description: 'Fetching current data from server.',
    }
  }
  if (error) {
    return {
      title: `Unable to load ${resourceName}`,
      description: 'A network or server error occurred. Please refresh or try again.',
    }
  }
  if (hasFilter) {
    return {
      title: `No ${resourceName} match the current filter`,
      description: 'Try adjusting or clearing your search criteria or category filters.',
    }
  }
  return {
    title: `No ${resourceName} found`,
    description: `There are currently no ${resourceName} recorded in the system.`,
  }
}
