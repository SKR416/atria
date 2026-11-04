<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'
import {
  Clock,
  Calendar,
  Shield,
  User,
  Info,
  CircleCheck,
  FolderTree,
  Mail,
  Lock
} from 'lucide-vue-next'

const { t } = useI18n()
const authStore = useAuthStore()

const displayName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || ''
})

const dataScopeLabel = computed(() => {
  const scope = authStore.user?.dataScope
  if (!scope) return ''
  return t(`iam.dataScopeTypes.${scope}`)
})
</script>

<template>
  <div class="portal-home">
    <!-- Welcome Banner -->
    <div class="welcome-banner">
      <div class="welcome-content">
        <h1 class="welcome-title">
          {{ t('portalHall.welcomeTitle', { name: displayName }) }}
        </h1>
        <p class="welcome-desc">{{ t('portalHall.welcomeDesc') }}</p>
      </div>
      <div class="welcome-badge">
        <span class="atria-badge atria-badge-info">{{ dataScopeLabel }}</span>
      </div>
    </div>

    <!-- Phase 3 readiness notice -->
    <div class="phase3-notice">
      <Info :size="16" class="notice-icon" />
      <span>{{ t('portalHall.phase3ReadyHint') }}</span>
    </div>

    <!-- Main Grid: 2 columns -->
    <div class="portal-grid">
      <!-- Left Column: Clock-in & Calendar Cards -->
      <div class="portal-col">
        <!-- Quick Clock-In Card -->
        <section class="atria-card feature-card">
          <div class="card-header">
            <div class="header-title-wrap">
              <Clock :size="18" class="card-icon clock-icon" />
              <h2 class="card-title">{{ t('portalHall.quickClockCard') }}</h2>
            </div>
            <span class="atria-badge atria-badge-warning">Phase 3</span>
          </div>

          <div class="card-body clock-body">
            <div class="clock-display">
              <span class="clock-status-tag">{{ t('common.loading').replace('...', '') }}</span>
              <p class="clock-placeholder-hint">
                {{ t('portalHall.phase3ReadyHint') }}
              </p>
            </div>

            <div class="clock-actions">
              <Button
                disabled
                severity="primary"
                class="clock-btn"
                :label="t('common.confirm')"
              />
              <Button
                disabled
                severity="secondary"
                outlined
                class="clock-btn"
                :label="t('common.cancel')"
              />
            </div>
          </div>
        </section>

        <!-- Calendar Preview Card -->
        <section class="atria-card feature-card">
          <div class="card-header">
            <div class="header-title-wrap">
              <Calendar :size="18" class="card-icon cal-icon" />
              <h2 class="card-title">{{ t('portalHall.calendarCard') }}</h2>
            </div>
            <span class="atria-badge atria-badge-warning">Phase 3</span>
          </div>

          <div class="card-body cal-body">
            <div class="cal-placeholder">
              <p class="cal-hint">{{ t('portalHall.phase3ReadyHint') }}</p>
            </div>
          </div>
        </section>
      </div>

      <!-- Right Column: User Security Profile Card -->
      <div class="portal-col">
        <section class="atria-card feature-card profile-card">
          <div class="card-header">
            <div class="header-title-wrap">
              <Shield :size="18" class="card-icon shield-icon" />
              <h2 class="card-title">{{ t('portalHall.myProfileCard') }}</h2>
            </div>
            <span class="atria-badge atria-badge-success">{{ t('common.active') }}</span>
          </div>

          <div class="card-body profile-body">
            <!-- Basic User Meta -->
            <div class="profile-meta-list">
              <div class="profile-item">
                <span class="item-label">
                  <User :size="14" />
                  {{ t('auth.username') }}
                </span>
                <span class="item-value font-mono">{{ authStore.user?.username || '-' }}</span>
              </div>

              <div class="profile-item">
                <span class="item-label">
                  <CircleCheck :size="14" />
                  {{ t('iam.user.displayName') }}
                </span>
                <span class="item-value font-bold">{{ authStore.user?.displayName || '-' }}</span>
              </div>

              <div class="profile-item">
                <span class="item-label">
                  <Mail :size="14" />
                  {{ t('iam.user.email') }}
                </span>
                <span class="item-value">{{ authStore.user?.email || '-' }}</span>
              </div>

              <div class="profile-item">
                <span class="item-label">
                  <FolderTree :size="14" />
                  {{ t('iam.dept.treePath') }}
                </span>
                <span class="item-value font-mono">{{ authStore.user?.departmentTreePath || '-' }}</span>
              </div>

              <div class="profile-item">
                <span class="item-label">
                  <Lock :size="14" />
                  {{ t('auth.dataScope') }}
                </span>
                <span class="item-value">
                  <span class="atria-badge atria-badge-info">{{ dataScopeLabel }}</span>
                </span>
              </div>
            </div>

            <!-- Assigned Roles -->
            <div class="profile-section">
              <div class="section-label">{{ t('iam.user.roles') }}</div>
              <div class="tags-container">
                <span
                  v-for="role in authStore.roles"
                  :key="role"
                  class="atria-badge atria-badge-info"
                >
                  {{ role }}
                </span>
                <span v-if="!authStore.roles.length" class="empty-hint">
                  {{ t('common.noData') }}
                </span>
              </div>
            </div>

            <!-- Granted Permissions -->
            <div class="profile-section">
              <div class="section-label">{{ t('iam.role.permissions') }}</div>
              <div class="permissions-container">
                <span
                  v-for="perm in authStore.permissions"
                  :key="perm"
                  class="perm-badge"
                >
                  {{ perm }}
                </span>
                <span v-if="!authStore.permissions.length" class="empty-hint">
                  {{ t('common.noData') }}
                </span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.portal-home {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* Welcome Banner */
.welcome-banner {
  background: linear-gradient(135deg, var(--atria-surface, #ffffff), var(--atria-surface-subtle, #f8fafc));
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 1.25rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.welcome-title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.welcome-desc {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  color: var(--atria-text-muted, #475569);
}

/* Phase 3 Notice */
.phase3-notice {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--atria-radius, 6px);
  padding: 0.625rem 1rem;
  color: #166534;
  font-size: 0.8125rem;
}

.notice-icon {
  flex-shrink: 0;
  color: #15803d;
}

/* Grid */
.portal-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
}

.portal-col {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* Card General */
.feature-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.625rem;
}

.header-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card-icon {
  flex-shrink: 0;
}

.clock-icon {
  color: #0284c7;
}

.cal-icon {
  color: #7c3aed;
}

.shield-icon {
  color: #059669;
}

.card-title {
  font-size: 0.95rem;
  font-weight: 700;
  margin: 0;
  color: var(--atria-text, #0f172a);
}

/* Clock In */
.clock-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.clock-display {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px dashed var(--atria-border-strong, #cbd5e1);
  border-radius: 6px;
  padding: 1.5rem;
  text-align: center;
}

.clock-status-tag {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--atria-text-light, #64748b);
  display: block;
  margin-bottom: 0.5rem;
}

.clock-placeholder-hint {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #475569);
}

.clock-actions {
  display: flex;
  gap: 0.75rem;
}

.clock-btn {
  flex: 1;
}

/* Calendar */
.cal-placeholder {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px dashed var(--atria-border-strong, #cbd5e1);
  border-radius: 6px;
  padding: 2.5rem 1rem;
  text-align: center;
}

.cal-hint {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #475569);
}

/* Profile */
.profile-meta-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 1rem;
}

.profile-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.8125rem;
}

.item-label {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: var(--atria-text-muted, #475569);
  font-weight: 500;
}

.item-value {
  color: var(--atria-text, #0f172a);
}

.font-mono {
  font-family: monospace;
  font-size: 0.75rem;
}

.font-bold {
  font-weight: 600;
}

.profile-section {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin-top: 0.75rem;
}

.section-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--atria-text-muted, #475569);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.permissions-container {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  max-height: 140px;
  overflow-y: auto;
  padding: 0.375rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
}

.perm-badge {
  font-family: monospace;
  font-size: 0.6875rem;
  background: #ffffff;
  border: 1px solid var(--atria-border, #e2e8f0);
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  color: var(--atria-primary, #1e40af);
}

.empty-hint {
  font-size: 0.75rem;
  color: var(--atria-text-light, #64748b);
}

@media (max-width: 900px) {
  .portal-grid {
    grid-template-columns: 1fr;
  }
}
</style>
