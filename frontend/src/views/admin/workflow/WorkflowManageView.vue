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
  FileText,
  GitBranch,
  Inbox,
  BellRing,
  CheckCircle
} from 'lucide-vue-next'

import {
  workflowApi,
  type FormTemplateDto,
  type FormTemplateUpsertPayload,
  type FormFieldSchema,
  type FlowDefinitionDto,
  type FlowDefinitionDetailDto,
  type FlowNodeDto,
  type WorkflowInstanceDto,
  type WorkflowInstanceDetailDto,
  type WorkflowInstanceStatus,
  type WorkflowTargetType,
  type ConditionOperator
} from '@/api/workflow'
import { notificationApi, type DigestBatchResult } from '@/api/notification'
import { useAuthStore } from '@/stores/auth'

type ActiveTab = 'templates' | 'flows' | 'instances' | 'digest'

const { t, locale } = useI18n()
const toast = useToast()
const authStore = useAuthStore()

const activeTab = ref<ActiveTab>('templates')

const canWrite = computed<boolean>(() => {
  return (
    authStore.hasPermission('wf:template:write') ||
    authStore.hasPermission('wf:instance:reassign') ||
    authStore.roles.includes('SUPER_ADMIN') ||
    authStore.roles.includes('HR_ADMIN') ||
    authStore.roles.includes('HR_MANAGER')
  )
})

function getLocalizedName(nameObj?: Record<string, string> | null): string {
  if (!nameObj) return ''
  const cur = locale.value
  return nameObj[cur] || nameObj['zh-TW'] || nameObj['en-US'] || nameObj['ja-JP'] || Object.values(nameObj)[0] || ''
}

// Static I18n Label Helpers
function getStatusLabel(status: WorkflowInstanceStatus): string {
  const map: Record<WorkflowInstanceStatus, string> = {
    DRAFT: t('workflow.statuses.DRAFT'),
    PENDING: t('workflow.statuses.PENDING'),
    APPROVED: t('workflow.statuses.APPROVED'),
    REJECTED: t('workflow.statuses.REJECTED'),
    CANCELLED: t('workflow.statuses.CANCELLED'),
    REVOKED: t('workflow.statuses.REVOKED')
  }
  return map[status] || status
}

function getStatusSeverity(status: WorkflowInstanceStatus): 'secondary' | 'info' | 'success' | 'danger' | 'warn' | 'contrast' {
  switch (status) {
    case 'APPROVED':
      return 'success'
    case 'PENDING':
      return 'info'
    case 'DRAFT':
      return 'secondary'
    case 'REJECTED':
      return 'danger'
    case 'CANCELLED':
      return 'warn'
    case 'REVOKED':
      return 'contrast'
    default:
      return 'secondary'
  }
}

function getTargetTypeLabel(type: WorkflowTargetType): string {
  const map: Record<WorkflowTargetType, string> = {
    ATT_LEAVE: t('workflow.targetTypes.ATT_LEAVE'),
    ATT_OVERTIME: t('workflow.targetTypes.ATT_OVERTIME'),
    ATT_PUNCH_CORRECTION: t('workflow.targetTypes.ATT_PUNCH_CORRECTION'),
    DYNAMIC_FORM: t('workflow.targetTypes.DYNAMIC_FORM')
  }
  return map[type] || type
}

function getTargetTypeSeverity(type: WorkflowTargetType): 'secondary' | 'info' | 'success' | 'warn' {
  switch (type) {
    case 'ATT_LEAVE':
      return 'info'
    case 'ATT_OVERTIME':
      return 'warn'
    case 'ATT_PUNCH_CORRECTION':
      return 'secondary'
    case 'DYNAMIC_FORM':
      return 'success'
    default:
      return 'secondary'
  }
}

function getActionLabel(action: string): string {
  const map: Record<string, string> = {
    SUBMIT: t('workflow.actions.SUBMIT'),
    RESUBMIT: t('workflow.actions.RESUBMIT'),
    APPROVE: t('workflow.actions.APPROVE'),
    REJECT: t('workflow.actions.REJECT'),
    RETURN_TO_DRAFT: t('workflow.actions.RETURN_TO_DRAFT'),
    CANCEL: t('workflow.actions.CANCEL'),
    REVOKE: t('workflow.actions.REVOKE'),
    REASSIGN: t('workflow.actions.REASSIGN'),
    AUTO_SKIP: t('workflow.actions.AUTO_SKIP')
  }
  return map[action] || action
}

const approverStrategyOptions = computed(() => [
  { label: t('workflow.strategies.DIRECT_LEADER'), value: 'DIRECT_LEADER' },
  { label: t('workflow.strategies.PARENT_LEADER'), value: 'PARENT_LEADER' },
  { label: t('workflow.strategies.SPECIFIC_ROLE'), value: 'SPECIFIC_ROLE' }
])

const fieldTypeOptions = [
  { label: 'TEXT', value: 'TEXT' },
  { label: 'TEXTAREA', value: 'TEXTAREA' },
  { label: 'NUMBER', value: 'NUMBER' },
  { label: 'SELECT', value: 'SELECT' },
  { label: 'DATE', value: 'DATE' }
]

const operatorOptions: { label: string; value: ConditionOperator }[] = [
  { label: '== (EQ)', value: 'EQ' },
  { label: '!= (NE)', value: 'NE' },
  { label: '> (GT)', value: 'GT' },
  { label: '>= (GTE)', value: 'GTE' },
  { label: '< (LT)', value: 'LT' },
  { label: '<= (LTE)', value: 'LTE' },
  { label: 'IN (IN)', value: 'IN' }
]

// ==========================================
// Tab 1: Form Templates (Schema)
// ==========================================
const templates = ref<FormTemplateDto[]>([])
const loadingTemplates = ref<boolean>(false)
const templateDialogVisible = ref<boolean>(false)
const editingTemplateId = ref<number | null>(null)
const savingTemplate = ref<boolean>(false)

const templateForm = ref<{
  code: string
  nameZh: string
  nameEn: string
  nameJa: string
  category: string
  enabled: boolean
  description: string
  schemaJson: FormFieldSchema[]
}>({
  code: '',
  nameZh: '',
  nameEn: '',
  nameJa: '',
  category: 'GENERAL',
  enabled: true,
  description: '',
  schemaJson: []
})

