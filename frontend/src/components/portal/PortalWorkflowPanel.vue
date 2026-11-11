<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import {
  workflowApi,
  type WorkflowInstanceDto,
  type WorkflowInstanceDetailDto,
  type FormTemplateDto,
  type FormFieldSchema,
  type WorkflowDeputyDto,
  type WorkflowInstanceStatus,
  type WorkflowTaskAction
} from '@/api/workflow'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import {
  Inbox,
  FileText,
  PlusCircle,
  UserCheck,
  CheckCircle2,
  XCircle,
  Undo2,
  GitCompare,
  RefreshCw,
  Send,
  Save,
  Clock,
  Eye,
  FileEdit
} from 'lucide-vue-next'

const { t, locale } = useI18n()
const toast = useToast()
const authStore = useAuthStore()

type TabKey = 'inbox' | 'myInstances' | 'newForm' | 'deputy'
const activeTab = ref<TabKey>('inbox')

// Data states
const inboxList = ref<WorkflowInstanceDto[]>([])
const myInstancesList = ref<WorkflowInstanceDto[]>([])
const availableTemplates = ref<FormTemplateDto[]>([])
const myDeputiesList = ref<WorkflowDeputyDto[]>([])

const loadingInbox = ref<boolean>(false)
const loadingMyInstances = ref<boolean>(false)
const loadingTemplates = ref<boolean>(false)
const loadingDeputies = ref<boolean>(false)

// Tab 3: Dynamic Form state
const editingDraftInstanceId = ref<number | null>(null)
const selectedTemplateCode = ref<string>('')
const formTitle = ref<string>('')
const formData = ref<Record<string, any>>({})
const submittingForm = ref<boolean>(false)

// Tab 4: Deputy Form state
const deputyForm = ref<{
  deputyUserId: number | null
  startDate: string
  endDate: string
  reason: string
}>({
  deputyUserId: null,
  startDate: '',
  endDate: '',
  reason: ''
})
const submittingDeputy = ref<boolean>(false)
const disablingDeputy = ref<Record<number, boolean>>({})

// Instance Detail & Action Dialog state
const detailVisible = ref<boolean>(false)
const loadingDetail = ref<boolean>(false)
const instanceDetail = ref<WorkflowInstanceDetailDto | null>(null)
const actionComment = ref<string>('')
const submittingAction = ref<boolean>(false)

// Computed helpers
const selectedTemplate = computed<FormTemplateDto | undefined>(() => {
  return availableTemplates.value.find((tpl) => tpl.code === selectedTemplateCode.value)
})

const templateSelectOptions = computed(() => {
  return availableTemplates.value.map((tpl) => ({
    label: `${getLocalizedText(tpl.name, tpl.code)} (${tpl.code}) - v${tpl.currentVersion}`,
    value: tpl.code
  }))
})

function getLocalizedText(field: Record<string, string> | undefined, fallback: string): string {
  if (!field) return fallback || ''
  const currentLoc = (locale.value as string) || 'zh-TW'
  return field[currentLoc] || field['zh-TW'] || field['en-US'] || fallback || ''
}

function getFieldLabel(field: FormFieldSchema): string {
  return getLocalizedText(field.label, field.key)
}

function formatTimestamp(isoStr: string | null | undefined): string {
  if (!isoStr) return '—'
  try {
    const d = new Date(isoStr)
    if (isNaN(d.getTime())) return isoStr
    return d.toLocaleString()
  } catch {
    return isoStr
  }
}

function formatDiffValue(val: unknown): string {
  if (val === null || val === undefined || val === '') return '—'
  if (typeof val === 'boolean') return val ? 'true' : 'false'
  if (typeof val === 'object') {
    try {
      return JSON.stringify(val)
    } catch {
      return String(val)
    }
  }
  return String(val)
}

function getWorkflowStatusSeverity(
  status: WorkflowInstanceStatus
): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
  switch (status) {
    case 'APPROVED':
      return 'success'
    case 'PENDING':
      return 'warn'
    case 'REJECTED':
      return 'danger'
    case 'DRAFT':
      return 'info'
    case 'CANCELLED':
    case 'REVOKED':
    default:
      return 'secondary'
  }
}

function getWorkflowActionSeverity(
  action: WorkflowTaskAction
): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
  switch (action) {
    case 'APPROVE':
      return 'success'
    case 'SUBMIT':
    case 'RESUBMIT':
      return 'info'
    case 'RETURN_TO_DRAFT':
      return 'warn'
    case 'REJECT':
    case 'REVOKE':
      return 'danger'
    case 'CANCEL':
    case 'REASSIGN':
    case 'AUTO_SKIP':
    default:
      return 'secondary'
  }
}

