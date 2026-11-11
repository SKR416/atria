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
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'

import {
  Layers,
  Award,
  Calculator,
  ShieldAlert,
  Sparkles,
  AlertCircle,
  Calendar,
  UserCheck
} from 'lucide-vue-next'

import { leaveOvertimeRuleApi } from '@/api/attendance'
import { useAuthStore } from '@/stores/auth'
import type {
  LeaveTypeResponse,
  LeaveTypeUpsertRequest,
  LeaveGrantRuleResponse,
  LeaveGrantRuleUpsertRequest,
  LeaveGrantBasis,
  OvertimeRateRuleResponse,
  OvertimeRateRuleUpsertRequest,
  CompLeaveConversionMode,
  OvertimeCapRuleResponse,
  OvertimeCapRuleUpsertRequest,
  OvertimeCapAction,
  AnnualLeaveGrantBatchResultDto,
  ManualLeaveGrantRequest,
  CalendarDayType
} from '@/types/attendance'

type ActiveTab = 'leaveTypes' | 'grantRules' | 'overtimeRates' | 'overtimeCaps' | 'balanceOps'

const { t } = useI18n()
const toast = useToast()
const authStore = useAuthStore()

const canWrite = computed<boolean>(() => {
  return (
    authStore.hasPermission('att:rule:write') ||
    authStore.hasPermission('att:calendar:write') ||
    authStore.roles.includes('SUPER_ADMIN') ||
    authStore.roles.includes('HR_ADMIN') ||
    authStore.roles.includes('HR_MANAGER')
  )
})

const activeTab = ref<ActiveTab>('leaveTypes')

// Region filter & options
const selectedRegionFilter = ref<string>('')

const regionFilterOptions = computed(() => [
  { label: t('regions.all'), value: '' },
  { label: t('regions.TW'), value: 'TW' },
  { label: t('regions.JP'), value: 'JP' }
])

const calendarCodeOptions = computed(() => [
  { label: t('regions.TW'), value: 'TW' },
  { label: t('regions.JP'), value: 'JP' }
])

const dayTypeOptions = computed(() => [
  { label: t('attendance.dayTypes.WORKDAY'), value: 'WORKDAY' },
  { label: t('attendance.dayTypes.REST_DAY'), value: 'REST_DAY' },
  { label: t('attendance.dayTypes.STATUTORY_REST_DAY'), value: 'STATUTORY_REST_DAY' },
  { label: t('attendance.dayTypes.NATIONAL_HOLIDAY'), value: 'NATIONAL_HOLIDAY' },
  { label: t('attendance.dayTypes.MAKEUP_WORKDAY'), value: 'MAKEUP_WORKDAY' }
])

const grantBasisOptions = computed(() => [
  { label: t('attendance.grantBases.ANNIVERSARY'), value: 'ANNIVERSARY' },
  { label: t('attendance.grantBases.CALENDAR_YEAR'), value: 'CALENDAR_YEAR' }
])

const compLeaveModeOptions = computed(() => [
  { label: t('attendance.compLeaveModes.RATE_MULTIPLIED'), value: 'RATE_MULTIPLIED' },
  { label: t('attendance.compLeaveModes.HOURS_1_TO_1'), value: 'HOURS_1_TO_1' }
])

const capActionOptions = computed(() => [
  { label: t('attendance.capActions.WARN'), value: 'WARN' },
  { label: t('attendance.capActions.BLOCK'), value: 'BLOCK' }
])

