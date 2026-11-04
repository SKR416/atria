<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, RouterView, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/iam'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import Button from 'primevue/button'
import {
  User,
  LogOut,
  KeyRound,
  LayoutDashboard,
  ExternalLink,
  CalendarDays
} from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const loggingOut = ref(false)

const userDisplayName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || ''
})

const dataScopeLabel = computed(() => {
  const scope = authStore.user?.dataScope
  if (!scope) return ''
  return t(`iam.dataScopeTypes.${scope}`)
})

async function handleLogout() {
  loggingOut.value = true
  try {
    await authApi.logout()
  } catch {
    // Ignore error during logout
  } finally {
    authStore.clearSession()
    loggingOut.value = false
    router.push('/login')
  }
}

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="portal-layout">
    <header class="portal-header">
      <div class="header-container">
        <!-- Brand & Hall Info -->
        <div class="header-left">
          <RouterLink to="/portal" class="brand-link">
            <div class="brand-logo">
              <User :size="18" class="brand-icon" />
            </div>
            <div class="brand-text">
              <span class="brand-name">{{ t('app.name') }}</span>
              <span class="hall-badge">{{ t('halls.portal') }}</span>
            </div>
          </RouterLink>

          <!-- Navigation links -->
          <nav class="portal-nav">
            <RouterLink to="/portal" class="nav-item" active-class="nav-item-active">
              <CalendarDays :size="15" />
              <span>{{ t('nav.portalHome') }}</span>
            </RouterLink>
            <RouterLink to="/public" class="nav-item">
              <ExternalLink :size="14" />
              <span>{{ t('halls.switchToPublic') }}</span>
            </RouterLink>
          </nav>
        </div>

        <!-- Right utility & user info -->
        <div class="header-right">
          <!-- Switch to Admin button (if authorized) -->
          <Button
            v-if="authStore.canAccessAdmin"
            size="small"
            severity="contrast"
            outlined
            @click="navigateTo('/admin/iam/departments')"
          >
            <template #icon>
              <LayoutDashboard :size="15" class="btn-icon" />
            </template>
            <span class="btn-text">{{ t('halls.switchToAdmin') }}</span>
          </Button>

          <!-- Language Switcher -->
          <LanguageSwitcher />

          <!-- User Display & Data Scope Badge -->
          <div class="user-profile-badge">
            <div class="user-avatar">
              {{ userDisplayName.charAt(0).toUpperCase() }}
            </div>
            <div class="user-meta">
              <span class="user-name">{{ userDisplayName }}</span>
              <span v-if="dataScopeLabel" class="scope-tag" :title="dataScopeLabel">
                {{ dataScopeLabel }}
              </span>
            </div>
          </div>

          <!-- Change password -->
          <Button
            size="small"
            severity="secondary"
            text
            :title="t('auth.changePasswordTitle')"
            @click="navigateTo('/change-password')"
          >
            <template #icon>
              <KeyRound :size="16" />
            </template>
          </Button>

          <!-- Logout -->
          <Button
            size="small"
            severity="danger"
            text
            :loading="loggingOut"
            :title="t('auth.logout')"
            @click="handleLogout"
          >
            <template #icon>
              <LogOut :size="16" />
            </template>
          </Button>
        </div>
      </div>
    </header>

    <main class="portal-main">
      <div class="main-container">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--atria-bg, #f1f5f9);
}

.portal-header {
  background-color: var(--atria-surface, #ffffff);
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  position: sticky;
  top: 0;
  z-index: 50;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.header-container {
  max-width: 1300px;
  margin: 0 auto;
  padding: 0.625rem 1.25rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  text-decoration: none;
  color: inherit;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: linear-gradient(135deg, #059669, #10b981);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
}

.brand-text {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.brand-name {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.hall-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.portal-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.35rem 0.65rem;
  border-radius: var(--atria-radius, 6px);
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--atria-text-muted, #475569);
  text-decoration: none;
  transition: all 0.15s ease;
}

.nav-item:hover {
  background-color: var(--atria-surface-subtle, #f8fafc);
  color: var(--atria-text, #0f172a);
}

.nav-item-active {
  background-color: var(--atria-primary-soft, #eff6ff);
  color: var(--atria-primary, #1e40af);
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.btn-icon {
  margin-right: 0.375rem;
}

.btn-text {
  font-size: 0.8125rem;
  font-weight: 600;
}

.user-profile-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
}

.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--atria-primary, #1e40af);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.75rem;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--atria-text, #0f172a);
}

.scope-tag {
  font-size: 0.6875rem;
  color: var(--atria-text-light, #64748b);
  max-width: 120px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.portal-main {
  flex: 1;
  padding: 1.5rem;
}

.main-container {
  max-width: 1300px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .portal-nav {
    display: none;
  }
  .scope-tag {
    display: none;
  }
  .btn-text {
    display: none;
  }
  .btn-icon {
    margin-right: 0;
  }
}
</style>
