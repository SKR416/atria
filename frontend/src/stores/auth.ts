import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CurrentUserProfileResponse, LoginResponse } from '@/types/iam'
import { getAppLocale, setAppLocale } from '@/i18n'
import type { SupportedLocale } from '@/types/api'

const ADMIN_PERMISSIONS = [
  'iam:user:read',
  'iam:user:write',
  'iam:dept:read',
  'iam:dept:write',
  'iam:role:read',
  'iam:role:write',
  'iam:audit:read',
  'att:calendar:read',
  'att:calendar:write',
  'att:monthly-close:manage',
  'att:rule:read',
  'att:rule:write'
]

export const useAuthStore = defineStore('auth', () => {
  // Access Token is kept strictly in memory (ADR-0004), never persisted in localStorage
  const accessToken = ref<string | null>(null)
  const user = ref<CurrentUserProfileResponse | null>(null)
  const mustChangePassword = ref<boolean>(false)
  const initialized = ref<boolean>(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

  const roles = computed<string[]>(() => user.value?.roles ?? [])
  const permissions = computed<string[]>(() => user.value?.permissions ?? [])

  const canAccessAdmin = computed(() => {
    if (!user.value) return false
    if (roles.value.includes('SUPER_ADMIN') || roles.value.includes('HR_MANAGER') || roles.value.includes('DEPT_MANAGER')) {
      return true
    }
    return ADMIN_PERMISSIONS.some((p) => permissions.value.includes(p))
  })

  function hasPermission(permCode: string): boolean {
    return permissions.value.includes(permCode)
  }

  function applyLoginResponse(payload: LoginResponse) {
    accessToken.value = payload.accessToken
    user.value = payload.user
    mustChangePassword.value = Boolean(payload.mustChangePassword || payload.user?.mustChangePassword)
    initialized.value = true

    // Synchronize user's preferred locale if valid and user hasn't explicitly overridden
    const userLocale = payload.user?.locale as SupportedLocale | undefined
    if (userLocale && ['zh-TW', 'en-US', 'ja-JP'].includes(userLocale) && !localStorage.getItem('atria_locale')) {
      setAppLocale(userLocale)
    }
  }

  function updateProfile(profile: CurrentUserProfileResponse) {
    user.value = profile
    mustChangePassword.value = Boolean(profile.mustChangePassword)
  }

  function clearSession() {
    accessToken.value = null
    user.value = null
    mustChangePassword.value = false
    initialized.value = true
  }

  const activeLocale = computed(() => getAppLocale())

  return {
    accessToken,
    user,
    mustChangePassword,
    initialized,
    isAuthenticated,
    roles,
    permissions,
    canAccessAdmin,
    activeLocale,
    hasPermission,
    applyLoginResponse,
    updateProfile,
    clearSession
  }
})
