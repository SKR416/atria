<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

import { workCalendarApi } from '@/api/attendance'
import { useAuthStore } from '@/stores/auth'
import type {
  CalendarDayType,
  GenerateYearCalendarRequest,
  PunchRuleResponse,
  PunchRuleUpdateRequest,
  WorkCalendarDayResponse,
  WorkCalendarDayUpdateRequest
} from '@/types/attendance'

const { t } = useI18n()
const toast = useToast()
const authStore = useAuthStore()

const canWrite = computed<boolean>(
  () => authStore.hasPermission('att:calendar:write') || authStore.roles.includes('SUPER_ADMIN')
)

// Calendar region, year, month controls
const selectedCalendarCode = ref<'TW' | 'JP'>('TW')
const selectedYear = ref<number>(new Date().getFullYear())
const selectedMonth = ref<number>(new Date().getMonth() + 1)

const calendarCodeOptions = computed(() => [
  { label: t('regions.TW'), value: 'TW' },
  { label: t('regions.JP'), value: 'JP' }
])

const yearOptions = [
  { label: '2024', value: 2024 },
  { label: '2025', value: 2025 },
  { label: '2026', value: 2026 },
  { label: '2027', value: 2027 },
  { label: '2028', value: 2028 },
  { label: '2029', value: 2029 },
  { label: '2030', value: 2030 }
]

const monthOptions = Array.from({ length: 12 }, (_, i) => ({
  label: `${i + 1}`,
  value: i + 1
}))

// Calendar days data
const days = ref<WorkCalendarDayResponse[]>([])
const loadingDays = ref<boolean>(false)

// Punch rules data
const punchRule = ref<PunchRuleResponse | null>(null)
const loadingRule = ref<boolean>(false)
const savingRule = ref<boolean>(false)

const ruleForm = ref<PunchRuleUpdateRequest>({
  lateGraceMinutes: 10,
  earlyLeaveGraceMinutes: 0,
  clockInRoundingMinutes: 1,
  clockOutRoundingMinutes: 1,
  dayCutoffHour: 4,
  minPunchIntervalSeconds: 60,
  maxRetroactiveDays: 3,
  autoMonthlyCloseDay: 5
})

// Day editor dialog
const editDayDialogVisible = ref<boolean>(false)
const editingDate = ref<string>('')
const savingDay = ref<boolean>(false)

const dayForm = ref<{
  dayType: CalendarDayType
  workStartTime: string
  workEndTime: string
  breakStartTime: string
  breakEndTime: string
  holidayNameZh: string
  holidayNameEn: string
  holidayNameJa: string
  note: string
}>({
  dayType: 'WORKDAY',
  workStartTime: '09:00:00',
  workEndTime: '18:00:00',
  breakStartTime: '12:00:00',
  breakEndTime: '13:00:00',
  holidayNameZh: '',
  holidayNameEn: '',
  holidayNameJa: '',
  note: ''
})

const dayTypeOptions = computed(() => [
  { label: t('attendance.dayTypes.WORKDAY'), value: 'WORKDAY' },
  { label: t('attendance.dayTypes.MAKEUP_WORKDAY'), value: 'MAKEUP_WORKDAY' },
  { label: t('attendance.dayTypes.REST_DAY'), value: 'REST_DAY' },
  { label: t('attendance.dayTypes.STATUTORY_REST_DAY'), value: 'STATUTORY_REST_DAY' },
  { label: t('attendance.dayTypes.NATIONAL_HOLIDAY'), value: 'NATIONAL_HOLIDAY' }
])

// Generate Year Dialog
const generateDialogVisible = ref<boolean>(false)
const generatingYear = ref<boolean>(false)
const generateForm = ref<GenerateYearCalendarRequest>({
  calendarCode: 'TW',
  year: new Date().getFullYear(),
  overwriteExisting: false
})

// Localized weekday names
const weekdayHeaders = computed<string[]>(() => {
  const locale = authStore.activeLocale
  if (locale === 'zh-TW') {
    return ['週日', '週一', '週二', '週三', '週四', '週五', '週六']
  }
  if (locale === 'ja-JP') {
    return ['日', '月', '火', '水', '木', '金', '土']
  }
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
})

