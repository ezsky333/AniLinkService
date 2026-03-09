import axios from 'axios'
import { showSessionExpiredDialog } from './ui-feedback'

let initialized = false

export function setupHttpInterceptors() {
  if (initialized) {
    return
  }
  initialized = true

  axios.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('token')
      if (token) {
        config.headers.satoken = token
      }
      return config
    },
    (error) => Promise.reject(error)
  )

  axios.interceptors.response.use(
    (response) => response,
    (error) => {
      const status = error?.response?.status
      const hasToken = !!localStorage.getItem('token')

      if (status === 401 && hasToken) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        localStorage.removeItem('rememberMe')
        showSessionExpiredDialog('登录状态已过期，请重新登录。确认后将返回首页。')
      }

      return Promise.reject(error)
    }
  )
}
