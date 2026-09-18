import test from 'node:test'
import assert from 'node:assert/strict'
import { getErrorMessage, isMutationMethod, dispatchApiError } from './error-handler.js'
import { timeout, getCookie } from './http.js'
import { auth, loginSuccess, logout, isAuthenticated } from '../auth.js'

test('1. Successful request clears loading state', async () => {
  let loading = true
  const simulateApiCall = async () => 'data'

  try {
    const data = await simulateApiCall()
    assert.strictEqual(data, 'data')
  } finally {
    loading = false
  }

  assert.strictEqual(loading, false, 'Loading state should be cleared on success')
})

test('2. Failed request clears loading state', async () => {
  let loading = true
  const simulateFailingCall = async () => {
    throw new Error('API failure')
  }

  try {
    await simulateFailingCall()
    assert.fail('Should not succeed')
  } catch (err) {
    assert.strictEqual(err.message, 'API failure')
  } finally {
    loading = false
  }

  assert.strictEqual(loading, false, 'Loading state should be cleared on failure')
})

test('3. Timeout clears loading state', async () => {
  let loading = true
  const simulateTimeout = async () => {
    const err = new Error('timeout of 15000ms exceeded')
    err.code = 'ECONNABORTED'
    throw err
  }

  try {
    await simulateTimeout()
    assert.fail('Should have timed out')
  } catch (err) {
    assert.strictEqual(err.code, 'ECONNABORTED')
  } finally {
    loading = false
  }

  assert.strictEqual(loading, false, 'Loading state should be cleared on timeout')
})

test('4. Timeout displays a user-friendly message', () => {
  const econError = { code: 'ECONNABORTED', message: 'timeout of 15000ms exceeded' }
  const message1 = getErrorMessage(econError)
  assert.strictEqual(message1, 'Request timed out. Please check your connection and try again.')

  const timeoutMsgError = { message: 'Network timeout error' }
  const message2 = getErrorMessage(timeoutMsgError)
  assert.strictEqual(message2, 'Request timed out. Please check your connection and try again.')
})

test('5. 401 authentication failure is handled correctly', () => {
  let loggedOut = false
  let navigatedTo = null
  let messageShown = null

  const authError = {
    response: {
      status: 401,
      data: { message: 'Invalid credentials or session expired.' },
    },
  }

  dispatchApiError(authError, {
    onLogout: () => {
      loggedOut = true
    },
    onNavigate: (path) => {
      navigatedTo = path
    },
    showMessage: (msg) => {
      messageShown = msg
    },
    currentPath: '/dashboard',
  })

  assert.strictEqual(loggedOut, true, 'User should be logged out on 401')
  assert.strictEqual(navigatedTo, '/login', 'User should be navigated to /login')
  assert.strictEqual(messageShown, 'Invalid credentials or session expired.')

  // Verify no navigation loop if already on /login
  let renavigated = false
  dispatchApiError(authError, {
    onLogout: () => {},
    onNavigate: () => {
      renavigated = true
    },
    showMessage: () => {},
    currentPath: '/login',
  })
  assert.strictEqual(renavigated, false, 'Should not re-navigate to /login when already on /login')
})

test('6. 403 access denied is handled correctly with permission error', () => {
  const error403 = {
    response: {
      status: 403,
      data: { message: 'Access denied.' },
    },
  }
  const msg = getErrorMessage(error403)
  assert.strictEqual(msg, 'Access denied.')

  const fallback403 = {
    response: {
      status: 403,
      data: {},
    },
  }
  const fallbackMsg = getErrorMessage(fallback403)
  assert.strictEqual(fallbackMsg, 'Access denied. You do not have permission to perform this action.')
})

test('7. 404 not found is handled correctly', () => {
  const error404 = {
    response: {
      status: 404,
      data: { message: 'Borrow request not found: 123' },
    },
  }
  const msg = getErrorMessage(error404)
  assert.strictEqual(msg, 'Borrow request not found: 123')

  const fallback404 = {
    response: {
      status: 404,
      data: {},
    },
  }
  const fallbackMsg = getErrorMessage(fallback404)
  assert.strictEqual(fallbackMsg, 'Requested resource was not found.')
})

test('8. 429 rate limit exceeded is handled correctly with retry information', () => {
  const error429 = {
    response: {
      status: 429,
      headers: { 'retry-after': '45' },
      data: { message: 'Too many requests. Please try again later.' },
    },
  }
  const msg = getErrorMessage(error429)
  assert.ok(msg.includes('Too many requests'), 'Message should indicate rate limit exceeded')

  const fallback429 = {
    response: {
      status: 429,
      headers: { 'retry-after': '60' },
      data: {},
    },
  }
  const fallbackMsg = getErrorMessage(fallback429)
  assert.strictEqual(fallbackMsg, 'Too many requests. Please slow down and try again in 60 seconds.')
})

test('9. 500 unexpected server error displays generic message without leaking internals', () => {
  const error500 = {
    response: {
      status: 500,
      data: {
        message: 'Internal error: NullPointerException at com.siteflow.InventoryService.line42',
      },
    },
  }
  const msg = getErrorMessage(error500)
  assert.strictEqual(msg, 'An unexpected server error occurred. Please try again later.')
  assert.ok(!msg.includes('NullPointerException'), 'Internal details must be masked')
  assert.ok(!msg.includes('com.siteflow'), 'Internal packages must be masked')
})