function getTodayDateStr(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function getNextYearDateStr(): string {
  const d = new Date()
  const y = d.getFullYear() + 1
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// ============================================================================
// Tab 1: Leave Types
// ============================================================================
const leaveTypes = ref<LeaveTypeResponse[]>([])
const loadingLeaveTypes = ref<boolean>(false)
const leaveTypeDialogVisible = ref<boolean>(false)
const editingLeaveTypeId = ref<number | null>(null)
const savingLeaveType = ref<boolean>(false)

const leaveTypeForm = ref<{
  code: string
  nameZh: string
  nameEn: string
  nameJa: string
  minUnitMinutes: number
  paid: boolean
  balanceTracked: boolean
  attachmentRequired: boolean
  enabled: boolean
  sortOrder: number
  description: string
}>({
  code: '',
  nameZh: '',
  nameEn: '',
  nameJa: '',
  minUnitMinutes: 60,
  paid: true,
  balanceTracked: true,
  attachmentRequired: false,
  enabled: true,
  sortOrder: 10,
  description: ''
})

async function loadLeaveTypes() {
  loadingLeaveTypes.value = true
  try {
    leaveTypes.value = await leaveOvertimeRuleApi.listLeaveTypes(false)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingLeaveTypes.value = false
  }
}

function openAddLeaveTypeDialog() {
  editingLeaveTypeId.value = null
  leaveTypeForm.value = {
    code: '',
    nameZh: '',
    nameEn: '',
    nameJa: '',
    minUnitMinutes: 60,
    paid: true,
    balanceTracked: true,
    attachmentRequired: false,
    enabled: true,
    sortOrder: (leaveTypes.value.length + 1) * 10,
    description: ''
  }
  leaveTypeDialogVisible.value = true
}

function openEditLeaveTypeDialog(lt: LeaveTypeResponse) {
  editingLeaveTypeId.value = lt.id
  leaveTypeForm.value = {
    code: lt.code,
    nameZh: String(lt.name?.['zh-TW'] || lt.localizedName || ''),
    nameEn: String(lt.name?.['en-US'] || lt.localizedName || ''),
    nameJa: String(lt.name?.['ja-JP'] || lt.localizedName || ''),
    minUnitMinutes: lt.minUnitMinutes,
    paid: lt.paid,
    balanceTracked: lt.balanceTracked,
    attachmentRequired: lt.attachmentRequired,
    enabled: lt.enabled,
    sortOrder: lt.sortOrder,
    description: lt.description || ''
  }
  leaveTypeDialogVisible.value = true
}

async function handleSaveLeaveType() {
  if (!leaveTypeForm.value.code.trim() || !leaveTypeForm.value.nameZh.trim()) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }
  savingLeaveType.value = true
  try {
    const payload: LeaveTypeUpsertRequest = {
      code: leaveTypeForm.value.code.trim().toUpperCase(),
      name: {
        'zh-TW': leaveTypeForm.value.nameZh.trim(),
        'en-US': leaveTypeForm.value.nameEn.trim() || leaveTypeForm.value.nameZh.trim(),
        'ja-JP': leaveTypeForm.value.nameJa.trim() || leaveTypeForm.value.nameZh.trim()
      },
      minUnitMinutes: leaveTypeForm.value.minUnitMinutes,
      paid: leaveTypeForm.value.paid,
      balanceTracked: leaveTypeForm.value.balanceTracked,
      attachmentRequired: leaveTypeForm.value.attachmentRequired,
      enabled: leaveTypeForm.value.enabled,
      sortOrder: leaveTypeForm.value.sortOrder,
      description: leaveTypeForm.value.description.trim() || null
    }
    if (editingLeaveTypeId.value) {
      await leaveOvertimeRuleApi.updateLeaveType(editingLeaveTypeId.value, payload)
    } else {
      await leaveOvertimeRuleApi.createLeaveType(payload)
    }
    toast.add({ severity: 'success', summary: t('common.success'), detail: t('common.success'), life: 3000 })
    leaveTypeDialogVisible.value = false
    await loadLeaveTypes()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    savingLeaveType.value = false
  }
}

// ============================================================================
// Tab 2: Seniority Annual Leave Tiers
// ============================================================================
const grantRules = ref<LeaveGrantRuleResponse[]>([])
const loadingGrantRules = ref<boolean>(false)
const grantRuleDialogVisible = ref<boolean>(false)
const editingGrantRuleId = ref<number | null>(null)
const savingGrantRule = ref<boolean>(false)

const grantRuleForm = ref<{
  calendarCode: string
  leaveTypeCode: string
  grantBasis: LeaveGrantBasis
  minTenureMonths: number
  maxTenureMonths: number | null
  grantDays: number
  validMonths: number
  note: string
}>({
  calendarCode: 'TW',
  leaveTypeCode: 'ANNUAL',
  grantBasis: 'ANNIVERSARY',
  minTenureMonths: 6,
  maxTenureMonths: 12,
  grantDays: 3,
  validMonths: 12,
  note: ''
})

async function loadGrantRules() {
  loadingGrantRules.value = true
  try {
    const calendarCode = selectedRegionFilter.value || undefined
    grantRules.value = await leaveOvertimeRuleApi.listLeaveGrantRules(calendarCode)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingGrantRules.value = false
  }
}

function openAddGrantRuleDialog() {
  editingGrantRuleId.value = null
  grantRuleForm.value = {
    calendarCode: selectedRegionFilter.value || 'TW',
    leaveTypeCode: 'ANNUAL',
    grantBasis: 'ANNIVERSARY',
    minTenureMonths: 6,
    maxTenureMonths: 12,
    grantDays: 3,
    validMonths: 12,
    note: ''
  }
  grantRuleDialogVisible.value = true
}

function openEditGrantRuleDialog(gr: LeaveGrantRuleResponse) {
  editingGrantRuleId.value = gr.id
  grantRuleForm.value = {
    calendarCode: gr.calendarCode,
    leaveTypeCode: gr.leaveTypeCode,
    grantBasis: gr.grantBasis,
    minTenureMonths: gr.minTenureMonths,
    maxTenureMonths: gr.maxTenureMonths,
    grantDays: gr.grantDays,
    validMonths: gr.validMonths,
    note: gr.note || ''
  }
  grantRuleDialogVisible.value = true
}

async function handleSaveGrantRule() {
  savingGrantRule.value = true
  try {
    const payload: LeaveGrantRuleUpsertRequest = {
      calendarCode: grantRuleForm.value.calendarCode,
      leaveTypeCode: grantRuleForm.value.leaveTypeCode,
      grantBasis: grantRuleForm.value.grantBasis,
      minTenureMonths: grantRuleForm.value.minTenureMonths,
      maxTenureMonths: grantRuleForm.value.maxTenureMonths
        ? Number(grantRuleForm.value.maxTenureMonths)
        : null,
      grantDays: grantRuleForm.value.grantDays,
      validMonths: grantRuleForm.value.validMonths,
      note: grantRuleForm.value.note.trim() || null
    }
    if (editingGrantRuleId.value) {
      await leaveOvertimeRuleApi.updateLeaveGrantRule(editingGrantRuleId.value, payload)
    } else {
      await leaveOvertimeRuleApi.createLeaveGrantRule(payload)
    }
    toast.add({ severity: 'success', summary: t('common.success'), detail: t('common.success'), life: 3000 })
    grantRuleDialogVisible.value = false
    await loadGrantRules()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    savingGrantRule.value = false
  }
}

// ============================================================================
// Tab 3: Overtime Rate Rules
// ============================================================================
const rateRules = ref<OvertimeRateRuleResponse[]>([])
const loadingRateRules = ref<boolean>(false)
const rateRuleDialogVisible = ref<boolean>(false)
const editingRateRuleId = ref<number | null>(null)
const savingRateRule = ref<boolean>(false)

const rateRuleForm = ref<{
  calendarCode: string
  dayType: CalendarDayType
  fromMinute: number
  toMinute: number
  multiplier: number
  defaultCompLeaveMode: CompLeaveConversionMode
  nightPremium: boolean
  monthlyCumulative: boolean
  description: string
}>({
  calendarCode: 'TW',
  dayType: 'WORKDAY',
  fromMinute: 0,
  toMinute: 120,
  multiplier: 1.34,
  defaultCompLeaveMode: 'RATE_MULTIPLIED',
  nightPremium: false,
  monthlyCumulative: false,
  description: ''
})

async function loadRateRules() {
  loadingRateRules.value = true
  try {
    const calendarCode = selectedRegionFilter.value || undefined
    rateRules.value = await leaveOvertimeRuleApi.listOvertimeRateRules(calendarCode)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingRateRules.value = false
  }
}

function openAddRateRuleDialog() {
  editingRateRuleId.value = null
  rateRuleForm.value = {
    calendarCode: selectedRegionFilter.value || 'TW',
    dayType: 'WORKDAY',
    fromMinute: 0,
    toMinute: 120,
    multiplier: 1.34,
    defaultCompLeaveMode: 'RATE_MULTIPLIED',
    nightPremium: false,
    monthlyCumulative: false,
    description: ''
  }
  rateRuleDialogVisible.value = true
}

function openEditRateRuleDialog(rr: OvertimeRateRuleResponse) {
  editingRateRuleId.value = rr.id
  rateRuleForm.value = {
    calendarCode: rr.calendarCode,
    dayType: rr.dayType,
    fromMinute: rr.fromMinute,
    toMinute: rr.toMinute,
    multiplier: rr.multiplier,
    defaultCompLeaveMode: rr.defaultCompLeaveMode,
    nightPremium: rr.nightPremium,
    monthlyCumulative: rr.monthlyCumulative,
    description: rr.description || ''
  }
  rateRuleDialogVisible.value = true
}

async function handleSaveRateRule() {
  savingRateRule.value = true
  try {
    const payload: OvertimeRateRuleUpsertRequest = {
      calendarCode: rateRuleForm.value.calendarCode,
      dayType: rateRuleForm.value.dayType,
      fromMinute: rateRuleForm.value.fromMinute,
      toMinute: rateRuleForm.value.toMinute,
      multiplier: rateRuleForm.value.multiplier,
      defaultCompLeaveMode: rateRuleForm.value.defaultCompLeaveMode,
      nightPremium: rateRuleForm.value.nightPremium,
      monthlyCumulative: rateRuleForm.value.monthlyCumulative,
      description: rateRuleForm.value.description.trim() || null
    }
    if (editingRateRuleId.value) {
      await leaveOvertimeRuleApi.updateOvertimeRateRule(editingRateRuleId.value, payload)
    } else {
      await leaveOvertimeRuleApi.createOvertimeRateRule(payload)
    }
    toast.add({ severity: 'success', summary: t('common.success'), detail: t('common.success'), life: 3000 })
    rateRuleDialogVisible.value = false
    await loadRateRules()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    savingRateRule.value = false
  }
}

// ============================================================================
// Tab 4: Overtime Cap Rules
// ============================================================================
const capRules = ref<OvertimeCapRuleResponse[]>([])
const loadingCapRules = ref<boolean>(false)
const capRuleDialogVisible = ref<boolean>(false)
const editingCapRuleId = ref<number | null>(null)
const savingCapRule = ref<boolean>(false)

const capRuleForm = ref<{
  calendarCode: string
  ruleCode: string
  nameZh: string
  nameEn: string
  nameJa: string
  periodMonths: number
  maxMinutes: number
  enforcementAction: OvertimeCapAction
  enabled: boolean
  description: string
}>({
  calendarCode: 'TW',
  ruleCode: 'TW_MONTHLY_CAP_46H',
  nameZh: '台灣單月上限 46 小時 (WARN)',
  nameEn: 'Taiwan Monthly Cap 46h (WARN)',
  nameJa: '台湾単月上限 46時間 (WARN)',
  periodMonths: 1,
  maxMinutes: 2760,
  enforcementAction: 'WARN',
  enabled: true,
  description: ''
})

async function loadCapRules() {
  loadingCapRules.value = true
  try {
    const calendarCode = selectedRegionFilter.value || undefined
    capRules.value = await leaveOvertimeRuleApi.listOvertimeCapRules(calendarCode)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingCapRules.value = false
  }
}

function openAddCapRuleDialog() {
  editingCapRuleId.value = null
  capRuleForm.value = {
    calendarCode: selectedRegionFilter.value || 'TW',
    ruleCode: '',
    nameZh: '',
    nameEn: '',
    nameJa: '',
    periodMonths: 1,
    maxMinutes: 2760,
    enforcementAction: 'WARN',
    enabled: true,
    description: ''
  }
  capRuleDialogVisible.value = true
}

function openEditCapRuleDialog(cr: OvertimeCapRuleResponse) {
  editingCapRuleId.value = cr.id
  capRuleForm.value = {
    calendarCode: cr.calendarCode,
    ruleCode: cr.ruleCode,
    nameZh: String(cr.name?.['zh-TW'] || cr.localizedName || ''),
    nameEn: String(cr.name?.['en-US'] || cr.localizedName || ''),
    nameJa: String(cr.name?.['ja-JP'] || cr.localizedName || ''),
    periodMonths: cr.periodMonths,
    maxMinutes: cr.maxMinutes,
    enforcementAction: cr.enforcementAction,
    enabled: cr.enabled,
    description: cr.description || ''
  }
  capRuleDialogVisible.value = true
}

async function handleSaveCapRule() {
  if (!capRuleForm.value.ruleCode.trim() || !capRuleForm.value.nameZh.trim()) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }
  savingCapRule.value = true
  try {
    const payload: OvertimeCapRuleUpsertRequest = {
      calendarCode: capRuleForm.value.calendarCode,
      ruleCode: capRuleForm.value.ruleCode.trim().toUpperCase(),
      name: {
        'zh-TW': capRuleForm.value.nameZh.trim(),
        'en-US': capRuleForm.value.nameEn.trim() || capRuleForm.value.nameZh.trim(),
        'ja-JP': capRuleForm.value.nameJa.trim() || capRuleForm.value.nameZh.trim()
      },
      periodMonths: capRuleForm.value.periodMonths,
      maxMinutes: capRuleForm.value.maxMinutes,
      enforcementAction: capRuleForm.value.enforcementAction,
      enabled: capRuleForm.value.enabled,
      description: capRuleForm.value.description.trim() || null
    }
    if (editingCapRuleId.value) {
      await leaveOvertimeRuleApi.updateOvertimeCapRule(editingCapRuleId.value, payload)
    } else {
      await leaveOvertimeRuleApi.createOvertimeCapRule(payload)
    }
    toast.add({ severity: 'success', summary: t('common.success'), detail: t('common.success'), life: 3000 })
    capRuleDialogVisible.value = false
    await loadCapRules()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    savingCapRule.value = false
  }
}