function getDetailFieldLabel(key: string, schemaSnapshot: FormFieldSchema[] | null): string {
  if (schemaSnapshot) {
    const found = schemaSnapshot.find((s) => s.key === key)
    if (found) return getFieldLabel(found)
  }
  return key
}

function isCurrentApplicant(inst: WorkflowInstanceDetailDto | null): boolean {
  if (!inst || !authStore.user) return false
  return inst.applicantId === authStore.user.id
}

// Data loaders
async function loadInbox() {
  loadingInbox.value = true
  try {
    inboxList.value = await workflowApi.getInbox()
  } catch (err: unknown) {
    console.error('Failed to load workflow inbox', err)
  } finally {
    loadingInbox.value = false
  }
}

async function loadMyInstances() {
  loadingMyInstances.value = true
  try {
    myInstancesList.value = await workflowApi.getMyInstances()
  } catch (err: unknown) {
    console.error('Failed to load my workflow instances', err)
  } finally {
    loadingMyInstances.value = false
  }
}

async function loadTemplates() {
  loadingTemplates.value = true
  try {
    availableTemplates.value = await workflowApi.listTemplates(true)
  } catch (err: unknown) {
    console.error('Failed to load enabled workflow templates', err)
  } finally {
    loadingTemplates.value = false
  }
}

async function loadDeputies() {
  loadingDeputies.value = true
  try {
    myDeputiesList.value = await workflowApi.listMyDeputies()
  } catch (err: unknown) {
    console.error('Failed to load my deputies', err)
  } finally {
    loadingDeputies.value = false
  }
}

async function refreshActiveTabData() {
  if (activeTab.value === 'inbox') await loadInbox()
  else if (activeTab.value === 'myInstances') await loadMyInstances()
  else if (activeTab.value === 'newForm') await loadTemplates()
  else if (activeTab.value === 'deputy') await loadDeputies()
}

// Tab 3: Template & Draft actions
function onTemplateChange() {
  if (!selectedTemplate.value) return
  if (!editingDraftInstanceId.value) {
    const initial: Record<string, unknown> = {}
    for (const field of selectedTemplate.value.schemaJson || []) {
      initial[field.key] = field.type === 'NUMBER' ? null : ''
    }
    formData.value = initial
  }
}

function resetFormState() {
  editingDraftInstanceId.value = null
  selectedTemplateCode.value = ''
  formTitle.value = ''
  formData.value = {}
}

async function handleEditDraft(row: WorkflowInstanceDto) {
  try {
    const detail = await workflowApi.getInstanceDetail(row.id)
    editingDraftInstanceId.value = row.id
    selectedTemplateCode.value = row.formTemplateCode || ''
    formTitle.value = row.title || ''
    formData.value = { ...(detail.formDataJson || {}) }
    activeTab.value = 'newForm'
    toast.add({
      severity: 'info',
      summary: t('common.edit'),
      detail: `${row.instanceNo} (${row.title})`,
      life: 3000
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  }
}

async function handleSubmitForm() {
  if (!selectedTemplateCode.value || !formTitle.value.trim()) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }

  // Validate required fields
  if (selectedTemplate.value?.schemaJson) {
    for (const f of selectedTemplate.value.schemaJson) {
      if (f.required) {
        const val = formData.value[f.key]
        if (val === undefined || val === null || val === '') {
          toast.add({
            severity: 'error',
            summary: t('common.error'),
            detail: `${getFieldLabel(f)}: ${t('common.error')}`,
            life: 3000
          })
          return
        }
      }
    }
  }

  submittingForm.value = true
  try {
    const payload = {
      instanceId: editingDraftInstanceId.value,
      formTemplateCode: selectedTemplateCode.value,
      title: formTitle.value.trim(),
      formData: formData.value
    }
    await workflowApi.submitForm(payload)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.submitFormBtn'),
      life: 3000
    })
    resetFormState()
    activeTab.value = 'myInstances'
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingForm.value = false
  }
}

async function handleSaveDraft() {
  if (!selectedTemplateCode.value || !formTitle.value.trim()) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }

  submittingForm.value = true
  try {
    const payload = {
      instanceId: editingDraftInstanceId.value,
      formTemplateCode: selectedTemplateCode.value,
      title: formTitle.value.trim(),
      formData: formData.value
    }
    await workflowApi.saveDraft(payload)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.saveDraftBtn'),
      life: 3000
    })
    resetFormState()
    activeTab.value = 'myInstances'
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingForm.value = false
  }
}