async function loadTemplates() {
  loadingTemplates.value = true
  try {
    templates.value = await workflowApi.listTemplates(false)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingTemplates.value = false
  }
}

function openAddTemplateDialog() {
  editingTemplateId.value = null
  templateForm.value = {
    code: '',
    nameZh: '',
    nameEn: '',
    nameJa: '',
    category: 'GENERAL',
    enabled: true,
    description: '',
    schemaJson: []
  }
  templateDialogVisible.value = true
}

function openEditTemplateDialog(tpl: FormTemplateDto) {
  editingTemplateId.value = tpl.id
  templateForm.value = {
    code: tpl.code,
    nameZh: tpl.name?.['zh-TW'] || '',
    nameEn: tpl.name?.['en-US'] || '',
    nameJa: tpl.name?.['ja-JP'] || '',
    category: tpl.category || 'GENERAL',
    enabled: tpl.enabled,
    description: tpl.description || '',
    schemaJson: JSON.parse(JSON.stringify(tpl.schemaJson || []))
  }
  templateDialogVisible.value = true
}

function addFormField() {
  const index = templateForm.value.schemaJson.length + 1
  templateForm.value.schemaJson.push({
    key: `field_${index}`,
    type: 'TEXT',
    label: {
      'zh-TW': `欄位 ${index}`,
      'en-US': `Field ${index}`,
      'ja-JP': `項目 ${index}`
    },
    required: false
  })
}

function removeFormField(index: number) {
  templateForm.value.schemaJson.splice(index, 1)
}

async function handleSaveTemplate() {
  if (!templateForm.value.code.trim() || !templateForm.value.nameZh.trim()) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }

  savingTemplate.value = true
  try {
    const payload: FormTemplateUpsertPayload = {
      code: templateForm.value.code.trim().toUpperCase(),
      name: {
        'zh-TW': templateForm.value.nameZh.trim(),
        'en-US': templateForm.value.nameEn.trim() || templateForm.value.nameZh.trim(),
        'ja-JP': templateForm.value.nameJa.trim() || templateForm.value.nameZh.trim()
      },
      category: templateForm.value.category.trim() || 'GENERAL',
      schemaJson: templateForm.value.schemaJson,
      enabled: templateForm.value.enabled,
      description: templateForm.value.description.trim() || null
    }

    if (editingTemplateId.value) {
      await workflowApi.updateTemplate(editingTemplateId.value, payload)
    } else {
      await workflowApi.createTemplate(payload)
    }

    toast.add({ severity: 'success', summary: t('common.success'), life: 2500 })
    templateDialogVisible.value = false
    await loadTemplates()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3500 })
  } finally {
    savingTemplate.value = false
  }
}

// ==========================================
// Tab 2: Flow Definitions & Condition Nodes
// ==========================================
const flows = ref<FlowDefinitionDto[]>([])
const selectedFlowId = ref<number | null>(null)
const flowDetail = ref<FlowDefinitionDetailDto | null>(null)
const loadingFlows = ref<boolean>(false)
const loadingFlowDetail = ref<boolean>(false)
const savingFlowNodes = ref<boolean>(false)
const editingNodes = ref<FlowNodeDto[]>([])

async function loadFlows() {
  loadingFlows.value = true
  try {
    flows.value = await workflowApi.listFlows()
    if (flows.value.length > 0) {
      if (!selectedFlowId.value || !flows.value.some((f) => f.id === selectedFlowId.value)) {
        await selectFlow(flows.value[0].id)
      } else {
        await selectFlow(selectedFlowId.value)
      }
    } else {
      flowDetail.value = null
      editingNodes.value = []
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingFlows.value = false
  }
}

async function selectFlow(id: number) {
  selectedFlowId.value = id
  loadingFlowDetail.value = true
  try {
    const detail = await workflowApi.getFlowDetail(id)
    flowDetail.value = detail
    editingNodes.value = JSON.parse(JSON.stringify(detail.nodes || []))
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingFlowDetail.value = false
  }
}

function addFlowNode() {
  const nextOrder = editingNodes.value.length + 1
  editingNodes.value.push({
    stepOrder: nextOrder,
    nodeCode: `STEP_${nextOrder}`,
    name: {
      'zh-TW': `簽核關卡 ${nextOrder}`,
      'en-US': `Approval Step ${nextOrder}`,
      'ja-JP': `承認ステップ ${nextOrder}`
    },
    approverStrategy: 'DIRECT_LEADER',
    roleCode: null,
    selfApprovalAutoSkip: true,
    allowDeputy: true,
    conditions: []
  })
}

function removeFlowNode(index: number) {
  editingNodes.value.splice(index, 1)
  editingNodes.value.forEach((node, idx) => {
    node.stepOrder = idx + 1
  })
}

function addConditionToNode(node: FlowNodeDto) {
  if (!node.conditions) {
    node.conditions = []
  }
  node.conditions.push({
    logicalGroup: 1,
    fieldKey: '',
    operator: 'EQ',
    expectedValue: ''
  })
}

function removeConditionFromNode(node: FlowNodeDto, condIdx: number) {
  node.conditions.splice(condIdx, 1)
}

async function handleSaveFlowNodes() {
  if (!selectedFlowId.value) return

  savingFlowNodes.value = true
  try {
    const updated = await workflowApi.updateFlowNodes(selectedFlowId.value, editingNodes.value)
    flowDetail.value = updated
    editingNodes.value = JSON.parse(JSON.stringify(updated.nodes || []))
    toast.add({ severity: 'success', summary: t('common.success'), life: 2500 })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3500 })
  } finally {
    savingFlowNodes.value = false
  }
}

// ==========================================
// Tab 3: All Instances Monitor & Reassign / Revoke
// ==========================================
const instances = ref<WorkflowInstanceDto[]>([])
const loadingInstances = ref<boolean>(false)
const filterStatus = ref<WorkflowInstanceStatus | ''>('')
const filterTargetType = ref<WorkflowTargetType | ''>('')

const statusFilterOptions = computed(() => [
  { label: t('common.all'), value: '' },
  { label: t('workflow.statuses.DRAFT'), value: 'DRAFT' },
  { label: t('workflow.statuses.PENDING'), value: 'PENDING' },
  { label: t('workflow.statuses.APPROVED'), value: 'APPROVED' },
  { label: t('workflow.statuses.REJECTED'), value: 'REJECTED' },
  { label: t('workflow.statuses.CANCELLED'), value: 'CANCELLED' },
  { label: t('workflow.statuses.REVOKED'), value: 'REVOKED' }
])