// Grid calculation
interface CalendarGridCell {
  dateStr: string
  dayNumber: number
  dayData: WorkCalendarDayResponse | null
}

const daysByDate = computed<Map<string, WorkCalendarDayResponse>>(() => {
  const map = new Map<string, WorkCalendarDayResponse>()
  for (const d of days.value) {
    map.set(d.workDate, d)
  }
  return map
})

const emptyLeadingDaysCount = computed<number>(() => {
  return new Date(selectedYear.value, selectedMonth.value - 1, 1).getDay()
})

const monthCells = computed<CalendarGridCell[]>(() => {
  const cells: CalendarGridCell[] = []
  const y = selectedYear.value
  const m = selectedMonth.value
  const lastDay = new Date(y, m, 0).getDate()
  for (let day = 1; day <= lastDay; day++) {
    const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    cells.push({
      dateStr,
      dayNumber: day,
      dayData: daysByDate.value.get(dateStr) || null
    })
  }
  return cells
})

function getHolidayTitle(day: WorkCalendarDayResponse | null): string {
  if (!day) return ''
  if (day.localizedHolidayName) {
    return day.localizedHolidayName
  }
  if (day.holidayName) {
    const loc = authStore.activeLocale
    if (day.holidayName[loc]) {
      return String(day.holidayName[loc])
    }
    if (day.holidayName['zh-TW']) {
      return String(day.holidayName['zh-TW'])
    }
  }
  return ''
}

function getDayTypeSeverity(dayType: CalendarDayType): 'info' | 'warn' | 'secondary' | 'danger' | 'contrast' {
  switch (dayType) {
    case 'WORKDAY':
      return 'info'
    case 'MAKEUP_WORKDAY':
      return 'warn'
    case 'REST_DAY':
      return 'secondary'
    case 'STATUTORY_REST_DAY':
      return 'danger'
    case 'NATIONAL_HOLIDAY':
      return 'contrast'
    default:
      return 'info'
  }
}

function prevMonth() {
  if (selectedMonth.value === 1) {
    selectedMonth.value = 12
    selectedYear.value -= 1
  } else {
    selectedMonth.value -= 1
  }
}

function nextMonth() {
  if (selectedMonth.value === 12) {
    selectedMonth.value = 1
    selectedYear.value += 1
  } else {
    selectedMonth.value += 1
  }
}

async function loadCalendarDays() {
  loadingDays.value = true
  const y = selectedYear.value
  const m = selectedMonth.value
  const lastDay = new Date(y, m, 0).getDate()
  const startDate = `${y}-${String(m).padStart(2, '0')}-01`
  const endDate = `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`

  try {
    const result = await workCalendarApi.getDays(selectedCalendarCode.value, startDate, endDate)
    days.value = result
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingDays.value = false
  }
}