// Tab 4: Deputy actions
async function handleCreateDeputy() {
  if (!deputyForm.value.deputyUserId || !deputyForm.value.startDate || !deputyForm.value.endDate) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }

  submittingDeputy.value = true
  try {
    await workflowApi.createDeputy({
      deputyUserId: Number(deputyForm.value.deputyUserId),
      startDate: deputyForm.value.startDate,
      endDate: deputyForm.value.endDate,
      reason: deputyForm.value.reason.trim() || undefined
    })
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.addDeputyBtn'),
      life: 3000
    })
    deputyForm.value = {
      deputyUserId: null,
      startDate: '',
      endDate: '',
      reason: ''
    }
    await loadDeputies()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingDeputy.value = false
  }
}

async function handleDisableDeputy(id: number) {
  disablingDeputy.value[id] = true
  try {
    await workflowApi.disableDeputy(id)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.disableDeputyBtn'),
      life: 3000
    })
    await loadDeputies()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    disablingDeputy.value[id] = false
  }
}

// Instance Detail & Actions
async function openInstanceDetail(id: number) {
  loadingDetail.value = true
  detailVisible.value = true
  actionComment.value = ''
  try {
    instanceDetail.value = await workflowApi.getInstanceDetail(id)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
    detailVisible.value = false
  } finally {
    loadingDetail.value = false
  }
}

async function handleApprove() {
  if (!instanceDetail.value) return
  submittingAction.value = true
  try {
    await workflowApi.approveTask(instanceDetail.value.id, actionComment.value.trim() || undefined)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.approveBtn'),
      life: 3000
    })
    detailVisible.value = false
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingAction.value = false
  }
}

async function handleReject() {
  if (!instanceDetail.value) return
  submittingAction.value = true
  try {
    await workflowApi.rejectTask(instanceDetail.value.id, actionComment.value.trim() || undefined)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.rejectBtn'),
      life: 3000
    })
    detailVisible.value = false
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingAction.value = false
  }
}

async function handleReturnToDraft() {
  if (!instanceDetail.value) return
  submittingAction.value = true
  try {
    await workflowApi.returnToDraft(instanceDetail.value.id, actionComment.value.trim() || undefined)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.returnToDraftBtn'),
      life: 3000
    })
    detailVisible.value = false
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingAction.value = false
  }
}

async function handleWithdraw() {
  if (!instanceDetail.value) return
  submittingAction.value = true
  try {
    await workflowApi.cancelInstance(instanceDetail.value.id, actionComment.value.trim() || undefined)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.withdrawBtn'),
      life: 3000
    })
    detailVisible.value = false
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingAction.value = false
  }
}

async function handleRevoke() {
  if (!instanceDetail.value) return
  submittingAction.value = true
  try {
    await workflowApi.revokeInstance(instanceDetail.value.id, actionComment.value.trim() || undefined)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('workflow.revokeBtn'),
      life: 3000
    })
    detailVisible.value = false
    await Promise.all([loadInbox(), loadMyInstances()])
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 4000 })
  } finally {
    submittingAction.value = false
  }
}

onMounted(() => {
  loadInbox()
  loadMyInstances()
  loadTemplates()
  loadDeputies()
})
</script>

