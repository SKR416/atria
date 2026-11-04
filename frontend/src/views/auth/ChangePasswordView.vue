<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Password from 'primevue/password'
import Button from 'primevue/button'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { authApi } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'
import { KeyRound, CircleAlert, LogOut } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const loggingOut = ref(false)
const errorMessage = ref('')

async function handleSubmit() {
  errorMessage.value = ''

  if (!form.currentPassword) {
    return
  }

  if (form.newPassword.length < 8) {
    errorMessage.value = t('auth.passwordMinLength')
    return
  }

  if (form.newPassword !== form.confirmPassword) {
    errorMessage.value = t('auth.passwordMismatch')
    return
  }

  loading.value = true
  try {
    const res = await authApi.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })

    authStore.applyLoginResponse(res)

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('common.success'),
      life: 3000
    })

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
</script>

<template>
  <div class="change-pw-wrapper">
    <!-- Top toolbar for language & logout -->
    <header class="change-pw-topbar">
      <div class="topbar-brand">
        <span class="brand-text">{{ t('app.name') }}</span>
      </div>

      <div class="topbar-actions">
        <LanguageSwitcher />

        <Button
          size="small"
          severity="danger"
          text
          :loading="loggingOut"
          @click="handleLogout"
        >
          <template #icon>
            <LogOut :size="15" class="logout-icon" />
          </template>
          <span>{{ t('auth.logout') }}</span>
        </Button>
      </div>
    </header>

    <!-- Center Card -->
    <main class="change-pw-container">
      <div class="change-pw-card atria-card">
        <!-- Header -->
        <div class="card-header">
          <div class="icon-box">
            <KeyRound :size="24" class="header-icon" />
          </div>
          <h1 class="card-title">{{ t('auth.changePasswordTitle') }}</h1>
        </div>

        <!-- Mandatory Change Password Warning Banner (ADR-0004) -->
        <div v-if="authStore.mustChangePassword" class="force-hint-banner">
          <CircleAlert :size="18" class="banner-icon" />
          <p class="banner-text">{{ t('auth.changePasswordForceHint') }}</p>
        </div>

        <!-- Inline Error Banner -->
        <div v-if="errorMessage" class="error-banner">
          <CircleAlert :size="16" class="error-icon" />
          <span>{{ errorMessage }}</span>
        </div>

        <!-- Form -->
        <form class="pw-form" @submit.prevent="handleSubmit">
          <div class="form-field">
            <label for="currentPassword">{{ t('auth.currentPassword') }}</label>
            <Password
              id="currentPassword"
              v-model="form.currentPassword"
              :feedback="false"
              toggleMask
              :disabled="loading"
              class="w-full"
              style="width: 100%"
              :inputStyle="{ width: '100%' }"
              required
              autofocus
            />
          </div>

          <div class="form-field">
            <label for="newPassword">{{ t('auth.newPassword') }}</label>
            <Password
              id="newPassword"
              v-model="form.newPassword"
              :feedback="false"
              toggleMask
              :disabled="loading"
              class="w-full"
              style="width: 100%"
              :inputStyle="{ width: '100%' }"
              required
            />
          </div>

          <div class="form-field">
            <label for="confirmPassword">{{ t('auth.confirmPassword') }}</label>
            <Password
              id="confirmPassword"
              v-model="form.confirmPassword"
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
            :label="t('auth.updatePasswordButton')"
          />
        </form>
      </div>
    </main>
  </div>
</template>

<style scoped>
.change-pw-wrapper {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--atria-bg, #f1f5f9);
}

.change-pw-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
}

.brand-text {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.logout-icon {
  margin-right: 0.375rem;
}

.change-pw-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.change-pw-card {
  width: 100%;
  max-width: 440px;
  padding: 2rem;
  box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.08);
}

.card-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.icon-box {
  width: 44px;
  height: 44px;
  margin: 0 auto 0.75rem;
  border-radius: 8px;
  background: linear-gradient(135deg, #b45309, #f59e0b);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
}

.card-title {
  font-size: 1.25rem;
  font-weight: 700;
  margin: 0;
  color: var(--atria-text, #0f172a);
}

.force-hint-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  background-color: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--atria-radius, 6px);
  padding: 0.75rem 0.875rem;
  margin-bottom: 1.25rem;
}

.banner-icon {
  flex-shrink: 0;
  color: #b45309;
  margin-top: 0.125rem;
}

.banner-text {
  margin: 0;
  font-size: 0.8125rem;
  color: #92400e;
  line-height: 1.45;
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

.pw-form {
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
</style>