async function loadPunchRule() {
  loadingRule.value = true
  try {
    const rule = await workCalendarApi.getPunchRule(selectedCalendarCode.value)
    punchRule.value = rule
    ruleForm.value = {
      lateGraceMinutes: rule.lateGraceMinutes,
      earlyLeaveGraceMinutes: rule.earlyLeaveGraceMinutes,
      clockInRoundingMinutes: rule.clockInRoundingMinutes,
      clockOutRoundingMinutes: rule.clockOutRoundingMinutes,
      dayCutoffHour: rule.dayCutoffHour,
      minPunchIntervalSeconds: rule.minPunchIntervalSeconds,
      maxRetroactiveDays: rule.maxRetroactiveDays,
      autoMonthlyCloseDay: rule.autoMonthlyCloseDay
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingRule.value = false
  }
}

async function handleSavePunchRule() {
  if (!canWrite.value) return
  savingRule.value = true
  try {
    const updated = await workCalendarApi.updatePunchRule(selectedCalendarCode.value, ruleForm.value)
    punchRule.value = updated
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('attendance.punchRule.saveRuleBtn'),
      life: 3000
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    savingRule.value = false
  }
}

function openEditDayDialog(day: WorkCalendarDayResponse | null, dateStr: string) {
  if (!canWrite.value) return
  editingDate.value = dateStr
  if (day) {
    dayForm.value = {
      dayType: day.dayType,
      workStartTime: day.workStartTime || '09:00:00',
      workEndTime: day.workEndTime || '18:00:00',
      breakStartTime: day.breakStartTime || '12:00:00',
      breakEndTime: day.breakEndTime || '13:00:00',
      holidayNameZh: String(day.holidayName?.['zh-TW'] || ''),
      holidayNameEn: String(day.holidayName?.['en-US'] || ''),
      holidayNameJa: String(day.holidayName?.['ja-JP'] || ''),
      note: day.note || ''
    }
  } else {
    // Default values for unconfigured day
    const dayOfWeek = new Date(dateStr).getDay()
    const isWeekend = dayOfWeek === 0 || dayOfWeek === 6
    dayForm.value = {
      dayType: isWeekend ? 'REST_DAY' : 'WORKDAY',
      workStartTime: '09:00:00',
      workEndTime: '18:00:00',
      breakStartTime: '12:00:00',
      breakEndTime: '13:00:00',
      holidayNameZh: '',
      holidayNameEn: '',
      holidayNameJa: '',
      note: ''
    }
  }
  editDayDialogVisible.value = true
}

async function handleSaveDay() {
  if (!canWrite.value) return
  savingDay.value = true
  try {
    const holidayZh = dayForm.value.holidayNameZh.trim()
    const holidayEn = dayForm.value.holidayNameEn.trim()
    const holidayJa = dayForm.value.holidayNameJa.trim()

    const holidayNamePayload = (holidayZh || holidayEn || holidayJa)
      ? {
          'zh-TW': holidayZh,
          'en-US': holidayEn || holidayZh,
          'ja-JP': holidayJa || holidayZh
        }
      : null

    const payload: WorkCalendarDayUpdateRequest = {
      dayType: dayForm.value.dayType,
      workStartTime: dayForm.value.workStartTime.trim() || undefined,
      workEndTime: dayForm.value.workEndTime.trim() || undefined,
      breakStartTime: dayForm.value.breakStartTime.trim() || undefined,
      breakEndTime: dayForm.value.breakEndTime.trim() || undefined,
      holidayName: holidayNamePayload,
      note: dayForm.value.note.trim() || null
    }

    await workCalendarApi.upsertDay(selectedCalendarCode.value, editingDate.value, payload)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('common.success'),
      life: 3000
    })
    editDayDialogVisible.value = false
    await loadCalendarDays()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    savingDay.value = false
  }
}

function openGenerateYearDialog() {
  generateForm.value = {
    calendarCode: selectedCalendarCode.value,
    year: selectedYear.value,
    overwriteExisting: false
  }
  generateDialogVisible.value = true
}

async function handleGenerateYear() {
  generatingYear.value = true
  try {
    await workCalendarApi.generateYear(generateForm.value)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('attendance.calendar.generateSuccess'),
      life: 3000
    })
    generateDialogVisible.value = false
    selectedCalendarCode.value = generateForm.value.calendarCode as 'TW' | 'JP'
    selectedYear.value = generateForm.value.year
    await loadCalendarDays()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    generatingYear.value = false
  }
}

watch([selectedCalendarCode, selectedYear, selectedMonth], () => {
  loadCalendarDays()
})

watch(selectedCalendarCode, () => {
  loadPunchRule()
})

onMounted(() => {
  loadCalendarDays()
  loadPunchRule()
})
</script>

