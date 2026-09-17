import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, logout } from '../auth'
import router from '../router'

const http = axios.create({
  baseURL: '/api',
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
    const status = error.response?.status

    // 401 (missing/invalid credentials) is rejected by the Spring Security filter chain
    // before @RestControllerAdvice runs, but ApiAuthenticationEntryPoint gives it the
    // same ApiResponse envelope as every other error, so .message is safe to read here too.
    if (status === 401) {
      logout()
      ElMessage.error(error.response.data?.message || 'Invalid credentials or session expired.')
      router.push('/login')
    } else if (status === 403) {
      ElMessage.error(error.response.data?.message || 'Access denied.')
    } else if (status >= 400 && status < 500) {
      ElMessage.error(error.response.data?.message || 'Request failed.')
    } else if (status >= 500) {
      ElMessage.error(error.response.data?.message || 'An unexpected server error occurred.')
    } else {
      ElMessage.error('Cannot reach server.')
    }

    return Promise.reject(error)
  },
)

export default http