<template>
  <section class="atria-card portal-workflow-card" data-testid="portal-workflow-panel">
    <!-- Panel Header -->
    <div class="panel-header">
      <div class="panel-header-titles">
        <h2 class="panel-title">{{ t('workflow.portalTitle') }}</h2>
        <p class="panel-subtitle">{{ t('workflow.portalSubtitle') }}</p>
      </div>
      <div class="panel-header-actions">
        <Button
          size="small"
          severity="secondary"
          text
          :label="t('common.refresh')"
          @click="refreshActiveTabData"
        >
          <template #icon>
            <RefreshCw :size="14" class="btn-icon" />
          </template>
        </Button>
      </div>
    </div>

    <!-- Sub-Tabs Navigation -->
    <div class="panel-tabs-bar">
      <button
        type="button"
        class="tab-btn"
        :class="{ 'tab-btn-active': activeTab === 'inbox' }"
        data-testid="wf-tab-inbox"
        @click="activeTab = 'inbox'"
      >
        <Inbox :size="15" />
        <span>{{ t('workflow.inboxTab') }}</span>
        <span v-if="inboxList.length > 0" class="tab-badge font-mono">{{ inboxList.length }}</span>
      </button>

      <button
        type="button"
        class="tab-btn"
        :class="{ 'tab-btn-active': activeTab === 'myInstances' }"
        data-testid="wf-tab-my-instances"
        @click="activeTab = 'myInstances'"
      >
        <FileText :size="15" />
        <span>{{ t('workflow.myInstancesTab') }}</span>
      </button>

      <button
        type="button"
        class="tab-btn"
        :class="{ 'tab-btn-active': activeTab === 'newForm' }"
        data-testid="wf-tab-new-form"
        @click="activeTab = 'newForm'"
      >
        <PlusCircle :size="15" />
        <span>{{ t('workflow.newFormTab') }}</span>
      </button>

      <button
        type="button"
        class="tab-btn"
        :class="{ 'tab-btn-active': activeTab === 'deputy' }"
        data-testid="wf-tab-deputy"
        @click="activeTab = 'deputy'"
      >
        <UserCheck :size="15" />
        <span>{{ t('workflow.deputyTab') }}</span>
      </button>
    </div>

    <!-- Tab 1: Inbox Table -->
    <div v-show="activeTab === 'inbox'" class="tab-content">
      <div class="table-container">
        <table class="atria-table workflow-table">
          <thead>
            <tr>
              <th style="width: 140px">{{ t('workflow.instanceNo') }}</th>
              <th>{{ t('workflow.targetType') }}</th>
              <th>{{ t('workflow.title') }}</th>
              <th>{{ t('workflow.applicant') }}</th>
              <th>{{ t('workflow.department') }}</th>
              <th>{{ t('workflow.currentStep') }}</th>
              <th>{{ t('workflow.submissionCount') }}</th>
              <th style="width: 150px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in inboxList" :key="row.id">
              <td class="font-mono text-xs font-bold">{{ row.instanceNo }}</td>
              <td>
                <span class="target-type-pill">{{ t('workflow.targetTypes.' + row.targetType) }}</span>
              </td>
              <td class="font-bold">{{ row.title }}</td>
              <td>{{ row.applicantName }}</td>
              <td>{{ row.departmentName }}</td>
              <td class="font-mono">#{{ row.currentStepOrder ?? 1 }}</td>
              <td class="font-mono">{{ row.submissionCount }}</td>
              <td>
                <Button
                  size="small"
                  severity="primary"
                  outlined
                  :label="t('workflow.viewDetail')"
                  @click="openInstanceDetail(row.id)"
                >
                  <template #icon>
                    <Eye :size="13" class="btn-icon" />
                  </template>
                </Button>
              </td>
            </tr>
            <tr v-if="inboxList.length === 0">
              <td colspan="8" class="empty-table-cell text-muted">
                {{ loadingInbox ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Tab 2: My Instances Table -->
    <div v-show="activeTab === 'myInstances'" class="tab-content">
      <div class="table-container">
        <table class="atria-table workflow-table">
          <thead>
            <tr>
              <th style="width: 140px">{{ t('workflow.instanceNo') }}</th>
              <th>{{ t('workflow.targetType') }}</th>
              <th>{{ t('workflow.title') }}</th>
              <th>{{ t('common.status') }}</th>
              <th>{{ t('workflow.submissionCount') }}</th>
              <th>{{ t('workflow.submittedAt') }}</th>
              <th style="width: 200px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in myInstancesList" :key="row.id">
              <td class="font-mono text-xs font-bold">{{ row.instanceNo }}</td>
              <td>
                <span class="target-type-pill">{{ t('workflow.targetTypes.' + row.targetType) }}</span>
              </td>
              <td class="font-bold">{{ row.title }}</td>
              <td>
                <Tag
                  :value="t('workflow.statuses.' + row.status)"
                  :severity="getWorkflowStatusSeverity(row.status)"
                />
              </td>
              <td class="font-mono">{{ row.submissionCount }}</td>
              <td class="font-mono text-xs">{{ formatTimestamp(row.submittedAt) }}</td>
              <td>
                <div class="row-actions-group">
                  <Button
                    size="small"
                    severity="secondary"
                    outlined
                    :label="t('workflow.viewDetail')"
                    @click="openInstanceDetail(row.id)"
                  >
                    <template #icon>
                      <Eye :size="13" class="btn-icon" />
                    </template>
                  </Button>
                  <Button
                    v-if="row.status === 'DRAFT' && row.targetType === 'DYNAMIC_FORM'"
                    size="small"
                    severity="warn"
                    :label="t('common.edit')"
                    @click="handleEditDraft(row)"
                  >
                    <template #icon>
                      <FileEdit :size="13" class="btn-icon" />
                    </template>
                  </Button>
                </div>
              </td>
            </tr>
            <tr v-if="myInstancesList.length === 0">
              <td colspan="7" class="empty-table-cell text-muted">
                {{ loadingMyInstances ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Tab 3: New / Revise Dynamic Form -->
    <div v-show="activeTab === 'newForm'" class="tab-content">
      <div class="dynamic-form-container">
        <!-- Draft Edit Indicator Banner -->
        <div v-if="editingDraftInstanceId" class="draft-edit-alert">
          <div class="alert-info-line">
            <Clock :size="16" class="text-warning" />
            <span class="font-bold">
              {{ t('common.edit') }}: #{{ editingDraftInstanceId }} ({{ formTitle }})
            </span>
          </div>
          <Button
            size="small"
            severity="secondary"
            text
            :label="t('common.cancel')"
            @click="resetFormState"
          />
        </div>

        <!-- Template Picker & Version Info -->
        <div class="form-row-2col">
          <div class="form-field">
            <label class="font-bold">{{ t('workflow.selectTemplate') }}</label>
            <Select
              v-model="selectedTemplateCode"
              :options="templateSelectOptions"
              optionLabel="label"
              optionValue="value"
              :placeholder="t('workflow.selectTemplate')"
              class="w-full"
              @change="onTemplateChange"
            />
          </div>

          <div class="form-field">
            <label class="font-bold">{{ t('workflow.formVersion') }}</label>
            <div class="version-display-box font-mono">
              <span v-if="selectedTemplate">v{{ selectedTemplate.currentVersion }} ({{ selectedTemplate.category }})</span>
              <span v-else class="text-muted">—</span>
            </div>
          </div>
        </div>

        <!-- Form Instance Title -->
        <div class="form-field">
          <label class="font-bold">
            {{ t('workflow.title') }}
            <span class="required-star">*</span>
          </label>
          <InputText
            v-model="formTitle"
            :placeholder="t('workflow.title')"
            class="w-full"
          />
        </div>

        <!-- Dynamic Form Fields Generated from Schema -->
        <div v-if="selectedTemplate && selectedTemplate.schemaJson" class="schema-fields-panel">
          <div
            v-for="field in selectedTemplate.schemaJson"
            :key="field.key"
            class="form-field"
          >
            <label class="font-bold">
              {{ getFieldLabel(field) }}
              <span v-if="field.required" class="required-star">*</span>
            </label>

            <!-- TEXT input -->
            <InputText
              v-if="field.type === 'TEXT'"
              v-model="formData[field.key]"
              class="w-full"
            />

            <!-- TEXTAREA input -->
            <Textarea
              v-else-if="field.type === 'TEXTAREA'"
              v-model="formData[field.key]"
              rows="3"
              class="w-full"
            />

            <!-- NUMBER input -->
            <input
              v-else-if="field.type === 'NUMBER'"
              v-model.number="formData[field.key]"
              type="number"
              :min="field.min"
              :max="field.max"
              class="atria-native-input"
            />

            <!-- SELECT input -->
            <select
              v-else-if="field.type === 'SELECT'"
              v-model="formData[field.key]"
              class="atria-native-input"
            >
              <option value="" disabled>{{ t('workflow.selectTemplate') }}</option>
              <option
                v-for="opt in field.options"
                :key="opt.value"
                :value="opt.value"
              >
                {{ getLocalizedText(opt.label, opt.value) }}
              </option>
            </select>

            <!-- DATE input -->
            <input
              v-else-if="field.type === 'DATE'"
              v-model="formData[field.key]"
              type="date"
              class="atria-native-input"
            />
          </div>
        </div>

        <!-- Submit & Save Draft Action Buttons -->
        <div class="dynamic-form-actions-bar">
          <Button
            size="small"
            severity="secondary"
            outlined
            :label="t('workflow.saveDraftBtn')"
            data-testid="wf-save-draft-btn"
            :loading="submittingForm"
            :disabled="!selectedTemplateCode || !formTitle"
            @click="handleSaveDraft"
          >
            <template #icon>
              <Save :size="15" class="btn-icon" />
            </template>
          </Button>

          <Button
            size="small"
            severity="primary"
            :label="editingDraftInstanceId ? t('workflow.resubmitFormBtn') : t('workflow.submitFormBtn')"
            data-testid="wf-submit-form-btn"
            :loading="submittingForm"
            :disabled="!selectedTemplateCode || !formTitle"
            @click="handleSubmitForm"
          >
            <template #icon>
              <Send :size="15" class="btn-icon" />
            </template>
          </Button>
        </div>
      </div>
    </div>

    <!-- Tab 4: Deputy Delegation Management -->
    <div v-show="activeTab === 'deputy'" class="tab-content">
      <div class="deputy-container">
        <!-- New Deputy Delegation Form -->
        <div class="deputy-form-card">
          <h3 class="subsection-title">{{ t('workflow.addDeputyBtn') }}</h3>
          <div class="deputy-form-grid">
            <div class="form-field">
              <label class="font-bold">
                {{ t('workflow.deputyUserLabel') }}
                <span class="required-star">*</span>
              </label>
              <input
                v-model.number="deputyForm.deputyUserId"
                type="number"
                class="atria-native-input"
                placeholder="2"
                required
              />
            </div>

            <div class="form-field">
              <label class="font-bold">
                {{ t('workflow.startDate') }}
                <span class="required-star">*</span>
              </label>
              <input
                v-model="deputyForm.startDate"
                type="date"
                class="atria-native-input"
                required
              />
            </div>

            <div class="form-field">
              <label class="font-bold">
                {{ t('workflow.endDate') }}
                <span class="required-star">*</span>
              </label>
              <input
                v-model="deputyForm.endDate"
                type="date"
                class="atria-native-input"
                required
              />
            </div>

            <div class="form-field span-full">
              <label class="font-bold">{{ t('workflow.deputyReason') }}</label>
              <InputText
                v-model="deputyForm.reason"
                class="w-full"
                :placeholder="t('workflow.deputyReason')"
              />
            </div>
          </div>

          <div class="deputy-form-action">
            <Button
              size="small"
              severity="primary"
              :label="t('workflow.addDeputyBtn')"
              data-testid="wf-add-deputy-btn"
              :loading="submittingDeputy"
              @click="handleCreateDeputy"
            >
              <template #icon>
                <PlusCircle :size="15" class="btn-icon" />
              </template>
            </Button>
          </div>
        </div>

        <!-- My Deputies Table -->
        <div class="deputy-list-card">
          <h3 class="subsection-title">{{ t('workflow.deputyTab') }}</h3>
          <div class="table-container">
            <table class="atria-table workflow-table">
              <thead>
                <tr>
                  <th>{{ t('workflow.deputyUserLabel') }}</th>
                  <th>{{ t('workflow.startDate') }} ~ {{ t('workflow.endDate') }}</th>
                  <th>{{ t('workflow.deputyReason') }}</th>
                  <th>{{ t('common.status') }}</th>
                  <th style="width: 140px">{{ t('common.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="d in myDeputiesList" :key="d.id">
                  <td class="font-bold">{{ d.deputyUserName }} (#{{ d.deputyUserId }})</td>
                  <td class="font-mono text-xs">{{ d.startDate }} ~ {{ d.endDate }}</td>
                  <td>{{ d.reason || '—' }}</td>
                  <td>
                    <Tag
                      :value="d.enabled ? t('common.enabled') : t('common.disabled')"
                      :severity="d.enabled ? 'success' : 'secondary'"
                    />
                  </td>
                  <td>
                    <Button
                      v-if="d.enabled"
                      size="small"
                      severity="danger"
                      outlined
                      :label="t('workflow.disableDeputyBtn')"
                      :loading="disablingDeputy[d.id]"
                      @click="handleDisableDeputy(d.id)"
                    />
                    <span v-else class="text-muted text-xs">—</span>
                  </td>
                </tr>
                <tr v-if="myDeputiesList.length === 0">
                  <td colspan="5" class="empty-table-cell text-muted">
                    {{ loadingDeputies ? t('common.loading') : t('common.noData') }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Instance Detail & Action Dialog -->
    <Dialog
      v-model:visible="detailVisible"
      modal
      :header="instanceDetail ? `${instanceDetail.instanceNo} - ${instanceDetail.title}` : t('workflow.viewDetail')"
      :style="{ width: '780px', maxWidth: '95vw' }"
    >
      <div v-if="loadingDetail" class="dialog-loading">
        <span>{{ t('common.loading') }}</span>
      </div>

      <div v-else-if="instanceDetail" class="instance-detail-body">
        <!-- Instance Metadata Grid -->
        <div class="instance-meta-card">
          <div class="meta-row">
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.instanceNo') }}</span>
              <span class="meta-val font-mono font-bold">{{ instanceDetail.instanceNo }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.targetType') }}</span>
              <span class="meta-val">
                <Tag :value="t('workflow.targetTypes.' + instanceDetail.targetType)" severity="info" />
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('common.status') }}</span>
              <span class="meta-val">
                <Tag
                  :value="t('workflow.statuses.' + instanceDetail.status)"
                  :severity="getWorkflowStatusSeverity(instanceDetail.status)"
                />
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.submissionCount') }}</span>
              <span class="meta-val font-mono">{{ instanceDetail.submissionCount }}</span>
            </div>
          </div>

          <div class="meta-row">
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.applicant') }}</span>
              <span class="meta-val font-bold">{{ instanceDetail.applicantName }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.department') }}</span>
              <span class="meta-val">{{ instanceDetail.departmentName }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.currentApprover') }}</span>
              <span class="meta-val font-bold text-primary">{{ instanceDetail.currentApproverName || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('workflow.currentStep') }}</span>
              <span class="meta-val font-mono">#{{ instanceDetail.currentStepOrder ?? 1 }}</span>
            </div>
          </div>
        </div>

        <!-- Dynamic Form Fields & Values Display -->
        <div
          v-if="instanceDetail.formDataJson && Object.keys(instanceDetail.formDataJson).length > 0"
          class="detail-section"
        >
          <h4 class="section-title">{{ t('workflow.portalTitle') }}</h4>
          <div class="form-data-grid">
            <div
              v-for="(val, key) in instanceDetail.formDataJson"
              :key="key"
              class="form-data-box"
            >
              <span class="data-key font-bold">{{ getDetailFieldLabel(key, instanceDetail.schemaSnapshotJson) }}</span>
              <span class="data-val">{{ formatDiffValue(val) }}</span>
            </div>
          </div>
        </div>

        <!-- Return-to-Draft Revision Diff Table -->
        <div
          v-if="instanceDetail.diffJson && instanceDetail.diffJson.length > 0"
          class="detail-section diff-section-box"
          data-testid="wf-diff-section"
        >
          <div class="diff-header-line">
            <GitCompare :size="16" class="diff-header-icon" />
            <h4 class="section-title diff-title">{{ t('workflow.diffSectionTitle') }}</h4>
          </div>
          <div class="table-container">
            <table class="atria-table diff-table">
              <thead>
                <tr>
                  <th>{{ t('workflow.fieldKey') }}</th>
                  <th>{{ t('workflow.oldValue') }}</th>
                  <th>{{ t('workflow.newValue') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="diff in instanceDetail.diffJson" :key="diff.fieldKey">
                  <td class="font-mono font-bold">{{ diff.fieldKey }}</td>
                  <td class="diff-old-val">{{ formatDiffValue(diff.oldValue) }}</td>
                  <td class="diff-new-val font-bold">{{ formatDiffValue(diff.newValue) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Task Audit Trail -->
        <div class="detail-section">
          <h4 class="section-title">{{ t('workflow.taskLogsTitle') }}</h4>
          <div class="table-container">
            <table class="atria-table task-logs-table">
              <thead>
                <tr>
                  <th style="width: 70px">#</th>
                  <th>{{ t('workflow.currentStep') }}</th>
                  <th>{{ t('common.status') }}</th>
                  <th>{{ t('workflow.applicant') }}</th>
                  <th>{{ t('workflow.deputyBadge') }}</th>
                  <th>{{ t('attendance.requests.reason') }}</th>
                  <th>{{ t('workflow.submittedAt') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="log in (instanceDetail.taskLogs || [])" :key="log.id">
                  <td class="font-mono">#{{ log.stepOrder }}</td>
                  <td>{{ getLocalizedText(log.nodeName, 'Step ' + log.stepOrder) }}</td>
                  <td>
                    <Tag
                      :value="t('workflow.actions.' + log.action)"
                      :severity="getWorkflowActionSeverity(log.action)"
                    />
                  </td>
                  <td>{{ log.actorUserName }}</td>
                  <td>
                    <span v-if="log.delegatorUserId" class="deputy-pill">
                      <Tag :value="t('workflow.deputyBadge')" severity="warn" />
                      <span class="font-mono text-xs">({{ log.delegatorUserName || log.delegatorUserId }})</span>
                    </span>
                    <span v-else class="text-muted">—</span>
                  </td>
                  <td class="comment-cell">
                    <span v-if="log.comment" class="comment-text">"{{ log.comment }}"</span>
                    <span v-else class="text-muted">—</span>
                  </td>
                  <td class="font-mono text-xs">{{ formatTimestamp(log.actedAt) }}</td>
                </tr>
                <tr v-if="!instanceDetail.taskLogs || instanceDetail.taskLogs.length === 0">
                  <td colspan="7" class="empty-table-cell text-muted">{{ t('common.noData') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Action Footer with Comment & Buttons -->
      <template #footer>
        <div v-if="instanceDetail" class="dialog-action-footer">
          <div class="comment-input-area">
            <InputText
              v-model="actionComment"
              :placeholder="t('workflow.commentPlaceholder')"
              class="w-full action-comment-field"
            />
          </div>

          <div class="action-buttons-group">
            <template v-if="instanceDetail.status === 'PENDING'">
              <Button
                size="small"
                severity="success"
                :label="t('workflow.approveBtn')"
                data-testid="wf-approve-btn"
                :loading="submittingAction"
                @click="handleApprove"
              >
                <template #icon>
                  <CheckCircle2 :size="15" class="btn-icon" />
                </template>
              </Button>

              <Button
                size="small"
                severity="danger"
                :label="t('workflow.rejectBtn')"
                data-testid="wf-reject-btn"
                :loading="submittingAction"
                @click="handleReject"
              >
                <template #icon>
                  <XCircle :size="15" class="btn-icon" />
                </template>
              </Button>

              <Button
                v-if="instanceDetail.targetType === 'DYNAMIC_FORM'"
                size="small"
                severity="warn"
                outlined
                :label="t('workflow.returnToDraftBtn')"
                data-testid="wf-return-draft-btn"
                :loading="submittingAction"
                @click="handleReturnToDraft"
              >
                <template #icon>
                  <Undo2 :size="15" class="btn-icon" />
                </template>
              </Button>

              <Button
                v-if="isCurrentApplicant(instanceDetail)"
                size="small"
                severity="secondary"
                outlined
                :label="t('workflow.withdrawBtn')"
                :loading="submittingAction"
                @click="handleWithdraw"
              />
            </template>

            <template v-else-if="instanceDetail.status === 'APPROVED'">
              <Button
                size="small"
                severity="danger"
                outlined
                :label="t('workflow.revokeBtn')"
                :loading="submittingAction"
                @click="handleRevoke"
              />
            </template>

            <Button
              size="small"
              severity="secondary"
              outlined
              :label="t('common.close')"
              @click="detailVisible = false"
            />
          </div>
        </div>
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.portal-workflow-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.75rem;
}

.panel-title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.panel-subtitle {
  margin: 0.25rem 0 0;
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #64748b);
}

.panel-tabs-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  padding-bottom: 0.25rem;
  overflow-x: auto;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.85rem;
  border: none;
  background: transparent;
  color: var(--atria-text-muted, #475569);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.tab-btn:hover {
  background: var(--atria-surface-subtle, #f8fafc);
  color: var(--atria-text, #0f172a);
}

.tab-btn-active {
  background: #eff6ff;
  color: #1e40af;
  font-weight: 700;
}

.tab-badge {
  font-size: 0.6875rem;
  background: #ef4444;
  color: #ffffff;
  padding: 0.0625rem 0.35rem;
  border-radius: 999px;
  font-weight: 700;
}

.tab-content {
  padding-top: 0.5rem;
}

.table-container {
  overflow-x: auto;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
}

.workflow-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.workflow-table th,
.workflow-table td {
  padding: 0.5rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.workflow-table th {
  background: var(--atria-surface-subtle, #f8fafc);
  font-weight: 600;
  color: #475569;
}

.target-type-pill {
  font-size: 0.75rem;
  background: #f1f5f9;
  color: #334155;
  padding: 0.125rem 0.4rem;
  border-radius: 4px;
}

.row-actions-group {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.empty-table-cell {
  text-align: center;
  padding: 2rem 1rem !important;
}

/* Dynamic Form Container */
.dynamic-form-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 800px;
}

.draft-edit-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
}

.alert-info-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #92400e;
  font-size: 0.875rem;
}

.form-row-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-field label {
  font-size: 0.8125rem;
  color: #334155;
}

.required-star {
  color: #dc2626;
  font-weight: 700;
}

.version-display-box {
  padding: 0.5rem 0.75rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #cbd5e1);
  border-radius: 6px;
  font-size: 0.875rem;
  color: #475569;
}

.schema-fields-panel {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
}

.dynamic-form-actions-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

.btn-icon {
  margin-right: 0.35rem;
}

/* Deputy Tab */
.deputy-container {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.deputy-form-card {
  padding: 1rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.subsection-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.deputy-form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
}

.span-full {
  grid-column: 1 / -1;
}

.deputy-form-action {
  display: flex;
  justify-content: flex-end;
}

.deputy-list-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* Instance Detail Dialog */
.dialog-loading {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

.instance-detail-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.instance-meta-card {
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.meta-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.meta-label {
  font-size: 0.6875rem;
  color: #64748b;
  font-weight: 500;
}

.meta-val {
  font-size: 0.8125rem;
  color: #0f172a;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.section-title {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 700;
  color: #0f172a;
}

.form-data-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 0.5rem;
}

.form-data-box {
  padding: 0.5rem 0.65rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.data-key {
  font-size: 0.75rem;
  color: #64748b;
}

.data-val {
  font-size: 0.8125rem;
  color: #0f172a;
  word-break: break-all;
}

/* Diff Section */
.diff-section-box {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  padding: 0.75rem;
}

.diff-header-line {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: #92400e;
  margin-bottom: 0.35rem;
}

.diff-header-icon {
  color: #d97706;
}

.diff-title {
  color: #92400e;
}

.diff-table {
  background: #ffffff;
}

.diff-old-val {
  color: #dc2626;
  text-decoration: line-through;
  font-family: monospace;
  font-size: 0.75rem;
}

.diff-new-val {
  color: #16a34a;
  font-family: monospace;
  font-size: 0.75rem;
}

/* Task Logs */
.task-logs-table {
  width: 100%;
}

.deputy-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.comment-cell {
  max-width: 200px;
}

.comment-text {
  font-style: italic;
  color: #1e40af;
  font-size: 0.75rem;
}

/* Dialog Action Footer */
.dialog-action-footer {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
}

.comment-input-area {
  width: 100%;
}

.action-buttons-group {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  flex-wrap: wrap;
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

@media (max-width: 768px) {
  .form-row-2col,
  .deputy-form-grid,
  .meta-row {
    grid-template-columns: 1fr;
  }
}
</style>
