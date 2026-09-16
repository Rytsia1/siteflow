import { reactive } from 'vue'

const STORAGE_KEY = 'siteflow.auth'

function load() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : { username: null, token: null, role: null }
  } catch {
    return { username: null, token: null, role: null }
  }
}

export const auth = reactive(load())

function persist() {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ username: auth.username, token: auth.token, role: auth.role }),
  )
}

export function login(username, password) {
  auth.username = username
  auth.token = btoa(`${username}:${password}`)
  auth.role = null
  persist()
}

// Basic auth carries no server-side role claim, so the role is fetched
// separately (GET /auth/me) right after login and cached here for the
// router guards and role-gated UI to read synchronously.
export function setRole(role) {
  auth.role = role
  persist()
}

export function logout() {
  auth.username = null
  auth.token = null
  auth.role = null
  sessionStorage.removeItem(STORAGE_KEY)
}

export function isAuthenticated() {
  return Boolean(auth.token)
}
