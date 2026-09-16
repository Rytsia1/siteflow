import { reactive } from 'vue'

const STORAGE_KEY = 'siteflow.auth'

function load() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : { username: null, token: null }
  } catch {
    return { username: null, token: null }
  }
}

export const auth = reactive(load())

export function login(username, password) {
  auth.username = username
  auth.token = btoa(`${username}:${password}`)
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ username: auth.username, token: auth.token }))
}

export function logout() {
  auth.username = null
  auth.token = null
  sessionStorage.removeItem(STORAGE_KEY)
}

export function isAuthenticated() {
  return Boolean(auth.token)
}