<template>
  <div class="calendar-manage-view" data-testid="admin-calendar-manage-view">
    <!-- Top Header -->
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('attendance.calendar.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('attendance.calendar.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          v-if="canWrite"
          icon="pi pi-bolt"
          :label="t('attendance.calendar.generateYearBtn')"
          severity="primary"
          size="small"
          @click="openGenerateYearDialog"
        />
        <Button
          icon="pi pi-refresh"
          :label="t('common.refresh')"
          severity="secondary"
          outlined
          size="small"
          :loading="loadingDays || loadingRule"
          @click="() => { loadCalendarDays(); loadPunchRule(); }"
        />
      </div>
    </div>

    <!-- Toolbar: Region, Year, Month, Quick Nav -->
    <div class="atria-card calendar-toolbar-card">
      <div class="toolbar-left">
        <!-- Region -->
        <div class="toolbar-item">
          <label class="item-label">{{ t('attendance.calendar.regionLabel') }}</label>
          <Select
            v-model="selectedCalendarCode"
            :options="calendarCodeOptions"
            optionLabel="label"
            optionValue="value"
            class="w-36"
          />
        </div>

        <!-- Year -->
        <div class="toolbar-item">
          <label class="item-label">{{ t('attendance.calendar.yearLabel') }}</label>
          <Select
            v-model="selectedYear"
            :options="yearOptions"
            optionLabel="label"
            optionValue="value"
            class="w-28"
          />
        </div>

        <!-- Month -->
        <div class="toolbar-item">
          <label class="item-label">{{ t('attendance.calendar.monthLabel') }}</label>
          <div class="month-selector-wrap">
            <Button
              icon="pi pi-chevron-left"
              severity="secondary"
              text
              size="small"
              @click="prevMonth"
            />
            <Select
              v-model="selectedMonth"
              :options="monthOptions"
              optionLabel="label"
              optionValue="value"
              class="w-20"
            />
            <Button
              icon="pi pi-chevron-right"
              severity="secondary"
              text
              size="small"
              @click="nextMonth"
            />
          </div>
        </div>
      </div>

      <div class="toolbar-legend">
        <Tag :value="t('attendance.dayTypes.WORKDAY')" severity="info" class="legend-tag" />
        <Tag :value="t('attendance.dayTypes.MAKEUP_WORKDAY')" severity="warn" class="legend-tag" />
        <Tag :value="t('attendance.dayTypes.REST_DAY')" severity="secondary" class="legend-tag" />
        <Tag :value="t('attendance.dayTypes.STATUTORY_REST_DAY')" severity="danger" class="legend-tag" />
        <Tag :value="t('attendance.dayTypes.NATIONAL_HOLIDAY')" severity="contrast" class="legend-tag" />
      </div>
    </div>

    <!-- Monthly Calendar Grid -->
    <div class="atria-card calendar-grid-card">
      <!-- Weekday Headers -->
      <div class="calendar-grid-header">
        <div
          v-for="(header, idx) in weekdayHeaders"
          :key="idx"
          class="weekday-col-header"
          :class="{ 'weekend-header': idx === 0 || idx === 6 }"
        >
          {{ header }}
        </div>
      </div>

      <!-- Days Grid -->
      <div class="calendar-grid-body">
        <!-- Leading Empty Offset Cells -->
        <div
          v-for="emptyIdx in emptyLeadingDaysCount"
          :key="'empty-' + emptyIdx"
          class="calendar-cell calendar-cell-empty"
        />

        <!-- Date Cells -->
        <div
          v-for="cell in monthCells"
          :key="cell.dateStr"
          class="calendar-cell"
          :class="[
            cell.dayData ? `cell-type-${cell.dayData.dayType.toLowerCase()}` : 'cell-unconfigured',
            { 'cell-clickable': canWrite }
          ]"
          @click="openEditDayDialog(cell.dayData, cell.dateStr)"
        >
          <!-- Cell Header: Date Number & Badge -->
          <div class="cell-top-bar">
            <span class="cell-date-num">{{ cell.dayNumber }}</span>
            <Tag
              v-if="cell.dayData"
              :value="t('attendance.dayTypes.' + cell.dayData.dayType)"
              :severity="getDayTypeSeverity(cell.dayData.dayType)"
              class="cell-type-badge"
            />
            <span v-else class="cell-no-data-hint">-</span>
          </div>

          <!-- Holiday Name -->
          <div v-if="getHolidayTitle(cell.dayData)" class="cell-holiday-title">
            <i class="pi pi-bookmark-fill holiday-icon"></i>
            <span>{{ getHolidayTitle(cell.dayData) }}</span>
          </div>

          <!-- Shift Hours -->
          <div
            v-if="cell.dayData && cell.dayData.workStartTime && cell.dayData.workEndTime && cell.dayData.workingDay"
            class="cell-shift-hours"
          >
            <i class="pi pi-clock shift-icon"></i>
            <span>{{ cell.dayData.workStartTime.slice(0, 5) }} - {{ cell.dayData.workEndTime.slice(0, 5) }}</span>
          </div>

          <!-- Note -->
          <div v-if="cell.dayData && cell.dayData.note" class="cell-note-text" :title="cell.dayData.note">
            {{ cell.dayData.note }}
          </div>
        </div>
      </div>
    </div>

    <!-- Regional Punch & Cutoff Rule Configuration Card (att_punch_rule) -->
    <div class="atria-card punch-rule-card">
      <div class="card-section-header">
        <div>
          <h2 class="card-section-title">
            {{ t('attendance.punchRule.title') }}
            <span class="region-badge font-mono">({{ selectedCalendarCode }})</span>
          </h2>
          <p class="card-section-subtitle">
            {{ t('attendance.portal.cutoffHint', { hour: ruleForm.dayCutoffHour }) }}
          </p>
        </div>
        <Button
          v-if="canWrite"
          icon="pi pi-save"
          :label="t('attendance.punchRule.saveRuleBtn')"
          severity="primary"
          size="small"
          :loading="savingRule"
          @click="handleSavePunchRule"
        />
      </div>

      <div class="atria-form-grid rule-form-grid">
        <!-- lateGraceMinutes -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.lateGraceMinutes') }}</label>
          <InputNumber
            v-model="ruleForm.lateGraceMinutes"
            :min="0"
            :max="120"
            suffix=" min"
            :disabled="!canWrite"
          />
        </div>

        <!-- earlyLeaveGraceMinutes -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.earlyLeaveGraceMinutes') }}</label>
          <InputNumber
            v-model="ruleForm.earlyLeaveGraceMinutes"
            :min="0"
            :max="120"
            suffix=" min"
            :disabled="!canWrite"
          />
        </div>

        <!-- clockInRoundingMinutes -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.clockInRoundingMinutes') }}</label>
          <InputNumber
            v-model="ruleForm.clockInRoundingMinutes"
            :min="1"
            :max="60"
            suffix=" min"
            :disabled="!canWrite"
          />
        </div>

        <!-- clockOutRoundingMinutes -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.clockOutRoundingMinutes') }}</label>
          <InputNumber
            v-model="ruleForm.clockOutRoundingMinutes"
            :min="1"
            :max="60"
            suffix=" min"
            :disabled="!canWrite"
          />
        </div>

        <!-- dayCutoffHour -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.dayCutoffHour') }}</label>
          <InputNumber
            v-model="ruleForm.dayCutoffHour"
            :min="0"
            :max="12"
            suffix=":00 AM"
            :disabled="!canWrite"
          />
        </div>

        <!-- minPunchIntervalSeconds -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.minPunchIntervalSeconds') }}</label>
          <InputNumber
            v-model="ruleForm.minPunchIntervalSeconds"
            :min="0"
            :max="3600"
            suffix=" sec"
            :disabled="!canWrite"
          />
        </div>

        <!-- maxRetroactiveDays -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.maxRetroactiveDays') }}</label>
          <InputNumber
            v-model="ruleForm.maxRetroactiveDays"
            :min="0"
            :max="90"
            suffix=" days"
            :disabled="!canWrite"
          />
        </div>

        <!-- autoMonthlyCloseDay -->
        <div class="atria-field">
          <label>{{ t('attendance.punchRule.autoMonthlyCloseDay') }}</label>
          <InputNumber
            v-model="ruleForm.autoMonthlyCloseDay"
            :min="0"
            :max="31"
            suffix=" th"
            :disabled="!canWrite"
          />
        </div>
      </div>
    </div>

    <!-- Day Editor Dialog -->
    <Dialog
      v-model:visible="editDayDialogVisible"
      :header="t('attendance.calendar.editDayTitle', { date: editingDate })"
      :modal="true"
      :style="{ width: '560px' }"
    >
      <div class="dialog-content-form">
        <!-- Day Type -->
        <div class="atria-field">
          <label>{{ t('attendance.calendar.dayType') }}</label>
          <Select
            v-model="dayForm.dayType"
            :options="dayTypeOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </div>

        <!-- Working Hours Grid -->
        <div class="grid-2-cols">
          <div class="atria-field">
            <label>{{ t('attendance.calendar.workStartTime') }}</label>
            <InputText
              v-model="dayForm.workStartTime"
              placeholder="09:00:00"
              class="w-full font-mono"
            />
          </div>
          <div class="atria-field">
            <label>{{ t('attendance.calendar.workEndTime') }}</label>
            <InputText
              v-model="dayForm.workEndTime"
              placeholder="18:00:00"
              class="w-full font-mono"
            />
          </div>
        </div>

        <!-- Break Hours Grid -->
        <div class="grid-2-cols">
          <div class="atria-field">
            <label>{{ t('attendance.calendar.breakStartTime') }}</label>
            <InputText
              v-model="dayForm.breakStartTime"
              placeholder="12:00:00"
              class="w-full font-mono"
            />
          </div>
          <div class="atria-field">
            <label>{{ t('attendance.calendar.breakEndTime') }}</label>
            <InputText
              v-model="dayForm.breakEndTime"
              placeholder="13:00:00"
              class="w-full font-mono"
            />
          </div>
        </div>

        <!-- Trilingual Holiday Names -->
        <div class="atria-field">
          <label>{{ t('attendance.calendar.holidayNameZh') }}</label>
          <InputText
            v-model="dayForm.holidayNameZh"
            placeholder="例如：春節連假 / 國慶日"
            class="w-full"
          />
        </div>

        <div class="atria-field">
          <label>{{ t('attendance.calendar.holidayNameEn') }}</label>
          <InputText
            v-model="dayForm.holidayNameEn"
            placeholder="e.g. Lunar New Year / National Day"
            class="w-full"
          />
        </div>

        <div class="atria-field">
          <label>{{ t('attendance.calendar.holidayNameJa') }}</label>
          <InputText
            v-model="dayForm.holidayNameJa"
            placeholder="例：元日 / こどもの日"
            class="w-full"
          />
        </div>

        <!-- Note -->
        <div class="atria-field">
          <label>{{ t('attendance.calendar.note') }}</label>
          <InputText
            v-model="dayForm.note"
            placeholder="選填備註事項..."
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <Button
            :label="t('common.cancel')"
            severity="secondary"
            outlined
            @click="editDayDialogVisible = false"
          />
          <Button
            :label="t('common.save')"
            icon="pi pi-check"
            severity="primary"
            :loading="savingDay"
            @click="handleSaveDay"
          />
        </div>
      </template>
    </Dialog>

    <!-- Generate Year Template Dialog -->
    <Dialog
      v-model:visible="generateDialogVisible"
      :header="t('attendance.calendar.generateDialogTitle')"
      :modal="true"
      :style="{ width: '520px' }"
    >
      <div class="dialog-content-form">
        <!-- Region -->
        <div class="atria-field">
          <label>{{ t('attendance.calendar.regionLabel') }}</label>
          <Select
            v-model="generateForm.calendarCode"
            :options="calendarCodeOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </div>

        <!-- Year -->
        <div class="atria-field">
          <label>{{ t('attendance.calendar.yearLabel') }}</label>
          <Select
            v-model="generateForm.year"
            :options="yearOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </div>

        <!-- Overwrite checkbox -->
        <div class="checkbox-field">
          <Checkbox
            v-model="generateForm.overwriteExisting"
            :binary="true"
            inputId="overwriteCheckbox"
          />
          <label for="overwriteCheckbox" class="checkbox-label">
            {{ t('attendance.calendar.overwriteExisting') }}
          </label>
        </div>

        <!-- Template Generation Hint Box -->
        <div class="info-alert-box">
          <i class="pi pi-info-circle info-icon"></i>
          <span class="info-text">{{ t('attendance.calendar.generateConfirmHint') }}</span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <Button
            :label="t('common.cancel')"
            severity="secondary"
            outlined
            @click="generateDialogVisible = false"
          />
          <Button
            :label="t('attendance.calendar.generateYearBtn')"
            icon="pi pi-bolt"
            severity="primary"
            :loading="generatingYear"
            @click="handleGenerateYear"
          />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.calendar-manage-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* Toolbar */