// ============================================================================
// Tab 5: Annual Grant Batch & Manual Leave Grant
// ============================================================================
const asOfDate = ref<string>(getTodayDateStr())
const runningAnnualGrant = ref<boolean>(false)
const annualGrantResult = ref<AnnualLeaveGrantBatchResultDto | null>(null)

const manualGrantForm = ref<{
  userId: number
  leaveTypeCode: string
  grantMinutes: number
  validFrom: string
  validUntil: string
  note: string
}>({
  userId: 1,
  leaveTypeCode: 'ANNUAL',
  grantMinutes: 480,
  validFrom: getTodayDateStr(),
  validUntil: getNextYearDateStr(),
  note: ''
})
const submittingManualGrant = ref<boolean>(false)

async function handleRunAnnualGrant() {
  if (!canWrite.value) return
  runningAnnualGrant.value = true
  try {
    annualGrantResult.value = await leaveOvertimeRuleApi.runAnnualLeaveGrant(asOfDate.value)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: `${t('attendance.leaveOvertime.runAnnualGrantBtn')}: ${annualGrantResult.value.grantedCount}`,
      life: 4000
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    runningAnnualGrant.value = false
  }
}

async function handleSubmitManualGrant() {
  if (!canWrite.value) return
  if (
    !manualGrantForm.value.userId ||
    !manualGrantForm.value.leaveTypeCode ||
    !manualGrantForm.value.grantMinutes ||
    !manualGrantForm.value.validFrom ||
    !manualGrantForm.value.validUntil
  ) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }
  submittingManualGrant.value = true
  try {
    const payload: ManualLeaveGrantRequest = {
      userId: manualGrantForm.value.userId,
      leaveTypeCode: manualGrantForm.value.leaveTypeCode,
      grantMinutes: manualGrantForm.value.grantMinutes,
      validFrom: manualGrantForm.value.validFrom,
      validUntil: manualGrantForm.value.validUntil,
      note: manualGrantForm.value.note.trim() || null
    }
    await leaveOvertimeRuleApi.manualGrantLeave(payload)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('attendance.leaveOvertime.manualGrantBtn'),
      life: 3000
    })
    manualGrantForm.value.note = ''
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingManualGrant.value = false
  }
}

