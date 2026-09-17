/**
 * Centralized API error handling utilities for SiteFlow frontend.
 * Resolves safe, user-friendly error messages consistent with backend ApiResponse<ErrorDetails>.
 */

export function getErrorMessage(error) {
  if (!error) {
    return 'An unknown error occurred.'
  }

  // 1. Timeout error (Axios code ECONNABORTED or timeout message)
  if (error.code === 'ECONNABORTED' || (error.message && error.message.toLowerCase().includes('timeout'))) {
    return 'Request timed out. Please check your connection and try again.'
  }

  // 2. Network error (no response received from server)
  if (!error.response) {
    return 'Cannot reach server. Please check your network connection.'
  }

  const { status, data, headers } = error.response
  const serverMessage = data?.message

  // 3. Field validation errors (status 400 with structured fieldErrors map)
  if (data?.data?.fieldErrors && typeof data.data.fieldErrors === 'object' && Object.keys(data.data.fieldErrors).length > 0) {
    const details = Object.entries(data.data.fieldErrors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join('; ')
    return `${serverMessage || 'Validation failed'}: ${details}`
  }

  // 4. Status-specific safe messages
  switch (status) {
    case 400:
      return serverMessage || 'Invalid request. Please check your input.'
    case 401:
      return serverMessage || 'Authentication required. Please log in.'
    case 403:
      return serverMessage || 'Access denied. You do not have permission to perform this action.'
    case 404:
      return serverMessage || 'Requested resource was not found.'
    case 409:
      return serverMessage || 'Conflict: The operation cannot be completed in the current state.'
    case 429: {
      const retryAfter = headers?.['retry-after'] || headers?.['Retry-After']
      if (retryAfter) {
        return serverMessage || `Too many requests. Please slow down and try again in ${retryAfter} seconds.`
      }
      return serverMessage || 'Too many requests. Please slow down and wait a moment before trying again.'
    }
    case 500:
      return 'An unexpected server error occurred. Please try again later.'
    default:
      if (status >= 500) {
        return 'An unexpected server error occurred. Please try again later.'
      }
      return serverMessage || 'Request failed. Please try again.'
  }
}

/**
 * Checks if an HTTP method represents a state-mutating operation.
 * Mutations must never be automatically retried on error or timeout.
 */
export function isMutationMethod(method) {
  if (!method) return false
  const m = String(method).toUpperCase()
  return m === 'POST' || m === 'PUT' || m === 'DELETE' || m === 'PATCH'
}

/**
 * Dispatches centralized error handling (logout, notification, navigation).
 */
export function dispatchApiError(error, { onLogout, onNavigate, showMessage, currentPath } = {}) {
  const message = getErrorMessage(error)
  const status = error.response?.status

  if (status === 401) {
    if (onLogout) onLogout()
    if (showMessage) showMessage(message)
    if (onNavigate && currentPath !== '/login') {
      onNavigate('/login')
    }
  } else {
    if (showMessage) showMessage(message)
  }

  return message
}
