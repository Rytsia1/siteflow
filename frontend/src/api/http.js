import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, logout } from '../auth'
import router from '../router'

const http = axios.create({
  baseURL: '/api',
})

http.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Basic ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data.data,
  (error) => {
    const status = error.response?.status

    // A 401 comes from the Spring Security filter chain, before @RestControllerAdvice
    // runs, so the body is not an ApiResponse envelope — never read .message here.
    if (status === 401) {
      logout()
      ElMessage.error('Invalid credentials or session expired.')
      router.push('/login')
    } else if (status === 403) {
      ElMessage.error(error.response.data?.message || 'Access denied.')
    } else if (status === 400 || status === 409) {
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