const targetTypeFilterOptions = computed(() => [
  { label: t('common.all'), value: '' },
  { label: t('workflow.targetTypes.ATT_LEAVE'), value: 'ATT_LEAVE' },
  { label: t('workflow.targetTypes.ATT_OVERTIME'), value: 'ATT_OVERTIME' },
  { label: t('workflow.targetTypes.ATT_PUNCH_CORRECTION'), value: 'ATT_PUNCH_CORRECTION' },
  { label: t('workflow.targetTypes.DYNAMIC_FORM'), value: 'DYNAMIC_FORM' }
])

async function loadInstances() {
  loadingInstances.value = true
  try {
    instances.value = await workflowApi.getAllInstances({
      status: filterStatus.value || undefined,
      targetType: filterTargetType.value || undefined
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingInstances.value = false
  }
}

// Instance Detail Dialog
const detailDialogVisible = ref<boolean>(false)
const loadingDetail = ref<boolean>(false)
const selectedDetail = ref<WorkflowInstanceDetailDto | null>(null)

async function openInstanceDetail(id: number) {
  detailDialogVisible.value = true
  loadingDetail.value = true
  try {
    selectedDetail.value = await workflowApi.getInstanceDetail(id)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loadingDetail.value = false
  }
}

// Reassign Task Dialog
const reassignDialogVisible = ref<boolean>(false)
const reassigning = ref<boolean>(false)
const reassignInstanceId = ref<number | null>(null)
const reassignNewApproverUserId = ref<number | null>(null)
const reassignComment = ref<string>('')

function openReassignDialog(item: WorkflowInstanceDto) {
  reassignInstanceId.value = item.id
  reassignNewApproverUserId.value = null
  reassignComment.value = ''
  reassignDialogVisible.value = true
}

async function handleConfirmReassign() {
  if (!reassignInstanceId.value || !reassignNewApproverUserId.value) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.error'), life: 3000 })
    return
  }

  reassigning.value = true
  try {
    await workflowApi.reassignTask(
      reassignInstanceId.value,
      reassignNewApproverUserId.value,
      reassignComment.value.trim() || undefined
    )
    toast.add({ severity: 'success', summary: t('common.success'), life: 2500 })
    reassignDialogVisible.value = false
    await loadInstances()
    if (detailDialogVisible.value && selectedDetail.value?.id === reassignInstanceId.value) {
      await openInstanceDetail(reassignInstanceId.value)
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3500 })
  } finally {
    reassigning.value = false
  }
}

// Revoke Instance Dialog
const revokeDialogVisible = ref<boolean>(false)
const revoking = ref<boolean>(false)
const revokeInstanceId = ref<number | null>(null)
const revokeComment = ref<string>('')

function openRevokeDialog(item: WorkflowInstanceDto) {
  revokeInstanceId.value = item.id
  revokeComment.value = ''
  revokeDialogVisible.value = true
}

async function handleConfirmRevoke() {
  if (!revokeInstanceId.value) return

  revoking.value = true
  try {
    await workflowApi.revokeInstance(revokeInstanceId.value, revokeComment.value.trim() || undefined)
    toast.add({ severity: 'success', summary: t('common.success'), life: 2500 })
    revokeDialogVisible.value = false
    await loadInstances()
    if (detailDialogVisible.value && selectedDetail.value?.id === revokeInstanceId.value) {
      await openInstanceDetail(revokeInstanceId.value)
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3500 })
  } finally {
    revoking.value = false
  }
}

// ==========================================
// Tab 4: Monthly Leave Balance Digest Job
// ==========================================
const digestPeriod = ref<string>(new Date().toISOString().substring(0, 7))
const triggeringDigest = ref<boolean>(false)
const digestResult = ref<DigestBatchResult | null>(null)

async function handleTriggerDigest() {
  triggeringDigest.value = true
  try {
    const res = await notificationApi.triggerMonthlyDigest(digestPeriod.value)
    digestResult.value = res
    toast.add({ severity: 'success', summary: t('common.success'), life: 3000 })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3500 })
  } finally {
    triggeringDigest.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'templates') {
    loadTemplates()
  } else if (tab === 'flows') {
    loadFlows()
  } else if (tab === 'instances') {
    loadInstances()
  }
})

onMounted(() => {
  loadTemplates()
})
</script>

