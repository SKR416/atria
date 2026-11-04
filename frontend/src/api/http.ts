import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import type { LoginResponse } from '@/types/iam'
import { getAppLocale } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const http = axios.create({
  baseURL: '',
  timeout: 15000,
  withCredentials: true
})

let refreshPromise: Promise<LoginResponse | null> | null = null

export async function trySilentRefresh(): Promise<LoginResponse | null> {
  if (refreshPromise) {
    return refreshPromise
  }
  refreshPromise = axios
    .post<ApiResponse<LoginResponse>>(
      '/api/v1/auth/refresh',
      {},
      {
        withCredentials: true,
        headers: {
          'Accept-Language': getAppLocale()
        }
      }
    )
    .then((res) => {
      if (res.data && res.data.code === 200 && res.data.data) {
        const authStore = useAuthStore()
        authStore.applyLoginResponse(res.data.data)
        return res.data.data
      }
      return null
    })
    .catch(() => {
      const authStore = useAuthStore()
      authStore.clearSession()
      return null
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  config.headers.set('Accept-Language', getAppLocale())
  if (authStore.accessToken && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${authStore.accessToken}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    if (body && typeof body.code === 'number' && body.code !== 200) {
      if (body.code === 1003) {
        const authStore = useAuthStore()
        authStore.mustChangePassword = true
        if (window.location.pathname !== '/change-password') {
          window.location.assign('/change-password')
        }
      }
      return Promise.reject(new Error(body.message || 'API Error'))
    }
    return response
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined
    const status = error.response?.status
    const errorCode = error.response?.data?.code
    const requestUrl = originalRequest?.url || ''

    // Mandatory Password Change Gate (ErrorCode 1003)
    if (errorCode === 1003) {
      const authStore = useAuthStore()
      authStore.mustChangePassword = true
      if (window.location.pathname !== '/change-password') {
        window.location.assign('/change-password')
      }
      return Promise.reject(new Error(error.response?.data?.message || 'Password change required'))
    }

    // Automatic silent refresh on 401 for non-auth endpoints
    const isAuthEndpoint =
      requestUrl.includes('/api/v1/auth/login') ||
      requestUrl.includes('/api/v1/auth/refresh') ||
      requestUrl.includes('/api/v1/auth/logout')

    if (status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true
      const refreshed = await trySilentRefresh()
      if (refreshed && refreshed.accessToken) {
        originalRequest.headers.set('Authorization', `Bearer ${refreshed.accessToken}`)
        return http(originalRequest)
      }
      if (window.location.pathname !== '/login' && !window.location.pathname.startsWith('/public')) {
        window.location.assign('/login')
      }
    }

    const message = error.response?.data?.message || error.message || 'Network Error'
    return Promise.reject(new Error(message))
  }
)
