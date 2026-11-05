<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { punchApi } from '@/api/attendance'
import type {
  TodayAttendanceSummaryResponse,
  MonthlyOverviewResponse,
  DailyAttendanceRecordResponse,
  PunchLogResponse,
  WorkCalendarDayResponse,
  AttendanceStatus,
  PunchSource
} from '@/types/attendance'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import {
  Clock,
  Calendar as CalendarIcon,
  Shield,
  User,
  Info,
  CircleCheck,
  FolderTree,
  Mail,
  Lock,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  Globe,
  AlertCircle,
  CheckCircle2,
  CalendarDays,
  FileText,
  Activity,
  Layers,
  Sparkles
} from 'lucide-vue-next'

interface CalendarGridCell {
  dateStr: string
  dayNumber: number
  isCurrentMonth: boolean
  isToday: boolean
  calendarDay?: WorkCalendarDayResponse
  dailyRecord?: DailyAttendanceRecordResponse
  punchLogsCount: number
}

const { t, te } = useI18n()
const toast = useToast()
const authStore = useAuthStore()

// User identity & metadata
const displayName = computed<string>(() => {
  return authStore.user?.displayName || authStore.user?.username || ''
})

const dataScopeLabel = computed<string>(() => {
  const scope = authStore.user?.dataScope
  if (!scope) return ''
  return t(`iam.dataScopeTypes.${scope}`)
})

// Timezone & Live Clock
const currentTime = ref<Date>(new Date())
let clockTimer: ReturnType<typeof setInterval> | null = null

const summary = ref<TodayAttendanceSummaryResponse | null>(null)
const loadingSummary = ref<boolean>(false)

const effectiveTimezone = computed<string>(() => {
  return summary.value?.timezone || authStore.user?.departmentTimezone || 'Asia/Taipei'
})

const regionalCalendarCode = computed<string>(() => {
  return summary.value?.calendarCode || authStore.user?.departmentCalendarCode || 'TW'
})

const formattedLiveTime = computed<string>(() => {
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: effectiveTimezone.value,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(currentTime.value)
  } catch {
    return currentTime.value.toTimeString().slice(0, 8)
  }
})

const formattedLiveDate = computed<string>(() => {
  const locale = authStore.activeLocale || 'zh-TW'
  try {
    return new Intl.DateTimeFormat(locale, {
      timeZone: effectiveTimezone.value,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      weekday: 'short'
    }).format(currentTime.value)
  } catch {
    return ''
  }
})

const todayDateStr = computed<string>(() => {
  if (summary.value?.logicalWorkDate) {
    return summary.value.logicalWorkDate
  }
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: effectiveTimezone.value,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).formatToParts(currentTime.value)
    const y = parts.find((p) => p.type === 'year')?.value
    const m = parts.find((p) => p.type === 'month')?.value
    const d = parts.find((p) => p.type === 'day')?.value
    if (y && m && d) return `${y}-${m}-${d}`
  } catch {
    // fallback
  }
  return new Date().toISOString().slice(0, 10)
})

// Punching Action State
const punchNote = ref<string>('')
const punching = ref<boolean>(false)

async function loadTodaySummary() {
  loadingSummary.value = true
  try {
    summary.value = await punchApi.getTodaySummary()
  } catch (err: unknown) {
    console.error('Failed to load today attendance summary', err)
  } finally {
    loadingSummary.value = false
  }
}

async function handlePunch() {
  if (punching.value || summary.value?.monthlyPeriodClosed) return
  punching.value = true
  try {
    const res = await punchApi.punchWeb({
      note: punchNote.value ? punchNote.value.trim() : null
    })
    summary.value = res
    punchNote.value = ''
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('attendance.portal.punchSuccess'),
      life: 4000
    })
    // Immediately reload monthly overview so the calendar and KPI stay in sync
    await loadMonthlyOverview()
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : t('common.error')
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: errorMsg,
      life: 5000
    })
  } finally {
    punching.value = false
  }
}

const sortedTodayPunchLogs = computed<PunchLogResponse[]>(() => {
  if (!summary.value?.punchLogs) return []
  return [...summary.value.punchLogs].sort(
    (a, b) => new Date(b.rawPunchTime).getTime() - new Date(a.rawPunchTime).getTime()
  )
})

