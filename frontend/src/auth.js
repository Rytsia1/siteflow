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
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ username: auth.username, token: auth.token, role: auth.role }),
    )
  }
}

export function loginSuccess(token, username, role) {
  auth.token = token
  auth.username = username
  auth.role = role
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
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

export function isAuthenticated() {
  return Boolean(auth.token)
}
