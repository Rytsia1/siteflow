import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, logout } from '../auth.js'
import { dispatchApiError } from './error-handler.js'

// Timeout defaults to 15 seconds unless overridden by VITE_API_TIMEOUT
const timeout = Number(import.meta.env?.VITE_API_TIMEOUT) || 15000

let routerInstance = null

export function setRouter(router) {
  routerInstance = router
}

const http = axios.create({
  baseURL: '/api',
  timeout,
})

http.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
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