// Monthly Attendance Overview State
function getInitialYearMonth(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

const selectedYearMonth = ref<string>(getInitialYearMonth())
const monthlyOverview = ref<MonthlyOverviewResponse | null>(null)
const loadingMonthly = ref<boolean>(false)

async function loadMonthlyOverview() {
  loadingMonthly.value = true
  try {
    monthlyOverview.value = await punchApi.getMonthlyOverview(selectedYearMonth.value)
  } catch (err: unknown) {
    console.error('Failed to load monthly attendance overview', err)
  } finally {
    loadingMonthly.value = false
  }
}

function prevMonth() {
  const [yStr, mStr] = selectedYearMonth.value.split('-')
  let y = parseInt(yStr, 10)
  let m = parseInt(mStr, 10) - 1
  if (m < 1) {
    m = 12
    y -= 1
  }
  selectedYearMonth.value = `${y}-${String(m).padStart(2, '0')}`
  loadMonthlyOverview()
}

function nextMonth() {
  const [yStr, mStr] = selectedYearMonth.value.split('-')
  let y = parseInt(yStr, 10)
  let m = parseInt(mStr, 10) + 1
  if (m > 12) {
    m = 1
    y += 1
  }
  selectedYearMonth.value = `${y}-${String(m).padStart(2, '0')}`
  loadMonthlyOverview()
}

function goToCurrentMonth() {
  const initial = getInitialYearMonth()
  if (selectedYearMonth.value !== initial) {
    selectedYearMonth.value = initial
    loadMonthlyOverview()
  }
}

// Maps for Calendar Grid Lookup
const calendarDayMap = computed(() => {
  const map = new Map<string, WorkCalendarDayResponse>()
  if (monthlyOverview.value?.calendarDays) {
    for (const d of monthlyOverview.value.calendarDays) {
      map.set(d.workDate, d)
    }
  }
  return map
})

const dailyRecordMap = computed(() => {
  const map = new Map<string, DailyAttendanceRecordResponse>()
  if (monthlyOverview.value?.dailyRecords) {
    for (const r of monthlyOverview.value.dailyRecords) {
      map.set(r.workDate, r)
    }
  }
  return map
})

const punchLogsByDateMap = computed(() => {
  const map = new Map<string, PunchLogResponse[]>()
  if (monthlyOverview.value?.punchLogs) {
    for (const p of monthlyOverview.value.punchLogs) {
      const list = map.get(p.workDate) || []
      list.push(p)
      map.set(p.workDate, list)
    }
  }
  return map
})

// Localized Weekday Headers (Sun .. Sat)
const weekDayHeaders = computed<string[]>(() => {
  const locale = authStore.activeLocale || 'zh-TW'
  const formatter = new Intl.DateTimeFormat(locale, { weekday: 'short' })
  // 2026-11-01 was a Sunday (offset 0..6 gives Sun..Sat)
  return [0, 1, 2, 3, 4, 5, 6].map((offset) => {
    const d = new Date(2026, 10, 1 + offset)
    return formatter.format(d)
  })
})

// Build 7-column Calendar Cells
const calendarGridCells = computed<CalendarGridCell[]>(() => {
  const [yStr, mStr] = selectedYearMonth.value.split('-')
  const year = parseInt(yStr, 10)
  const month = parseInt(mStr, 10)

  if (isNaN(year) || isNaN(month) || month < 1 || month > 12) {
    return []
  }

  const firstDay = new Date(year, month - 1, 1)
  const firstDayOfWeek = firstDay.getDay()
  const daysInMonth = new Date(year, month, 0).getDate()
  const prevDaysInMonth = new Date(year, month - 1, 0).getDate()

  const cells: CalendarGridCell[] = []

  // 1. Leading padding cells
  for (let i = 0; i < firstDayOfWeek; i++) {
    const dayNum = prevDaysInMonth - firstDayOfWeek + 1 + i
    let prevYear = year
    let prevMonth = month - 1
    if (prevMonth < 1) {
      prevMonth = 12
      prevYear -= 1
    }
    const prevDateStr = `${prevYear}-${String(prevMonth).padStart(2, '0')}-${String(dayNum).padStart(2, '0')}`
    cells.push({
      dateStr: prevDateStr,
      dayNumber: dayNum,
      isCurrentMonth: false,
      isToday: false,
      punchLogsCount: 0
    })
  }

  // 2. Current month cells
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const isToday = dateStr === todayDateStr.value
    const calendarDay = calendarDayMap.value.get(dateStr)
    const dailyRecord = dailyRecordMap.value.get(dateStr)
    const logs = punchLogsByDateMap.value.get(dateStr) || []
    cells.push({
      dateStr,
      dayNumber: d,
      isCurrentMonth: true,
      isToday,
      calendarDay,
      dailyRecord,
      punchLogsCount: logs.length
    })
  }

  // 3. Trailing padding cells
  const totalCells = cells.length
  const remainder = (7 - (totalCells % 7)) % 7
  for (let j = 1; j <= remainder; j++) {
    let nextYear = year
    let nextMonth = month + 1
    if (nextMonth > 12) {
      nextMonth = 1
      nextYear += 1
    }
    const nextDateStr = `${nextYear}-${String(nextMonth).padStart(2, '0')}-${String(j).padStart(2, '0')}`
    cells.push({
      dateStr: nextDateStr,
      dayNumber: j,
      isCurrentMonth: false,
      isToday: false,
      punchLogsCount: 0
    })
  }

  return cells
})

// Interactive Day Detail Dialog State
const selectedDay = ref<CalendarGridCell | null>(null)
const dayDetailVisible = ref<boolean>(false)

function openDayDetail(cell: CalendarGridCell) {
  if (!cell.isCurrentMonth) return
  selectedDay.value = cell
  dayDetailVisible.value = true
}

const selectedDayPunchLogs = computed<PunchLogResponse[]>(() => {
  if (!selectedDay.value || !selectedDay.value.isCurrentMonth) return []
  const dateStr = selectedDay.value.dateStr
  const monthLogs = punchLogsByDateMap.value.get(dateStr) || []
  if (summary.value?.logicalWorkDate === dateStr && summary.value.punchLogs?.length) {
    const logMap = new Map<number, PunchLogResponse>()
    for (const log of monthLogs) logMap.set(log.id, log)
    for (const log of summary.value.punchLogs) logMap.set(log.id, log)
    return Array.from(logMap.values()).sort(
      (a, b) => new Date(a.rawPunchTime).getTime() - new Date(b.rawPunchTime).getTime()
    )
  }
  return monthLogs
})

function getRoleLabel(code: string): string {
  return te(`roleCodes.${code}`) ? t(`roleCodes.${code}`) : code
}

function getPermissionLabel(code: string): string {
  return te(`permissionCodes.${code}`) ? t(`permissionCodes.${code}`) : code
}

// Collapsible Security Profile Card
const isProfileCollapsed = ref<boolean>(false)

// Formatting Helpers
function formatTimestamp(isoStr: string | null | undefined, includeSeconds = true): string {
  if (!isoStr) return '—'
  try {
    const d = new Date(isoStr)
    if (isNaN(d.getTime())) return isoStr
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: effectiveTimezone.value,
      hour: '2-digit',
      minute: '2-digit',
      ...(includeSeconds ? { second: '2-digit' } : {}),
      hour12: false
    }).format(d)
  } catch {
    return isoStr
  }
}

