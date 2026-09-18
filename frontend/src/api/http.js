import axios from 'axios'
import { ElMessage } from 'element-plus'
import { logout } from '../auth.js'
import { dispatchApiError } from './error-handler.js'

// Timeout defaults to 15 seconds unless overridden by VITE_API_TIMEOUT
const timeout = Number(import.meta.env?.VITE_API_TIMEOUT) || 15000

let routerInstance = null

export function setRouter(router) {
  routerInstance = router
}

export function getCookie(name) {
  if (typeof document === 'undefined' || !document.cookie) return null
  const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[2]) : null
}

const http = axios.create({
  baseURL: '/api',
  timeout,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

http.interceptors.request.use((config) => {
  // Read CSRF token from document.cookie and attach X-XSRF-TOKEN header for state-changing requests
  const xsrfToken = getCookie('XSRF-TOKEN')
  if (xsrfToken && !config.headers['X-XSRF-TOKEN']) {
    config.headers['X-XSRF-TOKEN'] = xsrfToken
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data.data,
  (error) => {
    const currentPath = routerInstance?.currentRoute?.value?.path || ''

    dispatchApiError(error, {
      onLogout: logout,
      onNavigate: (path) => {
        if (routerInstance?.push) {
          routerInstance.push(path).catch(() => {})
        } else if (typeof window !== 'undefined' && window.location?.pathname !== path) {
          window.location.href = path
        }
      },
      showMessage: (msg) => {
        if (typeof ElMessage !== 'undefined' && ElMessage.error) {
          ElMessage.error(msg)
        }
      },
      currentPath,
    })

    return Promise.reject(error)
  },
)

export default http
export { timeout }