// Watchers & Lifecycle
watch(selectedRegionFilter, () => {
  if (activeTab.value === 'grantRules') loadGrantRules()
  if (activeTab.value === 'overtimeRates') loadRateRules()
  if (activeTab.value === 'overtimeCaps') loadCapRules()
})

watch(activeTab, (tab) => {
  if (tab === 'leaveTypes') loadLeaveTypes()
  else if (tab === 'grantRules') loadGrantRules()
  else if (tab === 'overtimeRates') loadRateRules()
  else if (tab === 'overtimeCaps') loadCapRules()
})

onMounted(() => {
  loadLeaveTypes()
})
</script>

<template>
  <div class="leave-overtime-manage-view">
    <!-- Top Header -->
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('attendance.leaveOvertime.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('attendance.leaveOvertime.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          icon="pi pi-refresh"
          :label="t('common.refresh')"
          severity="secondary"
          outlined
          size="small"
          @click="() => {
            if (activeTab === 'leaveTypes') loadLeaveTypes()
            else if (activeTab === 'grantRules') loadGrantRules()
            else if (activeTab === 'overtimeRates') loadRateRules()
            else if (activeTab === 'overtimeCaps') loadCapRules()
          }"
        />
      </div>
    </div>

    <!-- Navigation Tabs Bar -->
    <div class="tabs-nav-bar">
      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'leaveTypes' }"
        data-testid="tab-leave-types"
        @click="activeTab = 'leaveTypes'"
      >
        <Layers :size="15" />
        <span>{{ t('attendance.leaveOvertime.tabLeaveTypes') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'grantRules' }"
        data-testid="tab-grant-rules"
        @click="activeTab = 'grantRules'"
      >
        <Award :size="15" />
        <span>{{ t('attendance.leaveOvertime.tabGrantRules') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'overtimeRates' }"
        data-testid="tab-overtime-rates"
        @click="activeTab = 'overtimeRates'"
      >
        <Calculator :size="15" />
        <span>{{ t('attendance.leaveOvertime.tabOvertimeRates') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'overtimeCaps' }"
        data-testid="tab-overtime-caps"
        @click="activeTab = 'overtimeCaps'"
      >
        <ShieldAlert :size="15" />
        <span>{{ t('attendance.leaveOvertime.tabOvertimeCaps') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'balanceOps' }"
        data-testid="tab-balance-ops"
        @click="activeTab = 'balanceOps'"
      >
        <Sparkles :size="15" />
        <span>{{ t('attendance.leaveOvertime.tabBalanceOps') }}</span>
      </button>
    </div>

    <!-- Tab 1: Leave Types -->
    <section v-if="activeTab === 'leaveTypes'" class="atria-card tab-content-card">
      <div class="tab-toolbar">
        <div class="toolbar-title-wrap">
          <Layers :size="18" class="text-primary" />
          <h2 class="toolbar-heading">{{ t('attendance.leaveOvertime.tabLeaveTypes') }}</h2>
        </div>
        <Button
          v-if="canWrite"
          size="small"
          severity="primary"
          icon="pi pi-plus"
          :label="t('attendance.leaveOvertime.addLeaveType')"
          data-testid="add-leave-type-btn"
          @click="openAddLeaveTypeDialog"
        />
      </div>

      <div class="table-container">
        <table class="atria-table rule-table">
          <thead>
            <tr>
              <th style="width: 120px">{{ t('attendance.leaveOvertime.leaveCode') }}</th>
              <th>{{ t('common.name') }}</th>
              <th style="width: 130px">{{ t('attendance.leaveOvertime.minUnitMinutes') }}</th>
              <th style="width: 100px">{{ t('attendance.leaveOvertime.paid') }}</th>
              <th style="width: 130px">{{ t('attendance.leaveOvertime.balanceTracked') }}</th>
              <th style="width: 120px">{{ t('attendance.leaveOvertime.attachmentRequired') }}</th>
              <th style="width: 90px">{{ t('common.status') }}</th>
              <th style="width: 90px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="lt in leaveTypes" :key="lt.id">
              <td class="font-mono font-bold">{{ lt.code }}</td>
              <td>
                <div class="type-name-cell">
                  <span class="font-bold">{{ lt.localizedName }}</span>
                  <span v-if="lt.description" class="type-desc text-muted">{{ lt.description }}</span>
                </div>
              </td>
              <td class="font-mono">{{ lt.minUnitMinutes }}m ({{ (lt.minUnitMinutes / 60).toFixed(1) }}h)</td>
              <td>
                <Tag
                  :value="lt.paid ? t('common.yes') : t('common.no')"
                  :severity="lt.paid ? 'success' : 'secondary'"
                />
              </td>
              <td>
                <Tag
                  :value="lt.balanceTracked ? t('common.yes') : t('common.no')"
                  :severity="lt.balanceTracked ? 'info' : 'secondary'"
                />
              </td>
              <td>
                <Tag
                  :value="lt.attachmentRequired ? t('common.yes') : t('common.no')"
                  :severity="lt.attachmentRequired ? 'warn' : 'secondary'"
                />
              </td>
              <td>
                <Tag
                  :value="lt.enabled ? t('common.enabled') : t('common.disabled')"
                  :severity="lt.enabled ? 'success' : 'danger'"
                />
              </td>
              <td>
                <Button
                  v-if="canWrite"
                  size="small"
                  text
                  icon="pi pi-pencil"
                  :title="t('attendance.leaveOvertime.editLeaveType')"
                  @click="openEditLeaveTypeDialog(lt)"
                />
              </td>
            </tr>
            <tr v-if="leaveTypes.length === 0">
              <td colspan="8" class="text-center text-muted empty-cell">
                {{ loadingLeaveTypes ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Tab 2: Seniority Annual Leave Tiers -->
    <section v-else-if="activeTab === 'grantRules'" class="atria-card tab-content-card">
      <div class="tab-toolbar">
        <div class="toolbar-left">
          <div class="toolbar-item">
            <label class="item-label">{{ t('attendance.calendar.regionLabel') }}</label>
            <Select
              v-model="selectedRegionFilter"
              :options="regionFilterOptions"
              optionLabel="label"
              optionValue="value"
              class="w-40"
            />
          </div>
        </div>
        <Button
          v-if="canWrite"
          size="small"
          severity="primary"
          icon="pi pi-plus"
          :label="t('attendance.leaveOvertime.addGrantRule')"
          data-testid="add-grant-rule-btn"
          @click="openAddGrantRuleDialog"
        />
      </div>

      <div class="table-container">
        <table class="atria-table rule-table">
          <thead>
            <tr>
              <th style="width: 80px">{{ t('attendance.calendar.regionLabel') }}</th>
              <th style="width: 110px">{{ t('attendance.requests.leaveType') }}</th>
              <th style="width: 140px">{{ t('attendance.leaveOvertime.grantBasis') }}</th>
              <th style="width: 170px">{{ t('attendance.leaveOvertime.minTenureMonths') }} - {{ t('attendance.leaveOvertime.maxTenureMonths') }}</th>
              <th style="width: 130px">{{ t('attendance.leaveOvertime.grantDays') }} ({{ t('attendance.leaveOvertime.grantMinutes') }})</th>
              <th style="width: 110px">{{ t('attendance.leaveOvertime.validMonths') }}</th>
              <th>{{ t('attendance.calendar.note') }}</th>
              <th style="width: 80px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="gr in grantRules" :key="gr.id">
              <td>
                <span class="atria-badge" :class="gr.calendarCode === 'JP' ? 'atria-badge-danger' : 'atria-badge-info'">
                  {{ gr.calendarCode }}
                </span>
              </td>
              <td class="font-bold">{{ gr.leaveTypeCode }}</td>
              <td>{{ t('attendance.grantBases.' + gr.grantBasis) }}</td>
              <td class="font-mono">
                {{ gr.minTenureMonths }} ~ {{ gr.maxTenureMonths ?? '∞' }} {{ t('attendance.leaveOvertime.monthsUnit') }}
              </td>
              <td class="font-mono font-bold">
                {{ gr.grantDays }} {{ t('attendance.leaveOvertime.daysUnit') }} ({{ gr.grantMinutes }}m)
              </td>
              <td class="font-mono">{{ gr.validMonths }} {{ t('attendance.leaveOvertime.monthsUnit') }}</td>
              <td class="text-muted">{{ gr.note || '—' }}</td>
              <td>
                <Button
                  v-if="canWrite"
                  size="small"
                  text
                  icon="pi pi-pencil"
                  :title="t('attendance.leaveOvertime.editGrantRule')"
                  @click="openEditGrantRuleDialog(gr)"
                />
              </td>
            </tr>
            <tr v-if="grantRules.length === 0">
              <td colspan="8" class="text-center text-muted empty-cell">
                {{ loadingGrantRules ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Tab 3: Overtime Rate Rules -->
    <section v-else-if="activeTab === 'overtimeRates'" class="atria-card tab-content-card">
      <div class="tab-toolbar">
        <div class="toolbar-left">
          <div class="toolbar-item">
            <label class="item-label">{{ t('attendance.calendar.regionLabel') }}</label>
            <Select
              v-model="selectedRegionFilter"
              :options="regionFilterOptions"
              optionLabel="label"
              optionValue="value"
              class="w-40"
            />
          </div>
        </div>
        <Button
          v-if="canWrite"
          size="small"
          severity="primary"
          icon="pi pi-plus"
          :label="t('attendance.leaveOvertime.addRateRule')"
          data-testid="add-rate-rule-btn"
          @click="openAddRateRuleDialog"
        />
      </div>

      <div class="table-container">
        <table class="atria-table rule-table">
          <thead>
            <tr>
              <th style="width: 80px">{{ t('attendance.calendar.regionLabel') }}</th>
              <th style="width: 140px">{{ t('attendance.calendar.dayType') }}</th>
              <th style="width: 150px">{{ t('attendance.leaveOvertime.fromMinute') }} - {{ t('attendance.leaveOvertime.toMinute') }}</th>
              <th style="width: 120px">{{ t('attendance.leaveOvertime.multiplier') }}</th>
              <th style="width: 180px">{{ t('attendance.leaveOvertime.defaultCompMode') }}</th>
              <th style="width: 110px">{{ t('attendance.leaveOvertime.nightPremium') }}</th>
              <th style="width: 110px">{{ t('attendance.leaveOvertime.monthlyCumulative') }}</th>
              <th>{{ t('common.description') }}</th>
              <th style="width: 80px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rr in rateRules" :key="rr.id">
              <td>
                <span class="atria-badge" :class="rr.calendarCode === 'JP' ? 'atria-badge-danger' : 'atria-badge-info'">
                  {{ rr.calendarCode }}
                </span>
              </td>
              <td>{{ t('attendance.dayTypes.' + rr.dayType) }}</td>
              <td class="font-mono">{{ rr.fromMinute }}m ~ {{ rr.toMinute }}m</td>
              <td class="font-mono font-bold text-primary">{{ Number(rr.multiplier).toFixed(2) }}x</td>
              <td>{{ t('attendance.compLeaveModes.' + rr.defaultCompLeaveMode) }}</td>
              <td>
                <Tag
                  :value="rr.nightPremium ? t('common.yes') : t('common.no')"
                  :severity="rr.nightPremium ? 'warn' : 'secondary'"
                />
              </td>
              <td>
                <Tag
                  :value="rr.monthlyCumulative ? t('common.yes') : t('common.no')"
                  :severity="rr.monthlyCumulative ? 'info' : 'secondary'"
                />
              </td>
              <td class="text-muted">{{ rr.description || '—' }}</td>
              <td>
                <Button
                  v-if="canWrite"
                  size="small"
                  text
                  icon="pi pi-pencil"
                  :title="t('attendance.leaveOvertime.editRateRule')"
                  @click="openEditRateRuleDialog(rr)"
                />
              </td>
            </tr>
            <tr v-if="rateRules.length === 0">
              <td colspan="9" class="text-center text-muted empty-cell">
                {{ loadingRateRules ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Tab 4: Overtime Cap Rules -->
    <section v-else-if="activeTab === 'overtimeCaps'" class="atria-card tab-content-card">
      <div class="tab-toolbar">
        <div class="toolbar-left">
          <div class="toolbar-item">
            <label class="item-label">{{ t('attendance.calendar.regionLabel') }}</label>
            <Select
              v-model="selectedRegionFilter"
              :options="regionFilterOptions"
              optionLabel="label"
              optionValue="value"
              class="w-40"
            />
          </div>
        </div>
        <Button
          v-if="canWrite"
          size="small"
          severity="primary"
          icon="pi pi-plus"
          :label="t('attendance.leaveOvertime.addCapRule')"
          data-testid="add-cap-rule-btn"
          @click="openAddCapRuleDialog"
        />
      </div>

      <div class="table-container">
        <table class="atria-table rule-table">
          <thead>
            <tr>
              <th style="width: 80px">{{ t('attendance.calendar.regionLabel') }}</th>
              <th style="width: 160px">{{ t('attendance.leaveOvertime.ruleCode') }}</th>
              <th>{{ t('common.name') }}</th>
              <th style="width: 120px">{{ t('attendance.leaveOvertime.periodMonths') }}</th>
              <th style="width: 140px">{{ t('attendance.leaveOvertime.maxMinutes') }}</th>
              <th style="width: 120px">{{ t('attendance.leaveOvertime.enforcementAction') }}</th>
              <th style="width: 90px">{{ t('common.status') }}</th>
              <th style="width: 80px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cr in capRules" :key="cr.id">
              <td>
                <span class="atria-badge" :class="cr.calendarCode === 'JP' ? 'atria-badge-danger' : 'atria-badge-info'">
                  {{ cr.calendarCode }}
                </span>
              </td>
              <td class="font-mono font-bold">{{ cr.ruleCode }}</td>
              <td>
                <div>{{ cr.localizedName }}</div>
                <div v-if="cr.description" class="text-muted text-xs">{{ cr.description }}</div>
              </td>
              <td class="font-mono">{{ cr.periodMonths }} M</td>
              <td class="font-mono font-bold">{{ cr.maxMinutes }}m ({{ (cr.maxMinutes / 60).toFixed(0) }}h)</td>
              <td>
                <Tag
                  :value="t('attendance.capActions.' + cr.enforcementAction)"
                  :severity="cr.enforcementAction === 'BLOCK' ? 'danger' : 'warn'"
                />
              </td>
              <td>
                <Tag
                  :value="cr.enabled ? t('common.enabled') : t('common.disabled')"
                  :severity="cr.enabled ? 'success' : 'danger'"
                />
              </td>
              <td>
                <Button
                  v-if="canWrite"
                  size="small"
                  text
                  icon="pi pi-pencil"
                  :title="t('attendance.leaveOvertime.editCapRule')"
                  @click="openEditCapRuleDialog(cr)"
                />
              </td>
            </tr>
            <tr v-if="capRules.length === 0">
              <td colspan="8" class="text-center text-muted empty-cell">
                {{ loadingCapRules ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Tab 5: Annual Grant Batch & Manual Leave Grant -->
    <section v-else-if="activeTab === 'balanceOps'" class="tab-balance-ops-container">
      <!-- Card 1: Run Anniversary Annual Leave Auto-Grant -->
      <div class="atria-card ops-card">
        <div class="ops-header">
          <Calendar :size="20" class="ops-icon text-primary" />
          <div>
            <h3 class="ops-title">{{ t('attendance.leaveOvertime.runAnnualGrantTitle') }}</h3>
            <p class="ops-desc text-muted">{{ t('attendance.leaveOvertime.runAnnualGrantDesc') }}</p>
          </div>
        </div>

        <div class="ops-form-inline">
          <div class="form-item">
            <label class="item-label">{{ t('attendance.leaveOvertime.asOfDateLabel') }}</label>
            <input
              v-model="asOfDate"
              type="date"
              class="atria-native-input w-44"
            />
          </div>
          <Button
            v-if="canWrite"
            icon="pi pi-play"
            severity="primary"
            size="small"
            :loading="runningAnnualGrant"
            :label="t('attendance.leaveOvertime.runAnnualGrantBtn')"
            data-testid="run-annual-grant-btn"
            @click="handleRunAnnualGrant"
          />
        </div>

        <!-- Result Box -->
        <div v-if="annualGrantResult" class="grant-result-box">
          <div class="result-summary-row">
            <div class="result-kpi">
              <span class="kpi-label font-bold text-success">{{ t('attendance.leaveOvertime.grantedCountLabel') }}</span>
              <span class="kpi-num font-mono font-bold">{{ annualGrantResult.grantedCount }}</span>
            </div>
            <div class="result-kpi">
              <span class="kpi-label font-bold text-warning">{{ t('attendance.leaveOvertime.skippedGrantedCountLabel') }}</span>
              <span class="kpi-num font-mono font-bold">{{ annualGrantResult.skippedAlreadyGrantedCount }}</span>
            </div>
          </div>
          <div v-if="annualGrantResult.skippedMissingHireDateUsernames?.length" class="missing-hire-alert">
            <AlertCircle :size="15" class="text-warning flex-shrink-0" />
            <span class="text-xs">
              {{ t('attendance.leaveOvertime.skippedMissingHireDateLabel') }} {{ annualGrantResult.skippedMissingHireDateUsernames.join(', ') }}
            </span>
          </div>
        </div>
      </div>

      <!-- Card 2: HR Manual Leave Grant Form -->
      <div class="atria-card ops-card">
        <div class="ops-header">
          <UserCheck :size="20" class="ops-icon text-primary" />
          <div>
            <h3 class="ops-title">{{ t('attendance.leaveOvertime.manualGrantTitle') }}</h3>
            <p class="ops-desc text-muted">{{ t('attendance.leaveOvertime.manualGrantDesc') }}</p>
          </div>
        </div>

        <div class="ops-form-grid">
          <div class="form-item">
            <label class="item-label">{{ t('attendance.leaveOvertime.targetUserId') }}</label>
            <InputNumber
              v-model="manualGrantForm.userId"
              :min="1"
              class="w-full"
            />
          </div>

          <div class="form-item">
            <label class="item-label">{{ t('attendance.requests.leaveType') }}</label>
            <Select
              v-model="manualGrantForm.leaveTypeCode"
              :options="leaveTypes.filter(lt => lt.balanceTracked)"
              optionLabel="localizedName"
              optionValue="code"
              class="w-full"
            />
          </div>

          <div class="form-item">
            <label class="item-label">{{ t('attendance.leaveOvertime.grantMinutesLabel') }}</label>
            <InputNumber
              v-model="manualGrantForm.grantMinutes"
              :step="60"
              class="w-full"
            />
          </div>

          <div class="form-item">
            <label class="item-label">{{ t('attendance.leaveOvertime.validFromLabel') }}</label>
            <input
              v-model="manualGrantForm.validFrom"
              type="date"
              class="atria-native-input"
              required
            />
          </div>

          <div class="form-item">
            <label class="item-label">{{ t('attendance.leaveOvertime.validUntilLabel') }}</label>
            <input
              v-model="manualGrantForm.validUntil"
              type="date"
              class="atria-native-input"
              required
            />
          </div>

          <div class="form-item form-item-full">
            <label class="item-label">{{ t('attendance.calendar.note') }}</label>
            <InputText
              v-model="manualGrantForm.note"
              placeholder="e.g. Special board-approved bonus leave"
              class="w-full"
            />
          </div>
        </div>

        <div class="ops-footer">
          <Button
            v-if="canWrite"
            icon="pi pi-check"
            severity="primary"
            size="small"
            :loading="submittingManualGrant"
            :label="t('attendance.leaveOvertime.manualGrantBtn')"
            data-testid="submit-manual-grant-btn"
            @click="handleSubmitManualGrant"
          />
        </div>
      </div>
    </section>

    <!-- Dialog 1: Leave Type Create/Edit -->
    <Dialog
      v-model:visible="leaveTypeDialogVisible"
      modal
      :header="editingLeaveTypeId ? t('attendance.leaveOvertime.editLeaveType') : t('attendance.leaveOvertime.addLeaveType')"
      :style="{ width: '540px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.leaveCode') }} *</label>
            <InputText
              v-model="leaveTypeForm.code"
              :disabled="!!editingLeaveTypeId"
              placeholder="e.g. ANNUAL, SICK"
              class="w-full font-mono uppercase"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.minUnitMinutes') }} *</label>
            <InputNumber
              v-model="leaveTypeForm.minUnitMinutes"
              :min="1"
              :step="30"
              class="w-full"
            />
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('common.name') }} (zh-TW) *</label>
          <InputText
            v-model="leaveTypeForm.nameZh"
            placeholder="特別休假"
            class="w-full"
          />
        </div>

        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('common.name') }} (en-US)</label>
            <InputText
              v-model="leaveTypeForm.nameEn"
              placeholder="Annual Leave"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('common.name') }} (ja-JP)</label>
            <InputText
              v-model="leaveTypeForm.nameJa"
              placeholder="年次有給休暇"
              class="w-full"
            />
          </div>
        </div>

        <div class="dialog-checkbox-grid">
          <div class="checkbox-item">
            <Checkbox v-model="leaveTypeForm.paid" binary inputId="lt-paid" />
            <label for="lt-paid">{{ t('attendance.leaveOvertime.paid') }}</label>
          </div>
          <div class="checkbox-item">
            <Checkbox v-model="leaveTypeForm.balanceTracked" binary inputId="lt-tracked" />
            <label for="lt-tracked">{{ t('attendance.leaveOvertime.balanceTracked') }}</label>
          </div>
          <div class="checkbox-item">
            <Checkbox v-model="leaveTypeForm.attachmentRequired" binary inputId="lt-att" />
            <label for="lt-att">{{ t('attendance.leaveOvertime.attachmentRequired') }}</label>
          </div>
          <div class="checkbox-item">
            <Checkbox v-model="leaveTypeForm.enabled" binary inputId="lt-en" />
            <label for="lt-en">{{ t('common.enabled') }}</label>
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('common.description') }}</label>
          <Textarea
            v-model="leaveTypeForm.description"
            rows="2"
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="leaveTypeDialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="savingLeaveType"
          @click="handleSaveLeaveType"
        />
      </template>
    </Dialog>

    <!-- Dialog 2: Seniority Grant Rule Create/Edit -->
    <Dialog
      v-model:visible="grantRuleDialogVisible"
      modal
      :header="editingGrantRuleId ? t('attendance.leaveOvertime.editGrantRule') : t('attendance.leaveOvertime.addGrantRule')"
      :style="{ width: '540px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.calendar.regionLabel') }} *</label>
            <Select
              v-model="grantRuleForm.calendarCode"
              :options="calendarCodeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.requests.leaveType') }} *</label>
            <InputText
              v-model="grantRuleForm.leaveTypeCode"
              class="w-full font-mono uppercase"
            />
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('attendance.leaveOvertime.grantBasis') }} *</label>
          <Select
            v-model="grantRuleForm.grantBasis"
            :options="grantBasisOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </div>

        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.minTenureMonths') }} *</label>
            <InputNumber
              v-model="grantRuleForm.minTenureMonths"
              :min="0"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.maxTenureMonths') }}</label>
            <InputNumber
              v-model="grantRuleForm.maxTenureMonths"
              :min="1"
              placeholder="Leave empty for open tier"
              class="w-full"
            />
          </div>
        </div>

        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.grantDays') }} *</label>
            <InputNumber
              v-model="grantRuleForm.grantDays"
              :min="1"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.validMonths') }} *</label>
            <InputNumber
              v-model="grantRuleForm.validMonths"
              :min="1"
              class="w-full"
            />
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('attendance.calendar.note') }}</label>
          <InputText
            v-model="grantRuleForm.note"
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="grantRuleDialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="savingGrantRule"
          @click="handleSaveGrantRule"
        />
      </template>
    </Dialog>

    <!-- Dialog 3: Overtime Rate Rule Create/Edit -->
    <Dialog
      v-model:visible="rateRuleDialogVisible"
      modal
      :header="editingRateRuleId ? t('attendance.leaveOvertime.editRateRule') : t('attendance.leaveOvertime.addRateRule')"
      :style="{ width: '540px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.calendar.regionLabel') }} *</label>
            <Select
              v-model="rateRuleForm.calendarCode"
              :options="calendarCodeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.calendar.dayType') }} *</label>
            <Select
              v-model="rateRuleForm.dayType"
              :options="dayTypeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
        </div>

        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.fromMinute') }} *</label>
            <InputNumber
              v-model="rateRuleForm.fromMinute"
              :min="0"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.toMinute') }} *</label>
            <InputNumber
              v-model="rateRuleForm.toMinute"
              :min="1"
              class="w-full"
            />
          </div>
        </div>

        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.multiplier') }} *</label>
            <InputNumber
              v-model="rateRuleForm.multiplier"
              :minFractionDigits="2"
              :maxFractionDigits="2"
              :step="0.01"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.defaultCompMode') }} *</label>
            <Select
              v-model="rateRuleForm.defaultCompLeaveMode"
              :options="compLeaveModeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
        </div>

        <div class="dialog-checkbox-grid">
          <div class="checkbox-item">
            <Checkbox v-model="rateRuleForm.nightPremium" binary inputId="rr-night" />
            <label for="rr-night">{{ t('attendance.leaveOvertime.nightPremium') }}</label>
          </div>
          <div class="checkbox-item">
            <Checkbox v-model="rateRuleForm.monthlyCumulative" binary inputId="rr-cum" />
            <label for="rr-cum">{{ t('attendance.leaveOvertime.monthlyCumulative') }}</label>
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('common.description') }}</label>
          <InputText
            v-model="rateRuleForm.description"
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="rateRuleDialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="savingRateRule"
          @click="handleSaveRateRule"
        />
      </template>
    </Dialog>

    <!-- Dialog 4: Overtime Cap Rule Create/Edit -->
    <Dialog
      v-model:visible="capRuleDialogVisible"
      modal
      :header="editingCapRuleId ? t('attendance.leaveOvertime.editCapRule') : t('attendance.leaveOvertime.addCapRule')"
      :style="{ width: '540px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('attendance.calendar.regionLabel') }} *</label>
            <Select
              v-model="capRuleForm.calendarCode"
              :options="calendarCodeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.ruleCode') }} *</label>
            <InputText
              v-model="capRuleForm.ruleCode"
              class="w-full font-mono uppercase"
            />
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('common.name') }} (zh-TW) *</label>
          <InputText
            v-model="capRuleForm.nameZh"
            class="w-full"
          />
        </div>

        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('common.name') }} (en-US)</label>
            <InputText
              v-model="capRuleForm.nameEn"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('common.name') }} (ja-JP)</label>
            <InputText
              v-model="capRuleForm.nameJa"
              class="w-full"
            />
          </div>
        </div>

        <div class="form-row-3">
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.periodMonths') }} *</label>
            <InputNumber
              v-model="capRuleForm.periodMonths"
              :min="1"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.maxMinutes') }} *</label>
            <InputNumber
              v-model="capRuleForm.maxMinutes"
              :min="1"
              class="w-full"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('attendance.leaveOvertime.enforcementAction') }} *</label>
            <Select
              v-model="capRuleForm.enforcementAction"
              :options="capActionOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
        </div>

        <div class="checkbox-item" style="margin-top: 0.5rem">
          <Checkbox v-model="capRuleForm.enabled" binary inputId="cr-en" />
          <label for="cr-en">{{ t('common.enabled') }}</label>
        </div>

        <div class="dialog-field">
          <label>{{ t('common.description') }}</label>
          <InputText
            v-model="capRuleForm.description"
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="capRuleDialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="savingCapRule"
          @click="handleSaveCapRule"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.leave-overtime-manage-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* Tabs Bar */