function formatTimeOnly(timeStr: string | null | undefined): string {
  if (!timeStr) return '—'
  if (timeStr.includes('T')) {
    return formatTimestamp(timeStr, false)
  }
  return timeStr.slice(0, 5)
}

function formatHoursMins(minutes: number | null | undefined): string {
  const m = minutes || 0
  const hours = Math.floor(m / 60)
  const mins = m % 60
  return t('attendance.portal.hoursMinutesUnit', { hours, mins })
}

function getHolidayName(day?: WorkCalendarDayResponse | null): string {
  if (!day) return ''
  if (day.localizedHolidayName) return day.localizedHolidayName
  const locale = authStore.activeLocale || 'zh-TW'
  if (day.holidayName && day.holidayName[locale]) {
    return day.holidayName[locale] as string
  }
  return (
    day.holidayName?.['zh-TW'] ||
    day.holidayName?.['ja-JP'] ||
    day.holidayName?.['en-US'] ||
    ''
  )
}

function getStatusSeverity(
  status?: AttendanceStatus | null
): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
  switch (status) {
    case 'NORMAL':
      return 'success'
    case 'LATE':
    case 'EARLY_LEAVE':
      return 'warn'
    case 'MISSING_PUNCH':
    case 'ABSENT':
      return 'danger'
    case 'ON_LEAVE':
      return 'info'
    default:
      return 'secondary'
  }
}

function getPunchSourceSeverity(source: PunchSource): 'info' | 'success' | 'warn' | 'secondary' {
  switch (source) {
    case 'WEB':
      return 'info'
    case 'BOT':
      return 'success'
    case 'CORRECTION':
      return 'warn'
    default:
      return 'secondary'
  }
}

onMounted(() => {
  clockTimer = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
  loadTodaySummary()
  loadMonthlyOverview()
})

onUnmounted(() => {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
})
</script>

