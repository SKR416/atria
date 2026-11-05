<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'
import {
  Megaphone,
  Calendar,
  Building2,
  ArrowRight,
  Info,
  Clock,
  Globe,
  ShieldCheck,
  CalendarDays
} from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

const now = ref<Date>(new Date())
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const taipeiTime = computed(() => {
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Taipei',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(now.value)
  } catch {
    return '--:--:--'
  }
})

const tokyoTime = computed(() => {
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Tokyo',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(now.value)
  } catch {
    return '--:--:--'
  }
})

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
      <!-- Top Split Hero Banner -->
      <section class="hero-split-card">
        <div class="hero-left">
          <div class="hero-badge">
            <Building2 :size="14" class="badge-icon" />
            <span>{{ t('publicHall.heroBadge') }}</span>
          </div>

          <h1 class="hero-title">{{ t('publicHall.heroTitle') }}</h1>
          <p class="hero-description">{{ t('publicHall.heroDesc') }}</p>

          <div class="hero-actions">
            <Button
              severity="primary"
              :label="authStore.isAuthenticated ? t('halls.switchToPortal') : t('auth.loginButton')"
              @click="handleActionClick"
            >
              <template #icon>
                <ArrowRight :size="16" class="btn-arrow" />
              </template>
            </Button>

            <Button
              v-if="authStore.canAccessAdmin"
              severity="secondary"
              outlined
              :label="t('halls.switchToAdmin')"
              @click="router.push('/admin/iam/departments')"
            />
          </div>
        </div>

        <!-- Subtle Light Regional Clocks & User-Facing Highlights -->
        <div class="hero-right">
          <div class="region-clock-grid">
            <div class="region-clock-card">
              <div class="region-top">
                <span class="region-name">{{ t('publicHall.regionTwLabel') }}</span>
                <span class="region-tz">UTC+8</span>
              </div>
              <div class="region-time-row">
                <Clock :size="16" class="clock-muted-icon" />
                <span class="region-time">{{ taipeiTime }}</span>
              </div>
              <div class="region-meta">{{ t('publicHall.standardShiftHint') }}</div>
            </div>

            <div class="region-clock-card">
              <div class="region-top">
                <span class="region-name">{{ t('publicHall.regionJpLabel') }}</span>
                <span class="region-tz">UTC+9</span>
              </div>
              <div class="region-time-row">
                <Clock :size="16" class="clock-muted-icon" />
                <span class="region-time">{{ tokyoTime }}</span>
              </div>
              <div class="region-meta">{{ t('publicHall.standardShiftHint') }}</div>
            </div>
          </div>

          <div class="system-capabilities-strip">
            <div class="cap-chip">
              <CalendarDays :size="15" class="cap-icon" />
              <span>{{ t('publicHall.featureCalendar') }}</span>
            </div>
            <div class="cap-chip">
              <Clock :size="15" class="cap-icon" />
              <span>{{ t('publicHall.featurePunch') }}</span>
            </div>
            <div class="cap-chip">
              <ShieldCheck :size="15" class="cap-icon" />
              <span>{{ t('publicHall.featureApproval') }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Main 2-Column Content Grid -->
      <div class="public-main-grid">
        <!-- Left Column: Public Announcements -->
        <section class="notice-section">
          <div class="section-header">
            <div class="section-title-wrap">
              <Megaphone :size="20" class="section-icon" />
              <h2 class="section-title">{{ t('publicHall.noticeBoardTitle') }}</h2>
            </div>
            <span class="atria-badge atria-badge-info">{{ t('publicHall.noticeCountBadge') }}</span>
          </div>

          <div class="notice-grid">
            <!-- Notice 1 -->
            <article class="atria-card notice-card">
              <div class="notice-meta">
                <span class="atria-badge atria-badge-info">{{ t('publicHall.categoryHr') }}</span>
                <div class="notice-date">
                  <Calendar :size="14" />
                  <span>{{ t('publicHall.sampleNotice1Date') }}</span>
                </div>
              </div>
              <h3 class="notice-title">{{ t('publicHall.sampleNotice1Title') }}</h3>
              <p class="notice-summary">{{ t('publicHall.sampleNotice1Summary') }}</p>
            </article>

            <!-- Notice 2 -->
            <article class="atria-card notice-card">
              <div class="notice-meta">
                <span class="atria-badge atria-badge-info">{{ t('publicHall.categorySystem') }}</span>
                <div class="notice-date">
                  <Calendar :size="14" />
                  <span>{{ t('publicHall.sampleNotice2Date') }}</span>
                </div>
              </div>
              <h3 class="notice-title">{{ t('publicHall.sampleNotice2Title') }}</h3>
              <p class="notice-summary">{{ t('publicHall.sampleNotice2Summary') }}</p>
            </article>
          </div>

          <!-- Brand-Aligned Portal Guidance Callout -->
          <div class="portal-hint-callout">
            <Info :size="18" class="callout-icon" />
            <div class="callout-text">
              <span>{{ t('publicHall.upcomingPhaseHint') }}</span>
            </div>
          </div>
        </section>

        <!-- Right Sidebar: Platform Service Guide -->
        <aside class="public-sidebar">
          <div class="atria-card sidebar-card">
            <div class="sidebar-card-header">
              <Building2 :size="18" class="section-icon" />
              <h3 class="sidebar-card-title">{{ t('publicHall.guideSectionTitle') }}</h3>
            </div>

            <div class="hall-guide-list">
              <div class="hall-guide-item">
                <div class="guide-icon-box">
                  <Globe :size="17" />
                </div>
                <div class="guide-body">
                  <div class="guide-title">{{ t('halls.public') }}</div>
                  <div class="guide-desc">{{ t('nav.publicHome') }}</div>
                </div>
              </div>

              <div class="hall-guide-item">
                <div class="guide-icon-box">
                  <CalendarDays :size="17" />
                </div>
                <div class="guide-body">
                  <div class="guide-title">{{ t('halls.portal') }}</div>
                  <div class="guide-desc">{{ t('nav.portalHome') }}</div>
                </div>
              </div>

              <div class="hall-guide-item">
                <div class="guide-icon-box">
                  <ShieldCheck :size="17" />
                </div>
                <div class="guide-body">
                  <div class="guide-title">{{ t('halls.admin') }}</div>
                  <div class="guide-desc">{{ t('nav.iamGroup') }} / {{ t('nav.attendanceGroup') }}</div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.public-home {
  padding: 1.75rem 2rem 3.5rem;
}

.public-container {
  max-width: 1680px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

/* Split Hero Section */
.hero-split-card {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 2.25rem;
  align-items: center;
  padding: 2.25rem 2.5rem;
  background: #ffffff;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.hero-left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 600;
  background-color: var(--atria-primary-soft, #eff6ff);
  color: var(--atria-primary, #1e40af);
  border: 1px solid #bfdbfe;
  margin-bottom: 1rem;
}

.badge-icon {
  color: var(--atria-primary, #1e40af);
}

.hero-title {
  font-size: 1.9rem;
  font-weight: 800;
  color: var(--atria-text, #0f172a);
  letter-spacing: -0.02em;
  margin: 0 0 0.75rem;
  line-height: 1.3;
}

.hero-description {
  max-width: 680px;
  margin: 0 0 1.5rem;
  font-size: 1rem;
  color: var(--atria-text-muted, #475569);
  line-height: 1.65;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.btn-arrow {
  margin-right: 0.375rem;
}

/* Right Hero Regional Clocks - Light & Subordinate */
.hero-right {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.region-clock-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.875rem;
}

.region-clock-card {
  background: var(--atria-surface-subtle, #f8fafc);
  color: var(--atria-text, #0f172a);
  padding: 1rem 1.125rem;
  border-radius: 8px;
  border: 1px solid var(--atria-border, #e2e8f0);
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.region-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.region-name {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.region-tz {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--atria-primary, #1e40af);
  background: var(--atria-primary-soft, #eff6ff);
  padding: 0.1rem 0.45rem;
  border-radius: 4px;
  font-variant-numeric: tabular-nums;
}

.region-time-row {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0.15rem 0;
}

.clock-muted-icon {
  color: var(--atria-primary, #1e40af);
  flex-shrink: 0;
}

.region-time {
  font-size: 1.4rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  color: var(--atria-text, #0f172a);
}

.region-meta {
  font-size: 0.84rem;
  color: var(--atria-text-muted, #475569);
  font-variant-numeric: tabular-nums;
}

.system-capabilities-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.cap-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.375rem 0.75rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: #334155;
}

.cap-icon {
  color: var(--atria-primary, #1e40af);
  flex-shrink: 0;
}

/* Main 2-Column Content Layout */
.public-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 2.2fr) minmax(340px, 1fr);
  gap: 1.5rem;
  align-items: start;
}

/* Notice Section */
.notice-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
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
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
  margin: 0;
}

.notice-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 1rem;
}

.notice-card {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding: 1.35rem;
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
  gap: 0.35rem;
  font-size: 0.85rem;
  color: var(--atria-text-light, #64748b);
  font-variant-numeric: tabular-nums;
}

.notice-title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
  margin: 0;
  line-height: 1.45;
}

.notice-summary {
  font-size: 0.9375rem;
  color: var(--atria-text-muted, #475569);
  margin: 0;
  line-height: 1.6;
}

/* Brand-Aligned Callout (Blue Info instead of Green) */
.portal-hint-callout {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background-color: var(--atria-primary-soft, #eff6ff);
  border: 1px solid #bfdbfe;
  border-radius: var(--atria-radius, 6px);
  padding: 0.875rem 1.125rem;
  color: #1e3a8a;
  font-size: 0.9rem;
}

.callout-icon {
  flex-shrink: 0;
  color: var(--atria-primary, #1e40af);
}

.callout-text {
  line-height: 1.5;
}

/* Right Sidebar */
.sidebar-card {
  padding: 1.35rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.sidebar-card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding-bottom: 0.625rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.sidebar-card-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.hall-guide-list {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.hall-guide-item {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
  padding: 0.75rem 0.875rem;
  border-radius: 6px;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
}

.guide-icon-box {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: var(--atria-primary-soft, #eff6ff);
  color: var(--atria-primary, #1e40af);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.guide-body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.guide-title {
  font-size: 0.9375rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.guide-desc {
  font-size: 0.85rem;
  color: var(--atria-text-muted, #475569);
}

@media (max-width: 1024px) {
  .hero-split-card {
    grid-template-columns: 1fr;
    padding: 1.5rem;
  }
  .public-main-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .public-home {
    padding: 1.25rem 1rem 2.5rem;
  }
  .region-clock-grid {
    grid-template-columns: 1fr;
  }
  .hero-title {
    font-size: 1.5rem;
  }
}
</style>