.tabs-nav-bar {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  background: var(--atria-surface, #ffffff);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 0.35rem;
  overflow-x: auto;
}

.tab-nav-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.85rem;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 0.8125rem;
  font-weight: 600;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.tab-nav-btn:hover {
  background: #f1f5f9;
  color: var(--atria-text, #0f172a);
}

.tab-nav-active {
  background: #eff6ff !important;
  color: #1e40af !important;
}

/* Tab Content Card */
.tab-content-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.toolbar-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toolbar-heading {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.toolbar-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.item-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #475569;
}

/* Tables */
.table-container {
  overflow-x: auto;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
}

.rule-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.rule-table th,
.rule-table td {
  padding: 0.6rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.rule-table th {
  background: var(--atria-surface-subtle, #f8fafc);
  font-weight: 600;
  color: #475569;
}

.rule-table tbody tr:hover {
  background: #f8fafc;
}

.type-name-cell {
  display: flex;
  flex-direction: column;
}

.type-desc {
  font-size: 0.7rem;
}

.empty-cell {
  padding: 2rem !important;
}

/* Tab 5: Balance Ops */
.tab-balance-ops-container {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.ops-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ops-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.ops-icon {
  margin-top: 0.15rem;
}

.ops-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.ops-desc {
  margin: 0.2rem 0 0;
  font-size: 0.8125rem;
}

.ops-form-inline {
  display: flex;
  align-items: flex-end;
  gap: 1rem;
  flex-wrap: wrap;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.ops-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.form-item-full {
  grid-column: 1 / -1;
}

.ops-footer {
  display: flex;
  justify-content: flex-end;
}

.grant-result-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--atria-radius, 6px);
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.result-summary-row {
  display: flex;
  align-items: center;
  gap: 2rem;
  flex-wrap: wrap;
}

.result-kpi {
  display: flex;
  flex-direction: column;
}

.kpi-label {
  font-size: 0.75rem;
}

.kpi-num {
  font-size: 1.25rem;
}

.missing-hire-alert {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #b45309;
}

/* Dialog Form Layout */
.dialog-form-layout {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 0.5rem 0;
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.dialog-field label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #334155;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.form-row-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0.75rem;
}

.dialog-checkbox-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  background: #f8fafc;
  padding: 0.75rem;
  border-radius: var(--atria-radius, 6px);
  border: 1px solid #e2e8f0;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox-item label {
  font-size: 0.8125rem;
  cursor: pointer;
}

.atria-native-input {
  width: 100%;
  padding: 0.45rem 0.65rem;
  border: 1px solid var(--atria-border, #cbd5e1);
  border-radius: var(--atria-radius, 6px);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--atria-text, #0f172a);
  background: var(--atria-surface, #ffffff);
}

.atria-native-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}
</style>