<template>
  <div class="workflow-manage-view" data-testid="admin-workflow-manage-view">
    <!-- Top Header -->
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('workflow.adminTitle') }}</h1>
        <p class="atria-page-subtitle">{{ t('workflow.adminSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          icon="pi pi-refresh"
          :label="t('common.refresh')"
          severity="secondary"
          outlined
          size="small"
          @click="() => {
            if (activeTab === 'templates') loadTemplates()
            else if (activeTab === 'flows') loadFlows()
            else if (activeTab === 'instances') loadInstances()
          }"
        />
      </div>
    </div>

    <!-- Navigation Tabs Bar -->
    <div class="tabs-nav-bar">
      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'templates' }"
        data-testid="wf-admin-tab-templates"
        @click="activeTab = 'templates'"
      >
        <FileText :size="15" />
        <span>{{ t('workflow.templatesTab') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'flows' }"
        data-testid="wf-admin-tab-flows"
        @click="activeTab = 'flows'"
      >
        <GitBranch :size="15" />
        <span>{{ t('workflow.flowsTab') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'instances' }"
        data-testid="wf-admin-tab-instances"
        @click="activeTab = 'instances'"
      >
        <Inbox :size="15" />
        <span>{{ t('workflow.allInstancesTab') }}</span>
      </button>

      <button
        type="button"
        class="tab-nav-btn"
        :class="{ 'tab-nav-active': activeTab === 'digest' }"
        data-testid="wf-admin-tab-digest"
        @click="activeTab = 'digest'"
      >
        <BellRing :size="15" />
        <span>{{ t('workflow.digestJobTab') }}</span>
      </button>
    </div>

    <!-- Tab 1: Form Templates (Schema) -->
    <section v-if="activeTab === 'templates'" class="atria-card tab-content-card">
      <div class="tab-toolbar">
        <div class="toolbar-title-wrap">
          <FileText :size="18" class="text-primary" />
          <h2 class="toolbar-heading">{{ t('workflow.templatesTab') }}</h2>
        </div>
        <Button
          v-if="canWrite"
          size="small"
          severity="primary"
          icon="pi pi-plus"
          :label="t('workflow.addTemplateBtn')"
          data-testid="wf-admin-add-template-btn"
          @click="openAddTemplateDialog"
        />
      </div>

      <div class="table-container">
        <table class="atria-table template-table">
          <thead>
            <tr>
              <th style="width: 140px">{{ t('workflow.templateCode') }}</th>
              <th>{{ t('iam.role.nameZh') }}</th>
              <th style="width: 120px">{{ t('workflow.templateCategory') }}</th>
              <th style="width: 100px">{{ t('workflow.formVersion') }}</th>
              <th style="width: 90px">{{ t('workflow.fieldCount') }}</th>
              <th style="width: 90px">{{ t('common.status') }}</th>
              <th style="width: 90px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="tpl in templates" :key="tpl.id">
              <td class="font-mono font-bold">{{ tpl.code }}</td>
              <td>
                <div class="name-cell">
                  <span class="font-bold">{{ getLocalizedName(tpl.name) }}</span>
                  <span v-if="tpl.description" class="cell-desc text-muted">{{ tpl.description }}</span>
                </div>
              </td>
              <td>
                <Tag :value="tpl.category" severity="secondary" />
              </td>
              <td>
                <Tag :value="`v${tpl.currentVersion}`" severity="info" />
              </td>
              <td class="font-mono font-bold">
                {{ tpl.schemaJson ? tpl.schemaJson.length : 0 }}
              </td>
              <td>
                <Tag
                  :value="tpl.enabled ? t('common.enabled') : t('common.disabled')"
                  :severity="tpl.enabled ? 'success' : 'danger'"
                />
              </td>
              <td>
                <Button
                  v-if="canWrite"
                  size="small"
                  text
                  icon="pi pi-pencil"
                  :title="t('workflow.editTemplateBtn')"
                  @click="openEditTemplateDialog(tpl)"
                />
              </td>
            </tr>
            <tr v-if="templates.length === 0">
              <td colspan="7" class="text-center text-muted empty-cell">
                {{ loadingTemplates ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Tab 2: Flow Definitions & Condition Nodes -->
    <section v-else-if="activeTab === 'flows'" class="atria-card tab-content-card">
      <div class="flow-layout">
        <!-- Flow List Sidebar / Selector -->
        <div class="flow-sidebar">
          <div class="sidebar-header">
            <GitBranch :size="16" class="text-primary" />
            <span class="font-bold">{{ t('workflow.flowsTab') }}</span>
          </div>
          <div class="flow-list">
            <button
              v-for="flow in flows"
              :key="flow.id"
              type="button"
              class="flow-item-btn"
              :class="{ 'flow-item-active': selectedFlowId === flow.id }"
              @click="selectFlow(flow.id)"
            >
              <div class="flow-item-code font-mono">{{ flow.code }}</div>
              <div class="flow-item-name">{{ getLocalizedName(flow.name) }}</div>
              <div class="flow-item-meta">
                <Tag :value="getTargetTypeLabel(flow.targetType)" :severity="getTargetTypeSeverity(flow.targetType)" />
              </div>
            </button>
            <div v-if="flows.length === 0" class="text-muted text-center p-4">
              {{ loadingFlows ? t('common.loading') : t('common.noData') }}
            </div>
          </div>
        </div>

        <!-- Flow Detail & Nodes Area -->
        <div class="flow-main" v-if="flowDetail">
          <div class="flow-toolbar">
            <div class="flow-info">
              <h2 class="flow-title">{{ getLocalizedName(flowDetail.name) }}</h2>
              <span class="font-mono text-xs text-muted">{{ flowDetail.code }}</span>
              <Tag
                :value="flowDetail.enabled ? t('common.enabled') : t('common.disabled')"
                :severity="flowDetail.enabled ? 'success' : 'danger'"
              />
            </div>
            <div class="flow-actions" v-if="canWrite">
              <Button
                size="small"
                severity="secondary"
                outlined
                icon="pi pi-plus"
                :label="t('workflow.addNodeBtn')"
                @click="addFlowNode"
              />
              <Button
                size="small"
                severity="primary"
                icon="pi pi-check"
                :label="t('workflow.saveFlowNodesBtn')"
                :loading="savingFlowNodes"
                data-testid="wf-admin-save-flow-btn"
                @click="handleSaveFlowNodes"
              />
            </div>
          </div>

          <!-- Nodes Step List -->
          <div class="nodes-container">
            <div
              v-for="(node, nodeIdx) in editingNodes"
              :key="nodeIdx"
              class="node-card"
            >
              <div class="node-header">
                <div class="node-step-badge">
                  <span>#{{ node.stepOrder }}</span>
                </div>
                <div class="node-title-inputs">
                  <div class="input-inline">
                    <label class="text-xs text-muted">{{ t('workflow.nodeCode') }}</label>
                    <InputText v-model="node.nodeCode" class="font-mono text-sm w-36" />
                  </div>
                  <div class="input-inline">
                    <label class="text-xs text-muted">{{ t('workflow.fieldLabelZh') }}</label>
                    <InputText v-model="node.name['zh-TW']" class="text-sm w-44" />
                  </div>
                  <div class="input-inline">
                    <label class="text-xs text-muted">{{ t('workflow.fieldLabelEn') }}</label>
                    <InputText v-model="node.name['en-US']" class="text-sm w-44" />
                  </div>
                  <div class="input-inline">
                    <label class="text-xs text-muted">{{ t('workflow.fieldLabelJa') }}</label>
                    <InputText v-model="node.name['ja-JP']" class="text-sm w-44" />
                  </div>
                </div>
                <Button
                  v-if="canWrite"
                  size="small"
                  text
                  severity="danger"
                  icon="pi pi-trash"
                  :title="t('common.delete')"
                  @click="removeFlowNode(nodeIdx)"
                />
              </div>

              <!-- Node Configuration Grid -->
              <div class="node-config-grid">
                <div class="config-field">
                  <label class="config-label">{{ t('workflow.approverStrategy') }}</label>
                  <Select
                    v-model="node.approverStrategy"
                    :options="approverStrategyOptions"
                    optionLabel="label"
                    optionValue="value"
                    class="w-full text-sm"
                  />
                </div>

                <div class="config-field" v-if="node.approverStrategy === 'SPECIFIC_ROLE'">
                  <label class="config-label">{{ t('workflow.roleCode') }}</label>
                  <InputText
                    v-model="node.roleCode"
                    class="font-mono text-sm w-full"
                    placeholder="e.g. HR_MANAGER"
                  />
                </div>

                <div class="config-field-checkbox">
                  <div class="checkbox-line">
                    <Checkbox
                      v-model="node.selfApprovalAutoSkip"
                      binary
                      :inputId="`auto-skip-${nodeIdx}`"
                    />
                    <label :for="`auto-skip-${nodeIdx}`" class="text-xs font-semibold cursor-pointer">
                      {{ t('workflow.selfApprovalAutoSkip') }}
                    </label>
                  </div>
                  <div class="checkbox-line">
                    <Checkbox
                      v-model="node.allowDeputy"
                      binary
                      :inputId="`allow-deputy-${nodeIdx}`"
                    />
                    <label :for="`allow-deputy-${nodeIdx}`" class="text-xs font-semibold cursor-pointer">
                      {{ t('workflow.allowDeputy') }}
                    </label>
                  </div>
                </div>
              </div>

              <!-- Conditions Sub-Section -->
              <div class="node-conditions-box">
                <div class="conditions-header">
                  <div class="flex items-center gap-2">
                    <GitBranch :size="14" class="text-muted" />
                    <span class="text-xs font-bold text-slate-700">{{ t('workflow.conditionsTitle') }}</span>
                  </div>
                  <Button
                    v-if="canWrite"
                    size="small"
                    text
                    icon="pi pi-plus"
                    :label="t('workflow.addConditionBtn')"
                    @click="addConditionToNode(node)"
                  />
                </div>

                <div v-if="node.conditions && node.conditions.length > 0" class="conditions-table-wrap">
                  <table class="conditions-table">
                    <thead>
                      <tr>
                        <th style="width: 110px">{{ t('workflow.logicalGroup') }}</th>
                        <th>{{ t('workflow.fieldKey') }}</th>
                        <th style="width: 140px">{{ t('workflow.operator') }}</th>
                        <th>{{ t('workflow.expectedValue') }}</th>
                        <th style="width: 50px"></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(cond, condIdx) in node.conditions" :key="condIdx">
                        <td>
                          <InputNumber
                            v-model="cond.logicalGroup"
                            :min="1"
                            class="w-full text-xs"
                          />
                        </td>
                        <td>
                          <InputText
                            v-model="cond.fieldKey"
                            placeholder="e.g. days / amount"
                            class="font-mono text-xs w-full"
                          />
                        </td>
                        <td>
                          <Select
                            v-model="cond.operator"
                            :options="operatorOptions"
                            optionLabel="label"
                            optionValue="value"
                            class="w-full text-xs"
                          />
                        </td>
                        <td>
                          <InputText
                            v-model="cond.expectedValue"
                            placeholder="e.g. 3 / TRUE"
                            class="font-mono text-xs w-full"
                          />
                        </td>
                        <td class="text-center">
                          <Button
                            size="small"
                            text
                            severity="danger"
                            icon="pi pi-times"
                            :title="t('common.delete')"
                            @click="removeConditionFromNode(node, condIdx)"
                          />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div v-else class="conditions-empty text-muted text-xs">
                  {{ t('common.noData') }} (Pass-through unconditional step)
                </div>
              </div>
            </div>

            <div v-if="editingNodes.length === 0" class="empty-cell text-center text-muted">
              {{ t('common.noData') }}
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Tab 3: All Instances Monitor & Reassign / Revoke -->
    <section v-else-if="activeTab === 'instances'" class="atria-card tab-content-card">
      <div class="tab-toolbar">
        <div class="toolbar-left">
          <div class="toolbar-item">
            <label class="item-label">{{ t('workflow.targetType') }}</label>
            <Select
              v-model="filterTargetType"
              :options="targetTypeFilterOptions"
              optionLabel="label"
              optionValue="value"
              class="w-44"
              @change="loadInstances"
            />
          </div>

          <div class="toolbar-item">
            <label class="item-label">{{ t('common.status') }}</label>
            <Select
              v-model="filterStatus"
              :options="statusFilterOptions"
              optionLabel="label"
              optionValue="value"
              class="w-40"
              @change="loadInstances"
            />
          </div>
        </div>

        <Button
          size="small"
          severity="secondary"
          outlined
          icon="pi pi-refresh"
          :label="t('common.refresh')"
          @click="loadInstances"
        />
      </div>

      <div class="table-container">
        <table class="atria-table instance-table">
          <thead>
            <tr>
              <th style="width: 140px">{{ t('workflow.instanceNo') }}</th>
              <th style="width: 130px">{{ t('workflow.targetType') }}</th>
              <th>{{ t('workflow.title') }}</th>
              <th style="width: 110px">{{ t('workflow.applicant') }}</th>
              <th style="width: 110px">{{ t('workflow.department') }}</th>
              <th style="width: 100px">{{ t('common.status') }}</th>
              <th style="width: 90px">{{ t('workflow.currentStep') }}</th>
              <th style="width: 110px">{{ t('workflow.currentApprover') }}</th>
              <th style="width: 130px">{{ t('workflow.submittedAt') }}</th>
              <th style="width: 140px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="inst in instances" :key="inst.id">
              <td class="font-mono font-bold">{{ inst.instanceNo }}</td>
              <td>
                <Tag
                  :value="getTargetTypeLabel(inst.targetType)"
                  :severity="getTargetTypeSeverity(inst.targetType)"
                />
              </td>
              <td class="font-bold">{{ inst.title }}</td>
              <td>{{ inst.applicantName }}</td>
              <td class="text-xs text-muted">{{ inst.departmentName }}</td>
              <td>
                <Tag
                  :value="getStatusLabel(inst.status)"
                  :severity="getStatusSeverity(inst.status)"
                />
              </td>
              <td class="font-mono text-center">
                {{ inst.currentStepOrder ? `#${inst.currentStepOrder}` : '-' }}
              </td>
              <td>{{ inst.currentApproverName || '-' }}</td>
              <td class="font-mono text-xs">
                {{ inst.submittedAt ? inst.submittedAt.substring(0, 16) : '-' }}
              </td>
              <td>
                <div class="flex items-center gap-1">
                  <Button
                    size="small"
                    text
                    icon="pi pi-eye"
                    :title="t('workflow.viewDetail')"
                    @click="openInstanceDetail(inst.id)"
                  />
                  <Button
                    v-if="canWrite && inst.status === 'PENDING'"
                    size="small"
                    text
                    severity="warn"
                    icon="pi pi-user-edit"
                    :title="t('workflow.reassignBtn')"
                    data-testid="wf-admin-reassign-btn"
                    @click="openReassignDialog(inst)"
                  />
                  <Button
                    v-if="canWrite && inst.status === 'APPROVED'"
                    size="small"
                    text
                    severity="danger"
                    icon="pi pi-ban"
                    :title="t('workflow.revokeBtn')"
                    @click="openRevokeDialog(inst)"
                  />
                </div>
              </td>
            </tr>
            <tr v-if="instances.length === 0">
              <td colspan="10" class="text-center text-muted empty-cell">
                {{ loadingInstances ? t('common.loading') : t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Tab 4: Monthly Leave Balance Digest Job -->
    <section v-else-if="activeTab === 'digest'" class="atria-card tab-content-card">
      <div class="digest-card">
        <div class="digest-header">
          <div class="toolbar-title-wrap">
            <BellRing :size="20" class="text-primary" />
            <div>
              <h2 class="toolbar-heading">{{ t('workflow.digestJobTab') }}</h2>
              <p class="text-xs text-muted mt-1">
                {{ t('workflow.adminSubtitle') }}
              </p>
            </div>
          </div>
        </div>

        <div class="digest-form-box">
          <div class="digest-input-row">
            <div class="dialog-field w-64">
              <label class="item-label">{{ t('workflow.digestPeriodLabel') }} *</label>
              <InputText
                v-model="digestPeriod"
                placeholder="YYYY-MM"
                class="font-mono text-sm w-full"
              />
            </div>
            <Button
              severity="primary"
              icon="pi pi-send"
              :label="t('workflow.triggerDigestBtn')"
              :loading="triggeringDigest"
              data-testid="wf-admin-trigger-digest-btn"
              @click="handleTriggerDigest"
            />
          </div>
        </div>

        <!-- Result Box -->
        <div v-if="digestResult" class="digest-result-box">
          <div class="result-summary-banner">
            <CheckCircle :size="18" class="text-green-600" />
            <span class="font-bold text-sm">
              {{
                t('workflow.digestResultSummary', {
                  total: digestResult.totalActiveUsers,
                  sent: digestResult.sentCount,
                  skipped: digestResult.skippedCount
                })
              }}
            </span>
          </div>

          <div class="stats-grid">
            <div class="stat-box">
              <div class="stat-number text-primary">{{ digestResult.totalActiveUsers }}</div>
              <div class="stat-label">{{ t('iam.user.title') }}</div>
            </div>
            <div class="stat-box">
              <div class="stat-number text-green-600">{{ digestResult.sentCount }}</div>
              <div class="stat-label">{{ t('common.success') }}</div>
            </div>
            <div class="stat-box">
              <div class="stat-number text-slate-500">{{ digestResult.skippedCount }}</div>
              <div class="stat-label">{{ t('workflow.actions.AUTO_SKIP') }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Dialog 1: Dynamic Form Template Create / Edit (Version Bump) -->
    <Dialog
      v-model:visible="templateDialogVisible"
      modal
      :header="editingTemplateId ? t('workflow.editTemplateBtn') : t('workflow.addTemplateBtn')"
      :style="{ width: '840px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="form-row-2">
          <div class="dialog-field">
            <label>{{ t('workflow.templateCode') }} *</label>
            <InputText
              v-model="templateForm.code"
              :disabled="!!editingTemplateId"
              class="w-full font-mono uppercase"
            />
          </div>
          <div class="dialog-field">
            <label>{{ t('workflow.templateCategory') }} *</label>
            <InputText
              v-model="templateForm.category"
              class="w-full uppercase font-mono"
            />
          </div>
        </div>

        <div class="form-row-3">
          <div class="dialog-field">
            <label>{{ t('iam.role.nameZh') }} *</label>
            <InputText v-model="templateForm.nameZh" class="w-full" />
          </div>
          <div class="dialog-field">
            <label>{{ t('iam.role.nameEn') }}</label>
            <InputText v-model="templateForm.nameEn" class="w-full" />
          </div>
          <div class="dialog-field">
            <label>{{ t('iam.role.nameJa') }}</label>
            <InputText v-model="templateForm.nameJa" class="w-full" />
          </div>
        </div>

        <div class="dialog-field">
          <label>{{ t('common.description') }}</label>
          <Textarea v-model="templateForm.description" rows="2" class="w-full text-sm" />
        </div>

        <div class="dialog-field">
          <div class="checkbox-line">
            <Checkbox v-model="templateForm.enabled" binary inputId="tpl-enabled" />
            <label for="tpl-enabled" class="text-sm font-semibold cursor-pointer">
              {{ t('common.enabled') }}
            </label>
          </div>
        </div>

        <!-- Dynamic Form Fields Builder -->
        <div class="fields-builder-box">
          <div class="builder-header">
            <span class="font-bold text-sm text-slate-800">
              JSON Schema {{ t('workflow.fieldCount') }} ({{ templateForm.schemaJson.length }})
            </span>
            <Button
              size="small"
              severity="secondary"
              icon="pi pi-plus"
              :label="t('workflow.addFieldBtn')"
              @click="addFormField"
            />
          </div>

          <div class="fields-list">
            <div
              v-for="(fld, fldIdx) in templateForm.schemaJson"
              :key="fldIdx"
              class="field-item-card"
            >
              <div class="field-item-top">
                <div class="dialog-field w-36">
                  <label class="text-xs">{{ t('workflow.fieldKey') }} *</label>
                  <InputText v-model="fld.key" class="font-mono text-xs w-full" />
                </div>
                <div class="dialog-field w-36">
                  <label class="text-xs">{{ t('workflow.fieldType') }} *</label>
                  <Select
                    v-model="fld.type"
                    :options="fieldTypeOptions"
                    optionLabel="label"
                    optionValue="value"
                    class="text-xs w-full"
                  />
                </div>
                <div class="dialog-field flex-1">
                  <label class="text-xs">{{ t('workflow.fieldLabelZh') }} *</label>
                  <InputText v-model="fld.label['zh-TW']" class="text-xs w-full" />
                </div>
                <div class="dialog-field flex-1">
                  <label class="text-xs">{{ t('workflow.fieldLabelEn') }}</label>
                  <InputText v-model="fld.label['en-US']" class="text-xs w-full" />
                </div>
                <div class="dialog-field flex-1">
                  <label class="text-xs">{{ t('workflow.fieldLabelJa') }}</label>
                  <InputText v-model="fld.label['ja-JP']" class="text-xs w-full" />
                </div>
                <div class="field-checkbox-col">
                  <Checkbox v-model="fld.required" binary :inputId="`fld-req-${fldIdx}`" />
                  <label :for="`fld-req-${fldIdx}`" class="text-xs cursor-pointer font-bold">
                    {{ t('workflow.fieldRequired') }}
                  </label>
                </div>
                <Button
                  size="small"
                  text
                  severity="danger"
                  icon="pi pi-trash"
                  :title="t('common.delete')"
                  @click="removeFormField(fldIdx)"
                />
              </div>
            </div>

            <div v-if="templateForm.schemaJson.length === 0" class="text-center text-muted p-4 text-xs">
              {{ t('common.noData') }}
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="templateDialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="savingTemplate"
          @click="handleSaveTemplate"
        />
      </template>
    </Dialog>

    <!-- Dialog 2: Instance Detail & Timeline -->
    <Dialog
      v-model:visible="detailDialogVisible"
      modal
      :header="t('workflow.viewDetail')"
      :style="{ width: '840px', maxWidth: '95vw' }"
    >
      <div v-if="loadingDetail" class="text-center p-6 text-muted">
        {{ t('common.loading') }}
      </div>
      <div v-else-if="selectedDetail" class="detail-container">
        <!-- Instance Meta Bar -->
        <div class="detail-meta-grid">
          <div class="meta-item">
            <span class="meta-label">{{ t('workflow.instanceNo') }}</span>
            <span class="meta-value font-mono font-bold">{{ selectedDetail.instanceNo }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">{{ t('workflow.targetType') }}</span>
            <Tag
              :value="getTargetTypeLabel(selectedDetail.targetType)"
              :severity="getTargetTypeSeverity(selectedDetail.targetType)"
            />
          </div>
          <div class="meta-item">
            <span class="meta-label">{{ t('common.status') }}</span>
            <Tag
              :value="getStatusLabel(selectedDetail.status)"
              :severity="getStatusSeverity(selectedDetail.status)"
            />
          </div>
          <div class="meta-item">
            <span class="meta-label">{{ t('workflow.applicant') }}</span>
            <span class="meta-value font-bold">{{ selectedDetail.applicantName }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">{{ t('workflow.department') }}</span>
            <span class="meta-value text-muted">{{ selectedDetail.departmentName }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">{{ t('workflow.currentApprover') }}</span>
            <span class="meta-value">{{ selectedDetail.currentApproverName || '-' }}</span>
          </div>
        </div>

        <!-- Form Payload JSON Display -->
        <div class="detail-form-box">
          <h3 class="section-heading">{{ t('workflow.title') }}: {{ selectedDetail.title }}</h3>
          <div class="json-preview font-mono text-xs">
            <pre>{{ JSON.stringify(selectedDetail.formDataJson, null, 2) }}</pre>
          </div>
        </div>

        <!-- Revision Diff Table (If resubmitted) -->
        <div v-if="selectedDetail.diffJson && selectedDetail.diffJson.length > 0" class="diff-section">
          <h3 class="section-heading text-amber-700">{{ t('workflow.diffSectionTitle') }}</h3>
          <table class="atria-table diff-table">
            <thead>
              <tr>
                <th>{{ t('workflow.fieldKey') }}</th>
                <th>{{ t('workflow.oldValue') }}</th>
                <th>{{ t('workflow.newValue') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in selectedDetail.diffJson" :key="d.fieldKey">
                <td class="font-mono font-bold">{{ d.fieldKey }}</td>
                <td class="diff-old-val">{{ String(d.oldValue ?? '-') }}</td>
                <td class="diff-new-val">{{ String(d.newValue ?? '-') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Task Logs Timeline -->
        <div v-if="selectedDetail.taskLogs && selectedDetail.taskLogs.length > 0" class="logs-section">
          <h3 class="section-heading">{{ t('workflow.taskLogsTitle') }}</h3>
          <table class="atria-table logs-table">
            <thead>
              <tr>
                <th style="width: 70px">{{ t('workflow.stepOrder') }}</th>
                <th>{{ t('workflow.nodeCode') }}</th>
                <th style="width: 120px">{{ t('common.actions') }}</th>
                <th>{{ t('workflow.currentApprover') }}</th>
                <th>{{ t('common.note') }}</th>
                <th style="width: 150px">{{ t('common.createdAt') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in selectedDetail.taskLogs" :key="log.id">
                <td class="font-mono">#{{ log.stepOrder }}</td>
                <td>{{ getLocalizedName(log.nodeName) }}</td>
                <td>
                  <Tag :value="getActionLabel(log.action)" severity="info" />
                </td>
                <td>
                  <div>{{ log.actorUserName }}</div>
                  <span v-if="log.delegatorUserName" class="text-xs text-muted">
                    ({{ t('workflow.deputyBadge') }}: {{ log.delegatorUserName }})
                  </span>
                </td>
                <td class="text-xs">{{ log.comment || '-' }}</td>
                <td class="font-mono text-xs">{{ log.actedAt.substring(0, 16) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.close')"
          severity="secondary"
          @click="detailDialogVisible = false"
        />
      </template>
    </Dialog>

    <!-- Dialog 3: HR Force Reassign Task -->
    <Dialog
      v-model:visible="reassignDialogVisible"
      modal
      :header="t('workflow.reassignBtn')"
      :style="{ width: '480px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="dialog-field">
          <label>{{ t('workflow.newApproverId') }} *</label>
          <InputNumber
            v-model="reassignNewApproverUserId"
            :min="1"
            class="w-full"
            placeholder="User ID (e.g. 1001)"
          />
        </div>

        <div class="dialog-field">
          <label>{{ t('common.note') }}</label>
          <Textarea
            v-model="reassignComment"
            rows="3"
            class="w-full text-sm"
            :placeholder="t('workflow.commentPlaceholder')"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="reassignDialogVisible = false"
        />
        <Button
          :label="t('common.confirm')"
          severity="warn"
          :loading="reassigning"
          @click="handleConfirmReassign"
        />
      </template>
    </Dialog>

    <!-- Dialog 4: Post-Approval Revoke Instance -->
    <Dialog
      v-model:visible="revokeDialogVisible"
      modal
      :header="t('workflow.revokeBtn')"
      :style="{ width: '480px', maxWidth: '95vw' }"
    >
      <div class="dialog-form-layout">
        <div class="dialog-field">
          <label>{{ t('common.note') }}</label>
          <Textarea
            v-model="revokeComment"
            rows="3"
            class="w-full text-sm"
            :placeholder="t('workflow.commentPlaceholder')"
          />
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          outlined
          @click="revokeDialogVisible = false"
        />
        <Button
          :label="t('common.confirm')"
          severity="danger"
          :loading="revoking"
          @click="handleConfirmRevoke"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.workflow-manage-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1.5rem;
  max-width: 1400px;
  margin: 0 auto;
}

.atria-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.atria-page-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--atria-text, #0f172a);
}

.atria-page-subtitle {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  color: var(--atria-text-muted, #64748b);
}

/* Tabs Navigation Bar */
.tabs-nav-bar {
  display: flex;
  gap: 0.5rem;
  border-bottom: 2px solid var(--atria-border, #e2e8f0);
  overflow-x: auto;
}

.tab-nav-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--atria-text-muted, #64748b);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
  white-space: nowrap;
}

.tab-nav-btn:hover {
  color: var(--atria-primary, #3b82f6);
}

.tab-nav-active {
  color: var(--atria-primary, #3b82f6);
  border-bottom-color: var(--atria-primary, #3b82f6);
}

/* Card & Content */
.tab-content-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem;
  background: #ffffff;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 8px);
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
  gap: 1rem;
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

.atria-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.atria-table th,
.atria-table td {
  padding: 0.6rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.atria-table th {
  background: var(--atria-surface-subtle, #f8fafc);
  font-weight: 600;
  color: #475569;
}

.atria-table tbody tr:hover {
  background: #f8fafc;
}

.name-cell {
  display: flex;
  flex-direction: column;
}

.cell-desc {
  font-size: 0.75rem;
}

.empty-cell {
  padding: 2.5rem !important;
}

/* Tab 2: Flow Layout */
.flow-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 1.25rem;
  min-height: 520px;
}

@media (max-width: 900px) {
  .flow-layout {
    grid-template-columns: 1fr;
  }
}

.flow-sidebar {
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  background: #ffffff;
  font-size: 0.875rem;
}

.flow-list {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  max-height: 600px;
}

.flow-item-btn {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem 1rem;
  text-align: left;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  cursor: pointer;
  transition: background 0.15s ease-in-out;
}

.flow-item-btn:hover {
  background: #f1f5f9;
}

.flow-item-active {
  background: #e0e7ff !important;
  border-left: 3px solid var(--atria-primary, #3b82f6);
}

.flow-item-code {
  font-size: 0.75rem;
  font-weight: 700;
  color: #1e293b;
}

.flow-item-name {
  font-size: 0.8125rem;
  color: #475569;
}

.flow-item-meta {
  margin-top: 0.25rem;
}

.flow-main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.flow-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
  flex-wrap: wrap;
  gap: 0.75rem;
}

.flow-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.flow-title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
}

.flow-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.nodes-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.node-card {
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: var(--atria-radius, 6px);
  padding: 1rem;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.node-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px dashed var(--atria-border, #e2e8f0);
}

.node-step-badge {
  background: #3b82f6;
  color: #ffffff;
  font-weight: 700;
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}

.node-title-inputs {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  flex: 1;
}

.input-inline {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.node-config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  padding: 0.75rem 0;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.config-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #475569;
}

.config-field-checkbox {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  justify-content: center;
}

.checkbox-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.node-conditions-box {
  background: #f8fafc;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
  padding: 0.75rem;
  margin-top: 0.5rem;
}

.conditions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.conditions-table-wrap {
  overflow-x: auto;
}

.conditions-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.75rem;
}

.conditions-table th,
.conditions-table td {
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.conditions-table th {
  background: #f1f5f9;
  text-align: left;
}

.conditions-empty {
  padding: 0.5rem;
  font-style: italic;
}

/* Tab 4: Digest Card */
.digest-card {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  max-width: 640px;
}

.digest-input-row {
  display: flex;
  align-items: flex-end;
  gap: 1rem;
  flex-wrap: wrap;
}

.digest-result-box {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  border-radius: 6px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.result-summary-banner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #166534;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

.stat-box {
  background: #ffffff;
  border: 1px solid #dcfce7;
  border-radius: 6px;
  padding: 0.75rem;
  text-align: center;
}

.stat-number {
  font-size: 1.5rem;
  font-weight: 800;
}

.stat-label {
  font-size: 0.75rem;
  color: #64748b;
  margin-top: 0.25rem;
}

/* Form Dialogs */
.dialog-form-layout {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-row-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 1rem;
}

@media (max-width: 640px) {
  .form-row-2,
  .form-row-3 {
    grid-template-columns: 1fr;
  }
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.dialog-field label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #334155;
}

.fields-builder-box {
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 6px;
  padding: 0.75rem;
  background: #f8fafc;
}

.builder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.fields-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 320px;
  overflow-y: auto;
}

.field-item-card {
  background: #ffffff;
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
}

.field-item-top {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.field-checkbox-col {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding-bottom: 0.4rem;
}

/* Detail Modal */
.detail-container {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
  padding: 0.75rem;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid var(--atria-border, #e2e8f0);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.meta-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
}

.meta-value {
  font-size: 0.875rem;
}

.section-heading {
  margin: 0 0 0.5rem;
  font-size: 0.9375rem;
  font-weight: 700;
}

.json-preview {
  background: #0f172a;
  color: #f8fafc;
  padding: 0.75rem;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
}

.diff-old-val {
  color: #b91c1c;
  text-decoration: line-through;
  font-family: monospace;
}

.diff-new-val {
  color: #15803d;
  font-weight: bold;
  font-family: monospace;
}
</style>
