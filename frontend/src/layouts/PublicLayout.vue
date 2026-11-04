<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter, RouterView, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import Button from 'primevue/button'
import { Building2, LogIn, LayoutDashboard, UserCheck } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="public-layout">
    <header class="public-header">
      <div class="header-container">
        <RouterLink to="/public" class="brand-link">
          <div class="brand-logo">
            <Building2 :size="20" class="brand-icon" />
          </div>
          <div class="brand-text">
            <span class="brand-name">{{ t('app.name') }}</span>
            <span class="hall-badge">{{ t('halls.public') }}</span>
          </div>
        </RouterLink>

        <div class="header-actions">
          <LanguageSwitcher />

          <template v-if="authStore.isAuthenticated">
            <Button
              size="small"
              severity="secondary"
              outlined
              @click="navigateTo('/portal')"
            >
              <template #icon>
                <UserCheck :size="16" class="btn-icon" />
              </template>
              <span class="btn-label">{{ t('halls.switchToPortal') }}</span>
            </Button>

            <Button
              v-if="authStore.canAccessAdmin"
              size="small"
              severity="primary"
              @click="navigateTo('/admin/iam/departments')"
            >
              <template #icon>
                <LayoutDashboard :size="16" class="btn-icon" />
              </template>
              <span class="btn-label">{{ t('halls.switchToAdmin') }}</span>
            </Button>
          </template>

          <template v-else>
            <Button
              size="small"
              severity="primary"
              @click="navigateTo('/login')"
            >
              <template #icon>
                <LogIn :size="16" class="btn-icon" />
              </template>
              <span class="btn-label">{{ t('auth.loginButton') }}</span>
            </Button>
          </template>
        </div>
      </div>
    </header>

    <main class="public-main">
      <RouterView />
    </main>

    <footer class="public-footer">
      <div class="footer-container">
        <span>{{ t('app.name') }} {{ t('app.subtitle') }}</span>
        <span class="footer-version">{{ t('app.version') }}</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.public-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--atria-bg, #f1f5f9);
}

.public-header {
  background-color: var(--atria-surface, #ffffff);
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  position: sticky;
  top: 0;
  z-index: 50;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.header-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0.75rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  text-decoration: none;
  color: inherit;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--atria-primary, #1e40af), #3b82f6);
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
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--atria-text, #0f172a);
}

.hall-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  background: var(--atria-primary-soft, #eff6ff);
  color: var(--atria-primary, #1e40af);
  border: 1px solid #bfdbfe;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.625rem;
}

.btn-icon {
  margin-right: 0.375rem;
}

.btn-label {
  font-weight: 600;
  font-size: 0.8125rem;
}

.public-main {
  flex: 1;
}

.public-footer {
  border-top: 1px solid var(--atria-border, #e2e8f0);
  background-color: var(--atria-surface, #ffffff);
  padding: 1rem 1.5rem;
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #475569);
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.footer-version {
  font-family: monospace;
  font-size: 0.75rem;
  color: var(--atria-text-light, #64748b);
}

@media (max-width: 640px) {
  .header-container {
    padding: 0.625rem 1rem;
  }
  .btn-label {
    display: none;
  }
  .btn-icon {
    margin-right: 0;
  }
}
</style>