.calendar-toolbar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 0.75rem 1rem;
}

.toolbar-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.toolbar-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.item-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--atria-text-muted);
}

.month-selector-wrap {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.toolbar-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.legend-tag {
  font-size: 0.72rem;
  padding: 0.125rem 0.375rem;
}

/* Calendar Grid */
.calendar-grid-card {
  padding: 0.75rem;
  overflow-x: auto;
}

.calendar-grid-header {
  display: grid;
  grid-template-columns: repeat(7, minmax(130px, 1fr));
  gap: 6px;
  margin-bottom: 6px;
}

.weekday-col-header {
  text-align: center;
  font-size: 0.8125rem;
  font-weight: 700;
  color: var(--atria-text-muted);
  padding: 0.375rem 0;
  background: var(--atria-surface-subtle);
  border-radius: var(--atria-radius);
  border: 1px solid var(--atria-border);
}

.weekend-header {
  color: var(--atria-danger);
}

.calendar-grid-body {
  display: grid;
  grid-template-columns: repeat(7, minmax(130px, 1fr));
  gap: 6px;
}

.calendar-cell {
  min-height: 104px;
  background: var(--atria-surface);
  border: 1px solid var(--atria-border);
  border-radius: var(--atria-radius);
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  transition: all 0.15s ease-in-out;
  position: relative;
}

.calendar-cell-empty {
  background: var(--atria-surface-subtle);
  border: 1px dashed var(--atria-border);
  opacity: 0.5;
}

.cell-clickable {
  cursor: pointer;
}

.cell-clickable:hover {
  border-color: var(--atria-primary);
  box-shadow: 0 2px 6px rgba(30, 64, 175, 0.12);
  transform: translateY(-1px);
}

/* Day Type Specific Styling */
.cell-type-workday {
  border-left: 3px solid #3b82f6;
}

.cell-type-makeup_workday {
  background: rgba(245, 158, 11, 0.04);
  border-left: 3px solid #f59e0b;
}

.cell-type-rest_day {
  background: rgba(100, 116, 139, 0.04);
  border-left: 3px solid #94a3b8;
}

.cell-type-statutory_rest_day {
  background: rgba(239, 68, 68, 0.05);
  border-left: 3px solid #ef4444;
}

.cell-type-national_holiday {
  background: rgba(168, 85, 247, 0.05);
  border-left: 3px solid #8b5cf6;
}

.cell-unconfigured {
  border-left: 3px solid var(--atria-border);
}

.cell-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.25rem;
}