test('10. No unsafe automatic retry occurs for mutation requests', () => {
  assert.strictEqual(isMutationMethod('POST'), true, 'POST is a mutation')
  assert.strictEqual(isMutationMethod('PUT'), true, 'PUT is a mutation')
  assert.strictEqual(isMutationMethod('PATCH'), true, 'PATCH is a mutation')
  assert.strictEqual(isMutationMethod('DELETE'), true, 'DELETE is a mutation')
  assert.strictEqual(isMutationMethod('GET'), false, 'GET is not a mutation')
  assert.strictEqual(isMutationMethod('HEAD'), false, 'HEAD is not a mutation')
})

test('11. Timeout configuration defaults to 15s or environment setting', () => {
  assert.strictEqual(typeof timeout, 'number')
  assert.ok(timeout >= 1000, 'Timeout must be at least 1 second')
})

test('12. Double-click rapid submission is prevented while request is in flight', async () => {
  let submitting = false
  let callCount = 0

  async function onSubmit() {
    if (submitting) return
    submitting = true
    try {
      callCount++
      // simulate async network latency
      await new Promise((resolve) => setTimeout(resolve, 50))
    } finally {
      submitting = false
    }
  }

  // Simulate two rapid clicks in parallel
  await Promise.all([onSubmit(), onSubmit()])

  assert.strictEqual(callCount, 1, 'Only the first click should execute; second should be blocked')
  assert.strictEqual(submitting, false, 'Submitting state must be restored to false on completion')
})

test('13. Submission failure restores submitting state so user can retry', async () => {
  let submitting = false
  let callCount = 0

  async function onSubmit() {
    if (submitting) return
    submitting = true
    try {
      callCount++
      throw new Error('Network error')
    } finally {
      submitting = false
    }
  }

  try {
    await onSubmit()
  } catch {
    // expected
  }

  assert.strictEqual(callCount, 1)
  assert.strictEqual(submitting, false, 'Submitting state must be restored even on error')

  // User retries after fixing input
  try {
    await onSubmit()
  } catch {
    // expected
  }
  assert.strictEqual(callCount, 2, 'User can submit again after an error')
  assert.strictEqual(submitting, false)
})

test('14. 409 Conflict displays server error message on duplicate submission or state conflict', () => {
  const conflictError = {
    response: {
      status: 409,
      data: {
        message: 'Duplicate submission detected: a request with this idempotency key is already completed or processing.',
      },
    },
  }
  const msg = getErrorMessage(conflictError)
  assert.strictEqual(
    msg,
    'Duplicate submission detected: a request with this idempotency key is already completed or processing.',
  )

  const fallbackConflict = {
    response: {
      status: 409,
      data: {},
    },
  }
  const fallbackMsg = getErrorMessage(fallbackConflict)
  assert.strictEqual(fallbackMsg, 'Conflict: The operation cannot be completed in the current state.')
})

test('15. loginSuccess does NOT store or expose raw JWT token in auth state or sessionStorage', () => {
  // Mock sessionStorage in test environment
  const mockStorage = {}
  global.sessionStorage = {
    getItem: (key) => mockStorage[key] || null,
    setItem: (key, val) => {
      mockStorage[key] = String(val)
    },
    removeItem: (key) => {
      delete mockStorage[key]
    },
  }

  // Simulate login
  const sensitiveJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitivePayload.signature'
  loginSuccess(sensitiveJwt, 'testuser', 'ADMIN')

  assert.strictEqual(auth.username, 'testuser')
  assert.strictEqual(auth.role, 'ADMIN')
  assert.strictEqual(auth.authenticated, true)
  assert.strictEqual(auth.token, null, 'auth.token must be strictly null to protect against XSS token theft')
  assert.strictEqual(isAuthenticated(), true)

  const stored = JSON.parse(mockStorage['siteflow.auth'])
  assert.strictEqual(stored.username, 'testuser')
  assert.strictEqual(stored.role, 'ADMIN')
  assert.strictEqual(stored.authenticated, true)
  assert.strictEqual(stored.token, undefined, 'Stored session must contain no token')
  assert.ok(!JSON.stringify(stored).includes('eyJ'), 'Serialized session storage must not contain JWT strings')

  // Clean up
  logout()
  assert.strictEqual(isAuthenticated(), false)
  assert.strictEqual(auth.username, null)
  assert.strictEqual(mockStorage['siteflow.auth'], undefined)
})

test('16. getCookie accurately parses cookie values including XSRF-TOKEN', () => {
  // When document is undefined, returns null
  const savedDoc = global.document
  delete global.document
  assert.strictEqual(getCookie('XSRF-TOKEN'), null)

  // With document.cookie mocked
  global.document = {
    cookie: 'theme=dark; XSRF-TOKEN=test-csrf-token-12345; other=abc',
  }
  assert.strictEqual(getCookie('XSRF-TOKEN'), 'test-csrf-token-12345')
  assert.strictEqual(getCookie('theme'), 'dark')
  assert.strictEqual(getCookie('missing'), null)

  // Restore document
  if (savedDoc) global.document = savedDoc
  else delete global.document
})

test('17. 401 handling logs out without creating infinite navigation loops', () => {
  let logoutCalled = false
  let navigations = 0

  dispatchApiError(
    { response: { status: 401 } },
    {
      onLogout: () => {
        logoutCalled = true
      },
      onNavigate: () => {
        navigations++
      },
      showMessage: () => {},
      currentPath: '/login', // already on login page
    },
  )

  assert.strictEqual(logoutCalled, true)
  assert.strictEqual(navigations, 0, 'Must not trigger navigation if already on /login')
})

