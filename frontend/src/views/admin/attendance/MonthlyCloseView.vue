<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

import { monthlyCloseApi } from '@/api/attendance'
import { useAuthStore } from '@/stores/auth'
import type {
  BlockingPendingRequestDto,
  MonthlyCloseRequest,
  MonthlyCloseResponse,
  MonthlyReopenRequest
} from '@/types/attendance'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const authStore = useAuthStore()

const canManage = computed<boolean>(() => {
  return (
    authStore.hasPermission('att:monthly-close:manage') ||
    authStore.hasPermission('att:monthly-close:write') ||
    authStore.roles.includes('SUPER_ADMIN')
  )
})

const calendarCodeOptions = computed(() => [
  { label: t('regions.TW'), value: 'TW' },
  { label: t('regions.JP'), value: 'JP' }
])

const filterCalendarOptions = computed(() => [
  { label: t('regions.all'), value: '' },
  { label: t('regions.TW'), value: 'TW' },
  { label: t('regions.JP'), value: 'JP' }
])

// Default yearMonth: previous month
function getDefaultYearMonth(): string {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

// Default settlement date: yesterday
function getYesterdayDate(): string {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// Card 1: Close Month Form State
const closeForm = ref<{
  calendarCode: 'TW' | 'JP'
  yearMonth: string
  note: string
}>({
  calendarCode: 'TW',
  yearMonth: getDefaultYearMonth(),
  note: ''
})

const checkingBlocking = ref<boolean>(false)
const closingMonth = ref<boolean>(false)
const blockingChecked = ref<boolean>(false)
const blockingRequests = ref<BlockingPendingRequestDto[]>([])

// Card 2: Daily Settlement State
const settlementDate = ref<string>(getYesterdayDate())
const runningSettlement = ref<boolean>(false)

// History Table State
const historyList = ref<MonthlyCloseResponse[]>([])
const loadingHistory = ref<boolean>(false)
const selectedFilterRegion = ref<string>('')

// Reopen Dialog State
const reopenDialogVisible = ref<boolean>(false)
const reopeningMonth = ref<boolean>(false)
const targetClosing = ref<MonthlyCloseResponse | null>(null)
const reopenNote = ref<string>('')

function formatDateTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '-'
  try {
    const d = new Date(isoStr)
    if (isNaN(d.getTime())) return isoStr
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${hh}:${mm}`
  } catch {
    return isoStr
  }
}

async function loadHistory() {
  loadingHistory.value = true
  try {
    const regionParam = selectedFilterRegion.value || undefined
    const list = await monthlyCloseApi.list(regionParam)
    historyList.value = list
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingHistory.value = false
  }
}

async function handleCheckBlocking() {
  const ym = closeForm.value.yearMonth.trim()
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(ym)) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: '請輸入正確的目標年月格式 (YYYY-MM)',
      life: 3000
    })
    return
  }

  checkingBlocking.value = true
  try {
    const reqs = await monthlyCloseApi.checkBlockingRequests(
      closeForm.value.calendarCode,
      ym
    )
    blockingRequests.value = reqs
    blockingChecked.value = true
    if (reqs.length === 0) {
      toast.add({
        severity: 'success',
        summary: t('common.success'),
        detail: t('attendance.monthlyClose.noBlockingRequests'),
        life: 3000
      })
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    checkingBlocking.value = false
  }
}

function handleCloseMonth() {
  if (!canManage.value) return
  const ym = closeForm.value.yearMonth.trim()
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(ym)) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: '請輸入正確的目標年月格式 (YYYY-MM)',
      life: 3000
    })
    return
  }

  if (blockingChecked.value && blockingRequests.value.length > 0) {
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('attendance.monthlyClose.blockingAlertTitle'),
      life: 4000
    })
    return
  }

  confirm.require({
    message: `確定要執行 [${closeForm.value.calendarCode}] ${ym} 月結關帳嗎？關帳後該月份所有考勤資料與打卡紀錄將鎖定封存。`,
    header: t('attendance.monthlyClose.closeMonthCard'),
    icon: 'pi pi-lock',
    acceptClass: 'p-button-danger',
    accept: async () => {
      closingMonth.value = true
      try {
        const payload: MonthlyCloseRequest = {
          calendarCode: closeForm.value.calendarCode,
          yearMonth: ym,
          note: closeForm.value.note.trim() || null
        }
        await monthlyCloseApi.closeMonth(payload)
        toast.add({
          severity: 'success',
          summary: t('common.success'),
          detail: t('common.success'),
          life: 3000
        })
        closeForm.value.note = ''
        blockingChecked.value = false
        blockingRequests.value = []
        await loadHistory()
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : t('common.error')
        toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
      } finally {
        closingMonth.value = false
      }
    }
  })
}

async function handleRunSettlement() {
  if (!canManage.value) return
  const targetDate = settlementDate.value.trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: '請輸入正確的日期格式 (YYYY-MM-DD)',
      life: 3000
    })
    return
  }

  runningSettlement.value = true
  try {
    const settledCount = await monthlyCloseApi.triggerDailySettlement(targetDate)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('attendance.monthlyClose.settlementSuccess', { count: settledCount }),
      life: 3500
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    runningSettlement.value = false
  }
}

function openReopenDialog(item: MonthlyCloseResponse) {
  if (!canManage.value) return
  targetClosing.value = item
  reopenNote.value = ''
  reopenDialogVisible.value = true
}

async function handleConfirmReopen() {
  if (!targetClosing.value || !canManage.value) return
  reopeningMonth.value = true
  try {
    const payload: MonthlyReopenRequest = {
      calendarCode: targetClosing.value.calendarCode,
      yearMonth: targetClosing.value.yearMonth,
      note: reopenNote.value.trim() || null
    }
    await monthlyCloseApi.reopenMonth(payload)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('attendance.monthlyClose.reopenBtn'),
      life: 3000
    })
    reopenDialogVisible.value = false
    await loadHistory()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    reopeningMonth.value = false
  }
}

onMounted(() => {
  loadHistory()
})
</script>

<template>
  <div class="monthly-close-view">
    <!-- Top Header -->
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('attendance.monthlyClose.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('attendance.monthlyClose.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          icon="pi pi-refresh"
          :label="t('common.refresh')"
          severity="secondary"
          outlined
          size="small"
          :loading="loadingHistory"
          @click="loadHistory"
        />
      </div>
    </div>

    <!-- Two-Column Top Action Grid -->
    <div class="action-grid-2cols">
      <!-- Card 1: Monthly Closing Lock -->
      <div class="atria-card action-card">
        <div class="card-title-bar">
          <div class="card-title-group">
            <i class="pi pi-lock-open card-icon"></i>
            <h2 class="card-title">{{ t('attendance.monthlyClose.closeMonthCard') }}</h2>
          </div>
          <Tag value="Lockout Guard" severity="warn" class="card-badge" />
        </div>

        <div class="card-form">
          <div class="form-row-2">
            <!-- Region -->
            <div class="atria-field">
              <label>{{ t('attendance.calendar.regionLabel') }}</label>
              <Select
                v-model="closeForm.calendarCode"
                :options="calendarCodeOptions"
                optionLabel="label"
                optionValue="value"
                class="w-full"
                @change="() => { blockingChecked = false; blockingRequests = []; }"
              />
            </div>

            <!-- Target YearMonth -->
            <div class="atria-field">
              <label>{{ t('attendance.monthlyClose.yearMonthLabel') }}</label>
              <InputText
                v-model="closeForm.yearMonth"
                placeholder="YYYY-MM"
                class="w-full font-mono"
                @input="() => { blockingChecked = false; blockingRequests = []; }"
              />
            </div>
          </div>

          <!-- Note -->
          <div class="atria-field">
            <label>{{ t('attendance.calendar.note') }}</label>
            <InputText
              v-model="closeForm.note"
              :placeholder="t('attendance.monthlyClose.notePlaceholder')"
              class="w-full"
            />
          </div>

          <!-- Buttons -->
          <div class="form-actions-row">
            <Button
              icon="pi pi-search"
              :label="t('attendance.monthlyClose.checkBlockingBtn')"
              severity="secondary"
              outlined
              size="small"
              :loading="checkingBlocking"
              @click="handleCheckBlocking"
            />
            <Button
              v-if="canManage"
              icon="pi pi-lock"
              :label="t('attendance.monthlyClose.executeCloseBtn')"
              severity="danger"
              size="small"
              :loading="closingMonth"
              @click="handleCloseMonth"
            />
          </div>

          <!-- Blocking Verification Results -->
          <div v-if="blockingChecked" class="blocking-result-section">
            <!-- Green Clean Banner -->
            <div v-if="blockingRequests.length === 0" class="banner-success">
              <i class="pi pi-check-circle banner-icon"></i>
              <span>{{ t('attendance.monthlyClose.noBlockingRequests') }}</span>
            </div>

            <!-- Red Alert Banner + Table -->
            <div v-else class="blocking-alert-wrap">
              <div class="banner-danger">
                <i class="pi pi-exclamation-triangle banner-icon"></i>
                <span class="banner-text">{{ t('attendance.monthlyClose.blockingAlertTitle') }}</span>
              </div>

              <DataTable
                :value="blockingRequests"
                dataKey="requestId"
                class="p-datatable-sm blocking-table"
                responsiveLayout="scroll"
              >
                <Column header="#" style="width: 70px">
                  <template #body="{ data }: { data: BlockingPendingRequestDto }">
                    <span class="font-mono text-xs">#{{ data.requestId }}</span>
                  </template>
                </Column>

                <Column :header="t('iam.user.displayName')" style="min-width: 130px">
                  <template #body="{ data }: { data: BlockingPendingRequestDto }">
                    <div class="applicant-info">
                      <span class="font-bold text-xs">{{ data.displayName }}</span>
                      <span class="text-xs text-muted font-mono">@{{ data.username }}</span>
                    </div>
                  </template>
                </Column>

                <Column :header="t('common.status')" style="width: 100px">
                  <template #body="{ data }: { data: BlockingPendingRequestDto }">
                    <Tag :value="data.requestType" severity="warn" class="text-xs" />
                  </template>
                </Column>

                <Column header="期間" style="min-width: 150px">
                  <template #body="{ data }: { data: BlockingPendingRequestDto }">
                    <span class="font-mono text-xs">{{ data.startDate }} ~ {{ data.endDate }}</span>
                  </template>
                </Column>

                <Column :header="t('attendance.calendar.note')" style="min-width: 140px">
                  <template #body="{ data }: { data: BlockingPendingRequestDto }">
                    <span class="text-xs">{{ data.reason || '-' }}</span>
                  </template>
                </Column>
              </DataTable>
            </div>
          </div>
        </div>
      </div>

      <!-- Card 2: Daily Settlement -->
      <div class="atria-card action-card">
        <div class="card-title-bar">
          <div class="card-title-group">
            <i class="pi pi-sync card-icon"></i>
            <h2 class="card-title">{{ t('attendance.monthlyClose.settlementCard') }}</h2>
          </div>
          <Tag value="Batch Cron / Manual" severity="info" class="card-badge" />
        </div>

        <div class="card-form">
          <p class="settlement-description">
            {{ t('attendance.monthlyClose.settlementDesc') }}
          </p>

          <div class="settlement-input-row">
            <div class="atria-field flex-1">
              <label>{{ t('attendance.monthlyClose.settlementDateLabel') }}</label>
              <InputText
                v-model="settlementDate"
                placeholder="YYYY-MM-DD"
                class="w-full font-mono"
              />
            </div>
            <Button
              v-if="canManage"
              icon="pi pi-play"
              :label="t('attendance.monthlyClose.runSettlementBtn')"
              severity="primary"
              size="small"
              class="self-end"
              :loading="runningSettlement"
              @click="handleRunSettlement"
            />
          </div>

          <div class="settlement-hint-box">
            <i class="pi pi-info-circle hint-icon"></i>
            <span class="hint-text">
              跨日結算會根據地區打卡規則（如切算點 04:00 AM）對表定出勤日進行工時裁決、計算缺卡（MISSING_PUNCH）或曠職（ABSENT），並標記為 settled。
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Bottom Card: Monthly Closing History Table -->
    <div class="atria-card history-card">
      <div class="history-header">
        <div class="header-left">
          <h2 class="history-title">{{ t('attendance.monthlyClose.historyTitle') }}</h2>
          <span class="history-count">({{ historyList.length }})</span>
        </div>

        <div class="header-right">
          <Select
            v-model="selectedFilterRegion"
            :options="filterCalendarOptions"
            optionLabel="label"
            optionValue="value"
            class="w-44"
            @change="loadHistory"
          />
        </div>
      </div>

      <DataTable
        :value="historyList"
        :loading="loadingHistory"
        dataKey="id"
        class="p-datatable-sm history-table"
        responsiveLayout="scroll"
        stripedRows
      >
        <template #empty>
          <div class="empty-message">{{ t('common.noData') }}</div>
        </template>

        <!-- Calendar Code -->
        <Column :header="t('attendance.calendar.regionLabel')" style="width: 90px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <Tag :value="data.calendarCode" severity="secondary" class="font-mono text-xs" />
          </template>
        </Column>

        <!-- YearMonth -->
        <Column :header="t('attendance.monthlyClose.yearMonthLabel')" style="width: 120px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <span class="font-mono font-bold text-sm">{{ data.yearMonth }}</span>
          </template>
        </Column>

        <!-- Status -->
        <Column :header="t('common.status')" style="width: 110px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <Tag
              :value="data.status"
              :severity="data.status === 'CLOSED' ? 'danger' : 'info'"
              class="font-mono text-xs"
            />
          </template>
        </Column>

        <!-- Closed At -->
        <Column :header="t('attendance.monthlyClose.closedAt')" style="min-width: 150px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <span class="font-mono text-xs text-muted">{{ formatDateTime(data.closedAt) }}</span>
          </template>
        </Column>

        <!-- Closed By -->
        <Column :header="t('attendance.monthlyClose.closedBy')" style="min-width: 120px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <span class="text-xs">{{ data.closedByUsername || (data.closedBy ? `#${data.closedBy}` : '-') }}</span>
          </template>
        </Column>

        <!-- Reopened At -->
        <Column :header="t('attendance.monthlyClose.reopenedAt')" style="min-width: 150px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <span class="font-mono text-xs text-muted">{{ formatDateTime(data.reopenedAt) }}</span>
          </template>
        </Column>

        <!-- Note -->
        <Column :header="t('attendance.calendar.note')" style="min-width: 160px">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <span class="text-xs text-muted">{{ data.note || '-' }}</span>
          </template>
        </Column>

        <!-- Action: Reopen -->
        <Column :header="t('common.actions')" style="width: 110px; text-align: center" headerClass="text-center">
          <template #body="{ data }: { data: MonthlyCloseResponse }">
            <Button
              v-if="data.status === 'CLOSED' && canManage"
              icon="pi pi-lock-open"
              :label="t('attendance.monthlyClose.reopenBtn')"
              size="small"
              severity="warn"
              text
              class="reopen-action-btn"
              @click="openReopenDialog(data)"
            />
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Reopen Dialog -->
    <Dialog
      v-model:visible="reopenDialogVisible"
      :header="t('attendance.monthlyClose.reopenBtn')"
      :modal="true"
      :style="{ width: '480px' }"
    >
      <div class="reopen-dialog-content">
        <p class="reopen-target-hint">
          目標月份：<strong class="font-mono">[{{ targetClosing?.calendarCode }}] {{ targetClosing?.yearMonth }}</strong>
        </p>

        <div class="atria-field">
          <label>{{ t('attendance.calendar.note') }}</label>
          <InputText
            v-model="reopenNote"
            :placeholder="t('attendance.monthlyClose.notePlaceholder')"
            class="w-full"
          />
        </div>

        <div class="reopen-warning-box">
          <i class="pi pi-exclamation-triangle warning-icon"></i>
          <span class="warning-text">
            解除月結鎖定後，員工與主管將可針對該月份提交補登或請假申請，建議於修改完成後儘速重新關帳。
          </span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <Button
            :label="t('common.cancel')"
            severity="secondary"
            outlined
            @click="reopenDialogVisible = false"
          />
          <Button
            :label="t('attendance.monthlyClose.reopenBtn')"
            icon="pi pi-lock-open"
            severity="warn"
            :loading="reopeningMonth"
            @click="handleConfirmReopen"
          />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.monthly-close-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* Action Cards Grid */
.action-grid-2cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 1rem;
}

.action-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.card-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--atria-border);
}

