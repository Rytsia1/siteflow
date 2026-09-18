import { reactive } from 'vue'

const STORAGE_KEY = 'siteflow.auth'

function load() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : { username: null, role: null, authenticated: false, token: null }
  } catch {
    return { username: null, role: null, authenticated: false, token: null }
  }
}

export const auth = reactive(load())

function persist() {
  if (typeof sessionStorage !== 'undefined') {
    // Only persist non-sensitive user profile attributes; NEVER store JWT tokens in browser storage
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ username: auth.username, role: auth.role, authenticated: auth.authenticated }),
    )
  }
}

export function loginSuccess(param1, param2, param3) {
  if (param3 !== undefined) {
    // Invocation pattern: loginSuccess(token, username, role) -> discard token
    auth.username = param2
    auth.role = param3
  } else {
    // Invocation pattern: loginSuccess(username, role)
    auth.username = param1
    auth.role = param2
  }
  auth.authenticated = true
  auth.token = null
  persist()
}

export function setRole(role) {
  auth.role = role
  persist()
}

export function logout() {
  auth.username = null
  auth.token = null
  auth.role = null
  auth.authenticated = false
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

export function isAuthenticated() {
  return Boolean(auth.authenticated && auth.username)
}