<template>
  <div class="portal-home">
    <!-- Top Welcome & Context Banner -->
    <div class="welcome-banner">
      <div class="welcome-content">
        <div class="welcome-header-line">
          <h1 class="welcome-title">
            {{ t('portalHall.welcomeTitle', { name: displayName }) }}
          </h1>
          <span class="welcome-sub-pill">ESS Portal</span>
        </div>
        <p class="welcome-desc">{{ t('portalHall.welcomeDesc') }}</p>
      </div>

      <!-- Context Badges -->
      <div class="welcome-badges-strip">
        <span class="atria-badge atria-badge-info context-badge">
          <Globe :size="13" class="badge-icon" />
          <span>{{ effectiveTimezone }}</span>
        </span>
        <span class="atria-badge atria-badge-info context-badge">
          <CalendarIcon :size="13" class="badge-icon" />
          <span>{{ regionalCalendarCode }} Calendar</span>
        </span>
        <span class="atria-badge atria-badge-info context-badge">
          <Lock :size="13" class="badge-icon" />
          <span>{{ dataScopeLabel }}</span>
        </span>
      </div>
    </div>

    <!-- Main Workspace Grid -->
    <div class="portal-workspace-grid">
      <!-- Left Column: Live Clock-In & Profile -->
      <div class="portal-left-col">
        <!-- Live Clock-In Card -->
        <section class="atria-card clock-in-card">
          <div class="card-header">
            <div class="header-title-wrap">
              <Clock :size="18" class="card-icon clock-icon" />
              <h2 class="card-title">{{ t('attendance.portal.clockCardTitle') }}</h2>
            </div>
            <div class="header-status-wrap">
              <span class="live-dot" title="Live Clock Syncing"></span>
              <Tag
                v-if="summary?.dailyRecord?.status"
                :value="t('attendance.statuses.' + summary.dailyRecord.status)"
                :severity="getStatusSeverity(summary.dailyRecord.status)"
              />
              <Tag
                v-else-if="summary && !summary.workingDay"
                :value="t('attendance.statuses.HOLIDAY')"
                severity="secondary"
              />
              <Tag
                v-else
                :value="t('common.active')"
                severity="info"
              />
            </div>
          </div>

          <!-- Digital Clock Display -->
          <div class="digital-clock-display">
            <div class="clock-time font-mono">{{ formattedLiveTime }}</div>
            <div class="clock-date-meta">
              <span class="clock-date-text">{{ formattedLiveDate }}</span>
              <span class="clock-tz-text font-mono">({{ effectiveTimezone }})</span>
            </div>
          </div>

          <!-- Logical Date & Cutoff Info Box -->
          <div class="logical-date-strip">
            <div class="logical-info-row">
              <div class="info-cell">
                <span class="info-label">{{ t('attendance.portal.logicalWorkDate') }}</span>
                <span class="info-value font-mono font-bold">
                  {{ summary?.logicalWorkDate || todayDateStr }}
                </span>
              </div>

              <div class="info-cell align-right">
                <span class="info-label">{{ t('attendance.dayTypes.WORKDAY').split(' ')[0] }}</span>
                <div class="day-type-wrap">
                  <Tag
                    :value="t('attendance.dayTypes.' + (summary?.dayType || 'WORKDAY'))"
                    :severity="summary?.workingDay ? 'info' : 'secondary'"
                  />
                  <span v-if="summary?.localizedHolidayName" class="holiday-pill">
                    {{ summary.localizedHolidayName }}
                  </span>
                </div>
              </div>
            </div>

            <div class="cutoff-hint-row">
              <Info :size="13" class="hint-icon" />
              <span>
                {{ t('attendance.portal.cutoffHint', { hour: summary?.dayCutoffHour ?? 5 }) }}
              </span>
            </div>
          </div>

          <!-- Shift & Today Attendance Metrics Grid -->
          <div class="attendance-metrics-grid">
            <div class="metric-box shift-box">
              <span class="metric-label">{{ t('attendance.portal.scheduledShift') }}</span>
              <span class="metric-val font-mono">
                {{ formatTimeOnly(summary?.scheduledStartTime) !== '—' ? formatTimeOnly(summary?.scheduledStartTime) : '09:00' }}
                –
                {{ formatTimeOnly(summary?.scheduledEndTime) !== '—' ? formatTimeOnly(summary?.scheduledEndTime) : '18:00' }}
              </span>
            </div>

            <div class="metric-box hours-box">
              <span class="metric-label">{{ t('attendance.portal.workedMinutes') }}</span>
              <span class="metric-val font-bold highlight-val">
                {{ formatHoursMins(summary?.dailyRecord?.workedMinutes) }}
              </span>
            </div>

            <div class="metric-box">
              <span class="metric-label">{{ t('attendance.portal.firstClockIn') }}</span>
              <span class="metric-val font-mono">
                {{ formatTimestamp(summary?.dailyRecord?.firstRawClockIn, true) }}
              </span>
            </div>

            <div class="metric-box">
              <span class="metric-label">{{ t('attendance.portal.lastClockOut') }}</span>
              <span class="metric-val font-mono">
                {{ formatTimestamp(summary?.dailyRecord?.lastRawClockOut, true) }}
              </span>
            </div>

            <div class="metric-box">
              <span class="metric-label">{{ t('attendance.portal.effectiveClockIn') }}</span>
              <span class="metric-val font-mono">
                {{ formatTimestamp(summary?.dailyRecord?.effectiveClockIn, false) }}
              </span>
            </div>

            <div class="metric-box">
              <span class="metric-label">{{ t('attendance.portal.effectiveClockOut') }}</span>
              <span class="metric-val font-mono">
                {{ formatTimestamp(summary?.dailyRecord?.effectiveClockOut, false) }}
              </span>
            </div>
          </div>

          <!-- Punch Action Section -->
          <div class="punch-action-section">
            <div class="punch-input-wrap">
              <InputText
                v-model="punchNote"
                :placeholder="t('attendance.portal.punchNotePlaceholder')"
                :disabled="summary?.monthlyPeriodClosed || punching || loadingSummary"
                class="punch-note-input"
              />
            </div>

            <Button
              :disabled="summary?.monthlyPeriodClosed || punching || loadingSummary"
              :loading="punching"
              severity="primary"
              class="punch-main-btn"
              :label="t('attendance.portal.punchButton')"
              @click="handlePunch"
            >
              <template #icon>
                <CheckCircle2 :size="18" class="btn-icon" />
              </template>
            </Button>

            <div v-if="summary?.monthlyPeriodClosed" class="period-closed-hint">
              <AlertCircle :size="14" class="closed-icon" />
              <span>{{ t('attendance.portal.monthClosedBadge') }}：{{ t('attendance.monthlyClose.noBlockingRequests') }}</span>
            </div>
          </div>

          <!-- Today's Append-Only Punch Log List -->
          <div class="today-logs-wrapper">
            <div class="logs-header">
              <div class="logs-title-wrap">
                <FileText :size="15" class="text-muted" />
                <span class="logs-title">{{ t('attendance.portal.todayPunchLogs') }}</span>
              </div>
              <span class="logs-count-badge font-mono">
                {{ sortedTodayPunchLogs.length }}
              </span>
            </div>

            <div v-if="!sortedTodayPunchLogs.length" class="empty-logs-box">
              <Clock :size="20" class="empty-icon" />
              <span>{{ t('attendance.portal.noPunchToday') }}</span>
            </div>

            <div v-else class="logs-timeline-list">
              <div
                v-for="log in sortedTodayPunchLogs"
                :key="log.id"
                class="timeline-log-row"
              >
                <div class="timeline-dot"></div>
                <div class="timeline-log-content">
                  <div class="log-headline">
                    <span class="log-time font-mono">
                      {{ formatTimestamp(log.rawPunchTime, true) }}
                    </span>
                    <Tag
                      :value="t('attendance.punchSources.' + log.source)"
                      :severity="getPunchSourceSeverity(log.source)"
                      class="source-pill"
                    />
                  </div>
                  <div class="log-details font-mono">
                    <span class="log-ip">{{ log.clientIp || '127.0.0.1' }}</span>
                    <span v-if="log.note" class="log-note-text">
                      "{{ log.note }}"
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Compact User Security Profile Card -->
        <section class="atria-card profile-card">
          <div class="card-header clickable-header" @click="isProfileCollapsed = !isProfileCollapsed">
            <div class="header-title-wrap">
              <Shield :size="18" class="card-icon shield-icon" />
              <h2 class="card-title">{{ t('portalHall.myProfileCard') }}</h2>
            </div>
            <div class="header-action-wrap">
              <span class="atria-badge atria-badge-success">{{ t('common.active') }}</span>
              <Button
                text
                rounded
                size="small"
                severity="secondary"
                class="collapse-btn"
                :aria-label="isProfileCollapsed ? 'Expand' : 'Collapse'"
              >
                <template #icon>
                  <ChevronDown v-if="isProfileCollapsed" :size="16" />
                  <ChevronUp v-else :size="16" />
                </template>
              </Button>
            </div>
          </div>

          <div v-show="!isProfileCollapsed" class="card-body profile-body">
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
                  :title="role"
                >
                  {{ getRoleLabel(role) }}
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
                  :title="perm"
                >
                  {{ getPermissionLabel(perm) }}
                </span>
                <span v-if="!authStore.permissions.length" class="empty-hint">
                  {{ t('common.noData') }}
                </span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- Right Column: Monthly KPI Strip & Interactive Calendar -->
      <div class="portal-right-col">
        <section class="atria-card calendar-card">
          <!-- Calendar Header & Navigation Bar -->
          <div class="calendar-top-bar">
            <div class="header-title-wrap">
              <CalendarDays :size="20" class="card-icon cal-icon" />
              <h2 class="card-title">{{ t('attendance.portal.monthlyCalendarTitle') }}</h2>
            </div>

            <div class="month-nav-controls">
              <div class="nav-buttons-group">
                <Button
                  text
                  rounded
                  size="small"
                  severity="secondary"
                  class="month-arrow-btn"
                  @click="prevMonth"
                  :title="t('attendance.portal.prevMonth')"
                >
                  <template #icon>
                    <ChevronLeft :size="18" />
                  </template>
                </Button>

                <span class="current-month-display font-mono">
                  {{ selectedYearMonth }}
                </span>

                <Button
                  text
                  rounded
                  size="small"
                  severity="secondary"
                  class="month-arrow-btn"
                  @click="nextMonth"
                  :title="t('attendance.portal.nextMonth')"
                >
                  <template #icon>
                    <ChevronRight :size="18" />
                  </template>
                </Button>
              </div>

              <Button
                size="small"
                outlined
                severity="secondary"
                class="current-month-btn"
                @click="goToCurrentMonth"
                :label="t('attendance.portal.currentMonth')"
              />

              <div class="month-lock-status">
                <Tag
                  v-if="monthlyOverview?.monthlyClosed"
                  :value="t('attendance.portal.monthClosedBadge')"
                  severity="warn"
                  class="status-tag"
                />
                <Tag
                  v-else
                  :value="t('attendance.portal.monthOpenBadge')"
                  severity="success"
                  class="status-tag"
                />
              </div>
            </div>
          </div>

          <!-- Monthly KPI Strip (4 Stat Tiles) -->
          <div class="monthly-kpi-strip">
            <div class="kpi-card">
              <span class="kpi-title">{{ t('attendance.portal.kpiTotalHours') }}</span>
              <span class="kpi-val font-mono font-bold">
                {{ formatHoursMins(monthlyOverview?.totalWorkedMinutes) }}
              </span>
              <span class="kpi-sub">
                <Activity :size="12" class="kpi-sub-icon" />
                {{ monthlyOverview?.calendarDays?.length || 0 }} Days in Month
              </span>
            </div>

            <div class="kpi-card">
              <span class="kpi-title">{{ t('attendance.portal.kpiNormalDays') }}</span>
              <span class="kpi-val font-mono font-bold text-success">
                {{ monthlyOverview?.normalDays ?? 0 }}
              </span>
              <span class="kpi-sub">
                <CheckCircle2 :size="12" class="kpi-sub-icon text-success" />
                {{ t('attendance.statuses.NORMAL') }}
              </span>
            </div>

            <div class="kpi-card">
              <span class="kpi-title">{{ t('attendance.portal.kpiLateEarly') }}</span>
              <span class="kpi-val font-mono font-bold text-warning">
                {{ monthlyOverview?.lateCount ?? 0 }} / {{ monthlyOverview?.earlyLeaveCount ?? 0 }}
              </span>
              <span class="kpi-sub">
                {{ t('attendance.statuses.LATE') }} / {{ t('attendance.statuses.EARLY_LEAVE') }}
              </span>
            </div>

            <div class="kpi-card">
              <span class="kpi-title">{{ t('attendance.portal.kpiAnomaly') }}</span>
              <span class="kpi-val font-mono font-bold text-danger">
                {{ monthlyOverview?.missingPunchCount ?? 0 }} / {{ monthlyOverview?.absentCount ?? 0 }}
              </span>
              <span class="kpi-sub">
                {{ t('attendance.statuses.MISSING_PUNCH') }} / {{ t('attendance.statuses.ABSENT') }}
              </span>
            </div>
          </div>

          <!-- 7-Column Interactive Monthly Calendar Grid -->
          <div class="calendar-grid-container">
            <!-- Weekday Headers -->
            <div class="calendar-week-header">
              <div
                v-for="(headerName, idx) in weekDayHeaders"
                :key="idx"
                class="week-header-cell"
                :class="{
                  'header-sun': idx === 0,
                  'header-sat': idx === 6
                }"
              >
                {{ headerName }}
              </div>
            </div>

            <!-- Month Days Grid -->
            <div class="calendar-days-grid">
              <div
                v-for="cell in calendarGridCells"
                :key="cell.dateStr"
                class="cal-day-cell"
                :class="{
                  'cell-outside': !cell.isCurrentMonth,
                  'cell-today': cell.isToday,
                  'cell-rest': cell.calendarDay?.dayType === 'REST_DAY',
                  'cell-statutory': cell.calendarDay?.dayType === 'STATUTORY_REST_DAY',
                  'cell-holiday': cell.calendarDay?.dayType === 'NATIONAL_HOLIDAY',
                  'cell-makeup': cell.calendarDay?.dayType === 'MAKEUP_WORKDAY'
                }"
                @click="openDayDetail(cell)"
              >
                <!-- Day Cell Header -->
                <div class="day-cell-top">
                  <span
                    class="day-num font-mono"
                    :class="{ 'today-circle': cell.isToday }"
                  >
                    {{ cell.dayNumber }}
                  </span>

                  <!-- Holiday / Makeup Day Badge -->
                  <div class="cell-type-tags">
                    <span
                      v-if="cell.calendarDay && getHolidayName(cell.calendarDay)"
                      class="holiday-tag-text"
                      :title="getHolidayName(cell.calendarDay)"
                    >
                      {{ getHolidayName(cell.calendarDay) }}
                    </span>
                    <span
                      v-else-if="cell.calendarDay?.dayType === 'MAKEUP_WORKDAY'"
                      class="makeup-tag-text"
                    >
                      {{ t('attendance.dayTypes.MAKEUP_WORKDAY').split(' ')[0] }}
                    </span>
                  </div>
                </div>

                <!-- Day Cell Body / Attendance Record -->
                <div v-if="cell.isCurrentMonth" class="day-cell-body">
                  <!-- Attendance Status Pill -->
                  <div v-if="cell.dailyRecord?.status" class="status-pill-wrap">
                    <Tag
                      :value="t('attendance.statuses.' + cell.dailyRecord.status)"
                      :severity="getStatusSeverity(cell.dailyRecord.status)"
                      class="mini-status-tag"
                    />
                  </div>

                  <!-- Clock-In / Out Times -->
                  <div
                    v-if="cell.dailyRecord?.effectiveClockIn || cell.dailyRecord?.effectiveClockOut"
                    class="cell-time-row font-mono"
                  >
                    <span>{{ formatTimeOnly(cell.dailyRecord.effectiveClockIn) }}</span>
                    <span class="time-sep">-</span>
                    <span>{{ formatTimeOnly(cell.dailyRecord.effectiveClockOut) }}</span>
                  </div>

                  <!-- Worked Hours Summary -->
                  <div
                    v-if="cell.dailyRecord && cell.dailyRecord.workedMinutes > 0"
                    class="cell-hours-row font-mono"
                  >
                    {{ formatHoursMins(cell.dailyRecord.workedMinutes) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <!-- Day Detail Dialog -->
    <Dialog
      v-model:visible="dayDetailVisible"
      modal
      :header="t('attendance.portal.dayDetailTitle', { date: selectedDay?.dateStr || '' })"
      :style="{ width: '600px', maxWidth: '95vw' }"
      class="day-detail-dialog"
    >
      <div v-if="selectedDay" class="dialog-content">
        <!-- Schedule Overview Banner -->
        <div class="detail-schedule-banner">
          <div class="schedule-top-row">
            <span class="detail-date font-mono font-bold">{{ selectedDay.dateStr }}</span>
            <div class="detail-tags">
              <Tag
                :value="t('attendance.dayTypes.' + (selectedDay.calendarDay?.dayType || 'WORKDAY'))"
                :severity="selectedDay.calendarDay?.workingDay ? 'info' : 'secondary'"
              />
              <Tag
                v-if="selectedDay.dailyRecord?.status"
                :value="t('attendance.statuses.' + selectedDay.dailyRecord.status)"
                :severity="getStatusSeverity(selectedDay.dailyRecord.status)"
              />
            </div>
          </div>

          <div v-if="getHolidayName(selectedDay.calendarDay)" class="holiday-alert">
            <Sparkles :size="14" class="text-warning" />
            <span>{{ getHolidayName(selectedDay.calendarDay) }}</span>
          </div>

          <div class="schedule-times-grid">
            <div class="time-item">
              <span class="time-label">{{ t('attendance.calendar.workStartTime') }} ~ {{ t('attendance.calendar.workEndTime') }}</span>
              <span class="time-val font-mono">
                {{ formatTimeOnly(selectedDay.calendarDay?.workStartTime) }}
                –
                {{ formatTimeOnly(selectedDay.calendarDay?.workEndTime) }}
              </span>
            </div>
            <div class="time-item">
              <span class="time-label">{{ t('attendance.calendar.breakStartTime') }} ~ {{ t('attendance.calendar.breakEndTime') }}</span>
              <span class="time-val font-mono">
                {{ formatTimeOnly(selectedDay.calendarDay?.breakStartTime) }}
                –
                {{ formatTimeOnly(selectedDay.calendarDay?.breakEndTime) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Derived Daily Record Metrics -->
        <div class="detail-section">
          <h3 class="section-heading">{{ t('attendance.portal.clockCardTitle') }}</h3>
          <div v-if="selectedDay.dailyRecord" class="detail-metrics-grid">
            <div class="d-metric-item">
              <span class="d-label">{{ t('attendance.portal.effectiveClockIn') }}</span>
              <span class="d-val font-mono">{{ formatTimestamp(selectedDay.dailyRecord.effectiveClockIn, false) }}</span>
            </div>
            <div class="d-metric-item">
              <span class="d-label">{{ t('attendance.portal.effectiveClockOut') }}</span>
              <span class="d-val font-mono">{{ formatTimestamp(selectedDay.dailyRecord.effectiveClockOut, false) }}</span>
            </div>
            <div class="d-metric-item">
              <span class="d-label">{{ t('attendance.portal.firstClockIn') }}</span>
              <span class="d-val font-mono">{{ formatTimestamp(selectedDay.dailyRecord.firstRawClockIn, true) }}</span>
            </div>
            <div class="d-metric-item">
              <span class="d-label">{{ t('attendance.portal.lastClockOut') }}</span>
              <span class="d-val font-mono">{{ formatTimestamp(selectedDay.dailyRecord.lastRawClockOut, true) }}</span>
            </div>
            <div class="d-metric-item highlight-item">
              <span class="d-label">{{ t('attendance.portal.workedMinutes') }}</span>
              <span class="d-val font-mono font-bold">{{ formatHoursMins(selectedDay.dailyRecord.workedMinutes) }}</span>
            </div>
            <div class="d-metric-item">
              <span class="d-label">{{ t('attendance.statuses.LATE') }} / {{ t('attendance.statuses.EARLY_LEAVE') }}</span>
              <span class="d-val font-mono">
                {{ selectedDay.dailyRecord.lateMinutes }} min / {{ selectedDay.dailyRecord.earlyLeaveMinutes }} min
              </span>
            </div>
          </div>
          <div v-else class="empty-detail-hint">
            <span>{{ t('common.noData') }}</span>
          </div>
        </div>

        <!-- Raw Append-Only Punch Logs -->
        <div class="detail-section">
          <div class="logs-header">
            <h3 class="section-heading">{{ t('attendance.portal.todayPunchLogs') }}</h3>
            <span class="logs-count-badge font-mono">{{ selectedDayPunchLogs.length }}</span>
          </div>

          <div v-if="!selectedDayPunchLogs.length" class="empty-detail-hint">
            <Clock :size="16" class="text-muted" />
            <span>{{ t('attendance.portal.noPunchToday') }}</span>
          </div>
          <div v-else class="detail-logs-list">
            <div
              v-for="log in selectedDayPunchLogs"
              :key="log.id"
              class="d-log-row"
            >
              <div class="d-log-time font-mono font-bold">
                {{ formatTimestamp(log.rawPunchTime, true) }}
              </div>
              <Tag
                :value="t('attendance.punchSources.' + log.source)"
                :severity="getPunchSourceSeverity(log.source)"
                class="source-pill"
              />
              <div class="d-log-meta font-mono">
                <span class="d-ip">{{ log.clientIp || '127.0.0.1' }}</span>
                <span v-if="log.note" class="d-note">"{{ log.note }}"</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.close')"
          severity="secondary"
          outlined
          @click="dayDetailVisible = false"
        />
      </template>
    </Dialog>
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
  padding: 1.15rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.welcome-header-line {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.welcome-title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.welcome-sub-pill {
  font-size: 0.75rem;
  font-weight: 700;
  color: #1e40af;
  background: #dbeafe;
  padding: 0.125rem 0.5rem;
  border-radius: 999px;
  text-transform: uppercase;
}

.welcome-desc {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  color: var(--atria-text-muted, #475569);
}

.welcome-badges-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.context-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.625rem;
  font-size: 0.75rem;
}

.badge-icon {
  flex-shrink: 0;
}

/* 2-Column Workspace Grid */
.portal-workspace-grid {
  display: grid;
  grid-template-columns: 390px minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.portal-left-col {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.portal-right-col {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-width: 0;
}

/* Card Header General */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.625rem;
  margin-bottom: 0.875rem;
}

.clickable-header {
  cursor: pointer;
  user-select: none;
  margin-bottom: 0.25rem;
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

.header-status-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #22c55e;
  box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.25);
  animation: pulse-live 2s infinite;
}

@keyframes pulse-live {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 6px rgba(34, 197, 94, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0);
  }
}

/* Digital Clock Display */
.digital-clock-display {
  background: linear-gradient(180deg, #0f172a, #1e293b);
  color: #f8fafc;
  border-radius: var(--atria-radius, 6px);
  padding: 1.25rem 1rem;
  text-align: center;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.3);
  margin-bottom: 0.875rem;
}

.clock-time {
  font-size: 2.25rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #38bdf8;
  text-shadow: 0 0 12px rgba(56, 189, 248, 0.4);
}

.clock-date-meta {
  margin-top: 0.25rem;
  font-size: 0.8125rem;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
}

.clock-tz-text {
  font-size: 0.75rem;
  color: #64748b;
}

/* Logical Date & Cutoff Info Box */
.logical-date-strip {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 0.625rem 0.75rem;
  margin-bottom: 0.875rem;
}

.logical-info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.5rem;
  border-bottom: 1px dashed var(--atria-border, #e2e8f0);
}

.info-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.align-right {
  align-items: flex-end;
}

.info-label {
  font-size: 0.75rem;
  color: var(--atria-text-muted, #475569);
}

.info-value {
  font-size: 0.875rem;
  color: var(--atria-text, #0f172a);
}

.day-type-wrap {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.holiday-pill {
  font-size: 0.6875rem;
  background: #fee2e2;
  color: #b91c1c;
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-weight: 600;
}

.cutoff-hint-row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: var(--atria-text-muted, #475569);
}

.hint-icon {
  color: #0284c7;
  flex-shrink: 0;
}

/* Metrics Grid */
.attendance-metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  margin-bottom: 0.875rem;
}

.metric-box {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
  padding: 0.5rem 0.625rem;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.shift-box,
.hours-box {
  grid-column: span 1;
}

.metric-label {
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #475569);
  font-weight: 500;
}

.metric-val {
  font-size: 0.8125rem;
  color: var(--atria-text, #0f172a);
}

.highlight-val {
  color: #1e40af;
  font-size: 0.875rem;
}

/* Punch Action Section */
.punch-action-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.punch-note-input {
  width: 100%;
  font-size: 0.8125rem;
}

.punch-main-btn {
  width: 100%;
  padding: 0.75rem 1rem;
  font-size: 0.95rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.btn-icon {
  flex-shrink: 0;
}

.period-closed-hint {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  background: #fef3c7;
  border: 1px solid #fde68a;
  color: #92400e;
  padding: 0.375rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
}

.closed-icon {
  flex-shrink: 0;
}

/* Today's Punch Log Timeline */
.today-logs-wrapper {
  border-top: 1px solid var(--atria-border, #e2e8f0);
  padding-top: 0.75rem;
}

.logs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.logs-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.logs-title {
  font-size: 0.8125rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.logs-count-badge {
  font-size: 0.6875rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  padding: 0.0625rem 0.375rem;
  border-radius: 999px;
  color: var(--atria-text-muted, #475569);
}

.empty-logs-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1.5rem 0.5rem;
  gap: 0.375rem;
  color: var(--atria-text-light, #64748b);
  font-size: 0.8125rem;
  border: 1px dashed var(--atria-border, #e2e8f0);
  border-radius: 4px;
  background: var(--atria-surface-subtle, #f8fafc);
}

.logs-timeline-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 180px;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.timeline-log-row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  border-radius: 4px;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
}

.timeline-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0284c7;
  margin-top: 0.375rem;
  flex-shrink: 0;
}

.timeline-log-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.log-headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.log-time {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--atria-text, #0f172a);
}

.source-pill {
  font-size: 0.625rem;
  padding: 0.0625rem 0.375rem;
}

.log-details {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #475569);
}

.log-note-text {
  color: #1e40af;
  font-style: italic;
}

/* User Security Profile Card */
.profile-card {
  display: flex;
  flex-direction: column;
}

.header-action-wrap {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.collapse-btn {
  width: 24px;
  height: 24px;
  padding: 0;
}

.profile-body {
  padding-top: 0.5rem;
}

.profile-meta-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.75rem;
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
  max-height: 120px;
  overflow-y: auto;
  padding: 0.375rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
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

/* Right Column: Calendar Card */
.calendar-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.calendar-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.75rem;
}

.month-nav-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.nav-buttons-group {
  display: inline-flex;
  align-items: center;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 0.125rem;
}

.month-arrow-btn {
  width: 28px;
  height: 28px;
  padding: 0;
}

.current-month-display {
  font-size: 0.95rem;
  font-weight: 700;
  padding: 0 0.5rem;
  color: var(--atria-text, #0f172a);
  min-width: 80px;
  text-align: center;
}

.current-month-btn {
  font-size: 0.8125rem;
}

/* Monthly KPI Strip (4 compact tiles) */
.monthly-kpi-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

.kpi-card {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.kpi-title {
  font-size: 0.75rem;
  color: var(--atria-text-muted, #475569);
  font-weight: 500;
}

.kpi-val {
  font-size: 1.15rem;
  color: var(--atria-text, #0f172a);
}

.kpi-sub {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.6875rem;
  color: var(--atria-text-light, #64748b);
  margin-top: 0.125rem;
}

.kpi-sub-icon {
  flex-shrink: 0;
}

.text-success {
  color: #15803d;
}

.text-warning {
  color: #b45309;
}

.text-danger {
  color: #b91c1c;
}

/* 7-Column Monthly Calendar Grid */
.calendar-grid-container {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  overflow: hidden;
  background: var(--atria-surface, #ffffff);
}

.calendar-week-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: #f1f5f9;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.week-header-cell {
  padding: 0.5rem 0.25rem;
  text-align: center;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--atria-text-muted, #475569);
}

.header-sun {
  color: #dc2626;
}

.header-sat {
  color: #2563eb;
}

.calendar-days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: var(--atria-border, #e2e8f0);
  gap: 1px;
}

.cal-day-cell {
  min-height: 94px;
  background: #ffffff;
  padding: 0.375rem 0.5rem;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
  position: relative;
}

.cal-day-cell:hover {
  background-color: #f8fafc;
}

.cell-outside {
  background: #f8fafc;
  opacity: 0.35;
  cursor: default;
}

.cell-outside:hover {
  background: #f8fafc;
}

.cell-today {
  box-shadow: inset 0 0 0 2px #3b82f6;
  background-color: #eff6ff;
}

.cell-rest {
  background-color: #fafaf9;
}

.cell-statutory {
  background-color: #fef2f2;
}

.cell-holiday {
  background-color: #fff1f2;
}

.cell-makeup {
  background-color: #f0fdf4;
}

.day-cell-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.25rem;
}

.day-num {
  font-size: 0.8125rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.today-circle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #2563eb;
  color: #ffffff;
}

.cell-type-tags {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  overflow: hidden;
}

.holiday-tag-text {
  font-size: 0.625rem;
  font-weight: 600;
  color: #b91c1c;
  background: #fee2e2;
  padding: 0.0625rem 0.25rem;
  border-radius: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 65px;
}

.makeup-tag-text {
  font-size: 0.625rem;
  font-weight: 600;
  color: #15803d;
  background: #dcfce7;
  padding: 0.0625rem 0.25rem;
  border-radius: 3px;
}

.day-cell-body {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  flex: 1;
}

.status-pill-wrap {
  margin-bottom: 0.125rem;
}

.mini-status-tag {
  font-size: 0.625rem !important;
  padding: 0.0625rem 0.25rem !important;
  line-height: 1.2 !important;
}

.cell-time-row {
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #475569);
  display: flex;
  align-items: center;
  gap: 0.125rem;
}

.time-sep {
  color: #94a3b8;
}

.cell-hours-row {
  font-size: 0.6875rem;
  font-weight: 600;
  color: #1e40af;
}

/* Day Detail Dialog */
.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.detail-schedule-banner {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.schedule-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-date {
  font-size: 1.1rem;
  color: var(--atria-text, #0f172a);
}

.detail-tags {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.holiday-alert {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  background: #fffbeb;
  border: 1px solid #fef3c7;
  padding: 0.375rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8125rem;
  color: #92400e;
  font-weight: 600;
}

.schedule-times-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  border-top: 1px dashed var(--atria-border, #e2e8f0);
  padding-top: 0.5rem;
}

.time-item {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.time-label {
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #475569);
}

.time-val {
  font-size: 0.8125rem;
  color: var(--atria-text, #0f172a);
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.section-heading {
  font-size: 0.875rem;
  font-weight: 700;
  margin: 0;
  color: var(--atria-text, #0f172a);
}

.detail-metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}

.d-metric-item {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.highlight-item {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.d-label {
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #475569);
}

.d-val {
  font-size: 0.8125rem;
  color: var(--atria-text, #0f172a);
}

.empty-detail-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  padding: 1.25rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px dashed var(--atria-border, #e2e8f0);
  border-radius: 4px;
  color: var(--atria-text-light, #64748b);
  font-size: 0.8125rem;
}

.detail-logs-list {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  max-height: 160px;
  overflow-y: auto;
}

.d-log-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
}

.d-log-time {
  font-size: 0.8125rem;
  color: var(--atria-text, #0f172a);
}

.d-log-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #475569);
}

.d-note {
  color: #1e40af;
  font-style: italic;
}

/* Responsiveness */
@media (max-width: 1100px) {
  .portal-workspace-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .monthly-kpi-strip {
    grid-template-columns: 1fr 1fr;
  }

  .cal-day-cell {
    min-height: 65px;
    padding: 0.25rem;
  }

  .cell-time-row,
  .cell-hours-row {
    display: none;
  }
}
</style>