.cell-date-num {
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--atria-text);
  line-height: 1;
}

.cell-type-badge {
  font-size: 0.68rem;
  padding: 0.1rem 0.35rem;
  line-height: 1.1;
}

.cell-no-data-hint {
  font-size: 0.75rem;
  color: var(--atria-text-light);
}

.cell-holiday-title {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--atria-danger);
  line-height: 1.2;
}

.holiday-icon {
  font-size: 0.7rem;
}

.cell-shift-hours {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.72rem;
  font-family: monospace;
  color: var(--atria-text-muted);
}

.shift-icon {
  font-size: 0.68rem;
}

.cell-note-text {
  font-size: 0.7rem;
  color: var(--atria-text-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Punch Rule Card */
.punch-rule-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.card-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--atria-border);
}

.card-section-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--atria-text);
}

.region-badge {
  color: var(--atria-primary);
}

.card-section-subtitle {
  margin: 0.2rem 0 0;
  font-size: 0.8125rem;
  color: var(--atria-text-muted);
}

.rule-form-grid {
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

/* Dialog Styling */
.dialog-content-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

.grid-2-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.checkbox-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.checkbox-label {
  font-size: 0.8125rem;
  color: var(--atria-text);
  cursor: pointer;
}

.info-alert-box {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--atria-primary-soft);
  border: 1px solid #bfdbfe;
  border-radius: var(--atria-radius);
  margin-top: 0.5rem;
}

.info-icon {
  font-size: 1rem;
  color: var(--atria-primary);
  margin-top: 0.125rem;
}

.info-text {
  font-size: 0.78rem;
  line-height: 1.45;
  color: #1e3a8a;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (max-width: 1024px) {
  .calendar-grid-header,
  .calendar-grid-body {
    grid-template-columns: repeat(7, minmax(100px, 1fr));
  }
}
</style>
