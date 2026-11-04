<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'
import {
  Megaphone,
  Calendar,
  Sparkles,
  ArrowRight,
  Info,
  Clock,
  Building2
} from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

function handleActionClick() {
  if (authStore.isAuthenticated) {
    router.push('/portal')
  } else {
    router.push('/login')
  }
}
</script>

<template>
  <div class="public-home">
    <div class="public-container">
      <!-- Hero Banner -->
      <section class="hero-section">
        <div class="hero-badge">
          <Sparkles :size="14" class="badge-icon" />
          <span>{{ t('app.name') }} {{ t('app.version') }}</span>
        </div>

        <h1 class="hero-title">{{ t('publicHall.heroTitle') }}</h1>
        <p class="hero-description">{{ t('publicHall.heroDesc') }}</p>

        <div class="hero-actions">
          <Button
            size="large"
            severity="primary"
            @click="handleActionClick"
          >
            <span>
              {{ authStore.isAuthenticated ? t('halls.switchToPortal') : t('auth.loginButton') }}
            </span>
            <ArrowRight :size="16" class="btn-arrow" />
          </Button>
        </div>
      </section>

      <!-- Phase Notification Callout -->
      <div class="phase-callout">
        <Info :size="18" class="callout-icon" />
        <div class="callout-text">
          <span class="callout-title">{{ t('common.loading').replace('...', '') }}</span>
          <span>{{ t('publicHall.upcomingPhaseHint') }}</span>
        </div>
      </div>

      <!-- Notice Board -->
      <section class="notice-section">
        <div class="section-header">
          <div class="section-title-wrap">
            <Megaphone :size="20" class="section-icon" />
            <h2 class="section-title">{{ t('publicHall.noticeBoardTitle') }}</h2>
          </div>
        </div>

        <div class="notice-grid">
          <!-- Notice 1 -->
          <article class="atria-card notice-card">
            <div class="notice-meta">
              <span class="atria-badge atria-badge-info">System</span>
              <div class="notice-date">
                <Calendar :size="13" />
                <span>{{ t('publicHall.sampleNotice1Date') }}</span>
              </div>
            </div>
            <h3 class="notice-title">{{ t('publicHall.sampleNotice1Title') }}</h3>
            <p class="notice-summary">{{ t('publicHall.sampleNotice1Summary') }}</p>
          </article>

          <!-- Notice 2 -->
          <article class="atria-card notice-card">
            <div class="notice-meta">
              <span class="atria-badge atria-badge-success">HR</span>
              <div class="notice-date">
                <Calendar :size="13" />
                <span>{{ t('publicHall.sampleNotice2Date') }}</span>
              </div>
            </div>
            <h3 class="notice-title">{{ t('publicHall.sampleNotice2Title') }}</h3>
            <p class="notice-summary">{{ t('publicHall.sampleNotice2Summary') }}</p>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.public-home {
  padding: 2.5rem 1.5rem 4rem;
}

.public-container {
  max-width: 1000px;
  margin: 0 auto;
}

/* Hero Section */
.hero-section {
  text-align: center;
  padding: 3rem 1.5rem;
  background: linear-gradient(180deg, var(--atria-surface, #ffffff) 0%, var(--atria-surface-subtle, #f8fafc) 100%);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.05);
  margin-bottom: 2rem;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  background-color: var(--atria-primary-soft, #eff6ff);
  color: var(--atria-primary, #1e40af);
  border: 1px solid #bfdbfe;
  margin-bottom: 1.25rem;
}

.badge-icon {
  color: var(--atria-primary, #1e40af);
}

.hero-title {
  font-size: 2rem;
  font-weight: 800;
  color: var(--atria-text, #0f172a);
  letter-spacing: -0.02em;
  margin: 0 0 0.875rem;
  line-height: 1.25;
}

.hero-description {
  max-width: 680px;
  margin: 0 auto 1.75rem;
  font-size: 1rem;
  color: var(--atria-text-muted, #475569);
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
}

.btn-arrow {
  margin-left: 0.5rem;
}

/* Callout */
.phase-callout {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--atria-radius, 6px);
  padding: 0.875rem 1rem;
  color: #166534;
  font-size: 0.875rem;
  margin-bottom: 2rem;
}

.callout-icon {
  flex-shrink: 0;
  color: #15803d;
  margin-top: 0.125rem;
}

.callout-text {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  line-height: 1.45;
}

.callout-title {
  font-weight: 700;
}

/* Notice Section */
.notice-section {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.625rem;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.section-icon {
  color: var(--atria-primary, #1e40af);
}

.section-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
  margin: 0;
}

.notice-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1.25rem;
}

.notice-card {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.notice-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.notice-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.notice-date {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--atria-text-light, #64748b);
  font-family: monospace;
}

.notice-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
  margin: 0;
  line-height: 1.4;
}

.notice-summary {
  font-size: 0.875rem;
  color: var(--atria-text-muted, #475569);
  margin: 0;
  line-height: 1.5;
}

@media (max-width: 640px) {
  .hero-title {
    font-size: 1.5rem;
  }
  .public-home {
    padding: 1.5rem 1rem 3rem;
  }
}
</style>
