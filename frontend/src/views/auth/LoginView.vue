<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { authApi } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'
import { Building2, ArrowLeft, CircleAlert } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const errorMessage = ref('')

async function handleLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  errorMessage.value = ''

  try {
    const res = await authApi.login({
      username: form.username.trim(),
      password: form.password
    })

    authStore.applyLoginResponse(res)

    if (res.mustChangePassword || res.user?.mustChangePassword) {
      await router.push('/change-password')
      return
    }

    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      await router.push(redirect)
      return
    }

    if (authStore.canAccessAdmin) {
      await router.push('/admin/iam/departments')
    } else {
      await router.push('/portal')
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    errorMessage.value = msg
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: msg,
      life: 5000
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrapper">
    <!-- Top toolbar for language & back link -->
    <header class="login-topbar">
      <RouterLink to="/public" class="back-link">
        <ArrowLeft :size="16" />
        <span>{{ t('halls.switchToPublic') }}</span>
      </RouterLink>

      <div class="topbar-actions">
        <LanguageSwitcher />
      </div>
    </header>

    <!-- Center Login Card -->
    <main class="login-container">
      <div class="login-card atria-card">
        <!-- Brand Header -->
        <div class="login-header">
          <div class="logo-box">
            <Building2 :size="24" class="logo-icon" />
          </div>
          <h1 class="login-title">{{ t('auth.loginTitle') }}</h1>
          <p class="login-subtitle">{{ t('auth.loginSubtitle') }}</p>
        </div>

        <!-- Inline Error Banner -->
        <div v-if="errorMessage" class="error-banner">
          <CircleAlert :size="16" class="error-icon" />
          <span>{{ errorMessage }}</span>
        </div>

        <!-- Login Form -->
        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-field">
            <label for="username">{{ t('auth.username') }}</label>
            <InputText
              id="username"
              v-model="form.username"
              type="text"
              class="w-full"
              :disabled="loading"
              autofocus
              required
            />
          </div>

          <div class="form-field">
            <label for="password">{{ t('auth.password') }}</label>
            <Password
              id="password"
              v-model="form.password"
              :feedback="false"
              toggleMask
              :disabled="loading"
              class="w-full"
              style="width: 100%"
              :inputStyle="{ width: '100%' }"
              required
            />
          </div>

          <Button
            type="submit"
            severity="primary"
            class="submit-btn"
            :loading="loading"
            :label="t('auth.loginButton')"
          />
        </form>
      </div>

      <!-- Footer Info -->
      <footer class="login-footer">
        <span>{{ t('app.name') }} {{ t('app.subtitle') }}</span>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.login-wrapper {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--atria-bg, #f1f5f9);
}

.login-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--atria-text-muted, #475569);
  text-decoration: none;
}

.back-link:hover {
  color: var(--atria-primary, #1e40af);
}

.login-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 2rem;
  box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.logo-box {
  width: 44px;
  height: 44px;
  margin: 0 auto 0.75rem;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--atria-primary, #1e40af), #3b82f6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
}

.login-title {
  font-size: 1.25rem;
  font-weight: 700;
  margin: 0 0 0.375rem;
  color: var(--atria-text, #0f172a);
}

.login-subtitle {
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #475569);
  margin: 0;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--atria-radius, 6px);
  padding: 0.625rem 0.875rem;
  color: var(--atria-danger, #b91c1c);
  font-size: 0.8125rem;
  margin-bottom: 1.25rem;
}

.error-icon {
  flex-shrink: 0;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.125rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-field label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--atria-text-muted, #475569);
}

.w-full {
  width: 100%;
}

.submit-btn {
  margin-top: 0.5rem;
  width: 100%;
}

.login-footer {
  margin-top: 2rem;
  font-size: 0.75rem;
  color: var(--atria-text-light, #64748b);
  text-align: center;
}
</style>