.card-title-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card-icon {
  font-size: 1.1rem;
  color: var(--atria-primary);
}

.card-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--atria-text);
}

.card-badge {
  font-size: 0.7rem;
}

.card-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.form-actions-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

/* Banners */
.banner-success {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 0.75rem;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--atria-radius);
  color: #166534;
  font-size: 0.8125rem;
  font-weight: 600;
}

.banner-danger {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 0.75rem;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--atria-radius);
  color: #991b1b;
  font-size: 0.8125rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.banner-icon {
  font-size: 1rem;
}

.blocking-result-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.blocking-alert-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.applicant-info {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.text-muted {
  color: var(--atria-text-light);
}

/* Settlement Card Styles */
.settlement-description {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--atria-text-muted);
  line-height: 1.5;
}

.settlement-input-row {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
}

.settlement-hint-box {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.625rem 0.75rem;
  background: var(--atria-surface-subtle);
  border: 1px solid var(--atria-border);
  border-radius: var(--atria-radius);
  margin-top: 0.25rem;
}

.hint-icon {
  font-size: 0.875rem;
  color: var(--atria-text-light);
  margin-top: 0.125rem;
}

.hint-text {
  font-size: 0.75rem;
  color: var(--atria-text-muted);
  line-height: 1.45;
}

/* History Card */
.history-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.history-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--atria-text);
}

.history-count {
  font-size: 0.8125rem;
  color: var(--atria-text-light);
}

.reopen-action-btn {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
}

/* Reopen Dialog */
.reopen-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-top: 0.25rem;
}

.reopen-target-hint {
  margin: 0;
  font-size: 0.875rem;
  color: var(--atria-text);
}

.reopen-warning-box {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.625rem 0.75rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--atria-radius);
}

.warning-icon {
  font-size: 1rem;
  color: #d97706;
  margin-top: 0.125rem;
}

.warning-text {
  font-size: 0.78rem;
  line-height: 1.45;
  color: #92400e;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.empty-message {
  text-align: center;
  padding: 1.5rem;
  color: var(--atria-text-light);
}

@media (max-width: 768px) {
  .action-grid-2cols {
    grid-template-columns: 1fr;
  }
}
</style>
