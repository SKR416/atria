<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, RouterView, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/iam'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import Button from 'primevue/button'
import {
  Building2,
  Users,
  ShieldCheck,
  FileClock,
  Shield,
  LogOut,
  ExternalLink,
  UserCheck,
  CalendarDays,
  Scale,
  LockKeyhole
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
    // Ignore logout error
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
  <div class="admin-layout">
    <!-- Left Sidebar for Japanese B2B high-density navigation -->
    <aside class="admin-sidebar">
      <div class="sidebar-header">
        <RouterLink to="/admin" class="sidebar-brand">
          <div class="brand-badge-icon">
            <Shield :size="18" />
          </div>
          <div class="brand-titles">
            <span class="brand-title">{{ t('app.name') }}</span>
            <span class="hall-tag">{{ t('halls.admin') }}</span>
          </div>
        </RouterLink>
      </div>

      <div class="sidebar-nav-container">
        <div class="nav-group-title">{{ t('nav.iamGroup') }}</div>

        <nav class="sidebar-menu">
          <RouterLink
            to="/admin/iam/departments"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
          >
            <Building2 :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.departments') }}</span>
          </RouterLink>

          <RouterLink
            to="/admin/iam/users"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
          >
            <Users :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.users') }}</span>
          </RouterLink>

          <RouterLink
            to="/admin/iam/roles"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
          >
            <ShieldCheck :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.roles') }}</span>
          </RouterLink>

          <RouterLink
            to="/admin/iam/audit-logs"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
          >
            <FileClock :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.auditLogs') }}</span>
          </RouterLink>
        </nav>

        <div class="nav-group-title" style="margin-top: 1.25rem">{{ t('nav.attendanceGroup') }}</div>

        <nav class="sidebar-menu">
          <RouterLink
            to="/admin/attendance/calendar"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
          >
            <CalendarDays :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.workCalendar') }}</span>
          </RouterLink>

          <RouterLink
            to="/admin/attendance/leave-overtime"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
            data-testid="nav-leave-overtime-rules"
          >
            <Scale :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.leaveOvertimeRules') }}</span>
          </RouterLink>

          <RouterLink
            to="/admin/attendance/monthly-close"
            class="sidebar-nav-item"
            active-class="sidebar-nav-item-active"
          >
            <LockKeyhole :size="16" class="nav-icon" />
            <span class="nav-label">{{ t('nav.monthlyClose') }}</span>
          </RouterLink>
        </nav>
      </div>

      <div class="sidebar-footer">
        <div class="system-meta">
          <span class="system-ver">{{ t('app.version') }}</span>
        </div>
      </div>
    </aside>

    <!-- Main Container -->
    <div class="admin-main-wrap">
      <!-- Topbar with Hall switchers, LanguageSwitcher, User profile, Logout -->
      <header class="admin-topbar">
        <div class="topbar-left">
          <!-- Hall Switcher: ESS Portal -->
          <Button
            size="small"
            severity="secondary"
            outlined
            :label="t('halls.switchToPortal')"
            @click="navigateTo('/portal')"
          >
            <template #icon>
              <UserCheck :size="15" class="topbar-btn-icon" />
            </template>
          </Button>

          <!-- Hall Switcher: Public Site -->
          <Button
            size="small"
            severity="secondary"
            text
            :label="t('halls.switchToPublic')"
            @click="navigateTo('/public')"
          >
            <template #icon>
              <ExternalLink :size="14" class="topbar-btn-icon" />
            </template>
          </Button>
        </div>

        <div class="topbar-right">
          <!-- Language Switcher -->
          <LanguageSwitcher />

          <!-- Current user info -->
          <div class="user-chip">
            <span class="user-name">{{ userDisplayName }}</span>
            <span v-if="dataScopeLabel" class="atria-badge atria-badge-info" :title="dataScopeLabel">
              {{ dataScopeLabel }}
            </span>
          </div>

          <!-- Logout button -->
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
      </header>

      <!-- Main Router Content -->
      <main class="admin-page-body">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background-color: var(--atria-bg, #f1f5f9);
}

/* Sidebar Styles */
.admin-sidebar {
  width: 240px;
  background-color: #0f172a;
  color: #f8fafc;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #1e293b;
}

.sidebar-header {
  padding: 1rem 1.125rem;
  border-bottom: 1px solid #1e293b;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  text-decoration: none;
  color: inherit;
}

.brand-badge-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
}

.brand-titles {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #ffffff;
  line-height: 1.2;
}

.hall-tag {
  font-size: 0.6875rem;
  color: #94a3b8;
  font-weight: 500;
}

.sidebar-nav-container {
  flex: 1;
  padding: 1rem 0.75rem;
}

.nav-group-title {
  padding: 0 0.5rem 0.5rem;
  font-size: 0.6875rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
}

.sidebar-menu {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.sidebar-nav-item {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  color: #cbd5e1;
  text-decoration: none;
  font-size: 0.8125rem;
  font-weight: 500;
  transition: all 0.15s ease;
}

.sidebar-nav-item:hover {
  background-color: #1e293b;
  color: #ffffff;
}

.sidebar-nav-item-active {
  background-color: #1e40af;
  color: #ffffff;
  font-weight: 600;
}

.nav-icon {
  flex-shrink: 0;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-footer {
  padding: 0.75rem 1.125rem;
  border-top: 1px solid #1e293b;
  font-size: 0.75rem;
  color: #64748b;
  font-family: monospace;
}

/* Main Container Styles */
.admin-main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.admin-topbar {
  height: 52px;
  background-color: var(--atria-surface, #ffffff);
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.25rem;
  gap: 1rem;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.topbar-btn-icon {
  margin-right: 0.375rem;
}

.topbar-btn-text {
  font-size: 0.8125rem;
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  background-color: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
}

.user-name {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--atria-text, #0f172a);
}

.admin-page-body {
  flex: 1;
  padding: 1.25rem;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .admin-sidebar {
    width: 60px;
  }
  .brand-titles,
  .nav-group-title,
  .nav-label,
  .system-meta,
  .topbar-btn-text {
    display: none;
  }
  .sidebar-nav-item {
    justify-content: center;
    padding: 0.5rem;
  }
  .sidebar-header {
    padding: 0.75rem 0.5rem;
    display: flex;
    justify-content: center;
  }
  .topbar-btn-icon {
    margin-right: 0;
  }
}
</style>
