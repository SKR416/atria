<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import TreeTable from 'primevue/treetable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import type { TreeNode } from 'primevue/treenode'

import { departmentApi } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'
import type {
  DepartmentRequest,
  DepartmentTreeNodeResponse
} from '@/types/iam'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const authStore = useAuthStore()

const canWrite = computed<boolean>(() => authStore.hasPermission('iam:dept:write'))

const rawDepartmentTree = ref<DepartmentTreeNodeResponse[]>([])
const treeNodes = ref<TreeNode[]>([])
const expandedKeys = ref<Record<string, boolean>>({})
const loading = ref<boolean>(false)

// Common IANA timezones (trilingual via vue-i18n)
const timezoneOptions = computed(() => [
  { label: t('timezones.Asia/Taipei'), value: 'Asia/Taipei' },
  { label: t('timezones.Asia/Tokyo'), value: 'Asia/Tokyo' },
  { label: t('timezones.UTC'), value: 'UTC' },
  { label: t('timezones.America/New_York'), value: 'America/New_York' },
  { label: t('timezones.Europe/London'), value: 'Europe/London' }
])

const calendarCodeOptions = computed(() => [
  { label: t('regions.TW'), value: 'TW' },
  { label: t('regions.JP'), value: 'JP' }
])

function getDepartmentDisplayName(dept: DepartmentTreeNodeResponse | null | undefined): string {
  if (!dept) return ''
  const locale = authStore.activeLocale
  if (dept.name && dept.name[locale]) {
    return dept.name[locale] as string
  }
  return dept.localizedName || dept.name?.['zh-TW'] || dept.code
}

function transformToTreeNodes(
  nodes: DepartmentTreeNodeResponse[],
  expMap: Record<string, boolean>
): TreeNode[] {
  return nodes.map((node) => {
    const key = String(node.id)
    expMap[key] = true
    return {
      key,
      data: node,
      children: node.children && node.children.length > 0
        ? transformToTreeNodes(node.children, expMap)
        : []
    }
  })
}

async function loadDepartmentTree() {
  loading.value = true
  try {
    const tree = await departmentApi.getTree()
    rawDepartmentTree.value = tree
    const expMap: Record<string, boolean> = {}
    treeNodes.value = transformToTreeNodes(tree, expMap)
    expandedKeys.value = expMap
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDepartmentTree()
})

watch(() => authStore.activeLocale, () => {
  loadDepartmentTree()
})

// Flatten tree for Parent selector
interface FlattenedDept {
  id: number
  displayName: string
  code: string
  treePath: string
  parentId: number | null
}

function flattenTree(nodes: DepartmentTreeNodeResponse[], depth = 0): FlattenedDept[] {
  const result: FlattenedDept[] = []
  for (const node of nodes) {
    const indent = depth > 0 ? `${'— '.repeat(depth)}` : ''
    result.push({
      id: node.id,
      displayName: `${indent}${getDepartmentDisplayName(node)} (${node.code})`,
      code: node.code,
      treePath: node.treePath,
      parentId: node.parentId
    })
    if (node.children && node.children.length > 0) {
      result.push(...flattenTree(node.children, depth + 1))
    }
  }
  return result
}

function collectSubtreeIds(node: DepartmentTreeNodeResponse, ids: Set<number>) {
  ids.add(node.id)
  if (node.children) {
    for (const child of node.children) {
      collectSubtreeIds(child, ids)
    }
  }
}

function findNodeById(nodes: DepartmentTreeNodeResponse[], id: number): DepartmentTreeNodeResponse | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children && n.children.length > 0) {
      const found = findNodeById(n.children, id)
      if (found) return found
    }
  }
  return null
}

// Dialog & Form state
interface DepartmentFormModel {
  id?: number
  parentId: number | null
  code: string
  name: {
    'zh-TW': string
    'en-US': string
    'ja-JP': string
  }
  leaderId: number | null
  timezone: string
  calendarCode: string
  sortOrder: number
}

type DialogMode = 'createRoot' | 'createChild' | 'edit'

const dialogVisible = ref<boolean>(false)
const dialogMode = ref<DialogMode>('createRoot')
const parentContextDept = ref<DepartmentTreeNodeResponse | null>(null)
const isEditing = computed<boolean>(() => dialogMode.value === 'edit')
const submitting = ref<boolean>(false)

const dialogHeaderTitle = computed<string>(() => {
  if (dialogMode.value === 'edit') return t('iam.dept.editDept')
  if (dialogMode.value === 'createChild') return t('iam.dept.addChild')
  return t('iam.dept.addRoot')
})

const formModel = ref<DepartmentFormModel>({
  parentId: null,
  code: '',
  name: {
    'zh-TW': '',
    'en-US': '',
    'ja-JP': ''
  },
  leaderId: null,
  timezone: 'Asia/Taipei',
  calendarCode: 'TW',
  sortOrder: 0
})

const parentOptions = computed(() => {
  const list: { label: string; value: number | null }[] = [
    { label: t('iam.dept.noParent'), value: null }
  ]

  const forbiddenIds = new Set<number>()
  if (isEditing.value && formModel.value.id != null) {
    const targetNode = findNodeById(rawDepartmentTree.value, formModel.value.id)
    if (targetNode) {
      collectSubtreeIds(targetNode, forbiddenIds)
    } else {
      forbiddenIds.add(formModel.value.id)
    }
  }

  const flattened = flattenTree(rawDepartmentTree.value)
  for (const item of flattened) {
    if (!forbiddenIds.has(item.id)) {
      list.push({ label: item.displayName, value: item.id })
    }
  }

  return list
})

function openCreateRootDialog() {
  dialogMode.value = 'createRoot'
  parentContextDept.value = null
  formModel.value = {
    parentId: null,
    code: '',
    name: {
      'zh-TW': '',
      'en-US': '',
      'ja-JP': ''
    },
    leaderId: null,
    timezone: 'Asia/Taipei',
    calendarCode: 'TW',
    sortOrder: 0
  }
  dialogVisible.value = true
}

function openCreateChildDialog(parentDept: DepartmentTreeNodeResponse) {
  dialogMode.value = 'createChild'
  parentContextDept.value = parentDept
  formModel.value = {
    parentId: parentDept.id,
    code: '',
    name: {
      'zh-TW': '',
      'en-US': '',
      'ja-JP': ''
    },
    leaderId: null,
    timezone: parentDept.timezone || 'Asia/Taipei',
    calendarCode: parentDept.calendarCode || 'TW',
    sortOrder: 0
  }
  dialogVisible.value = true
}

function openEditDialog(dept: DepartmentTreeNodeResponse) {
  dialogMode.value = 'edit'
  parentContextDept.value = null
  formModel.value = {
    id: dept.id,
    parentId: dept.parentId,
    code: dept.code,
    name: {
      'zh-TW': dept.name?.['zh-TW'] || '',
      'en-US': dept.name?.['en-US'] || '',
      'ja-JP': dept.name?.['ja-JP'] || ''
    },
    leaderId: dept.leaderId,
    timezone: dept.timezone || 'Asia/Taipei',
    calendarCode: dept.calendarCode || 'TW',
    sortOrder: dept.sortOrder ?? 0
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formModel.value.code.trim()) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.dept.code'),
      life: 3000
    })
    return
  }
  if (!formModel.value.name['zh-TW'].trim()) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.dept.nameZh'),
      life: 3000
    })
    return
  }

  const payload: DepartmentRequest = {
    parentId: formModel.value.parentId,
    code: formModel.value.code.trim(),
    name: {
      'zh-TW': formModel.value.name['zh-TW'].trim(),
      'en-US': formModel.value.name['en-US'].trim() || formModel.value.name['zh-TW'].trim(),
      'ja-JP': formModel.value.name['ja-JP'].trim() || formModel.value.name['zh-TW'].trim()
    },
    leaderId: formModel.value.leaderId,
    timezone: formModel.value.timezone,
    calendarCode: formModel.value.calendarCode,
    sortOrder: formModel.value.sortOrder ?? 0
  }

  submitting.value = true
  try {
    if (isEditing.value && formModel.value.id != null) {
      await departmentApi.update(formModel.value.id, payload)
    } else {
      await departmentApi.create(payload)
    }
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('common.success'),
      life: 3000
    })
    dialogVisible.value = false
    await loadDepartmentTree()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    submitting.value = false
  }
}

function handleDelete(dept: DepartmentTreeNodeResponse) {
  confirm.require({
    message: t('iam.dept.deleteConfirm', { name: getDepartmentDisplayName(dept) }),
    header: t('common.confirm'),
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await departmentApi.remove(dept.id)
        toast.add({
          severity: 'success',
          summary: t('common.success'),
          detail: t('common.success'),
          life: 3000
        })
        await loadDepartmentTree()
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : t('common.error')
        toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
      }
    }
  })
}

function asDeptNode(node: unknown): DepartmentTreeNodeResponse {
  const tn = node as { data?: DepartmentTreeNodeResponse }
  return tn?.data as DepartmentTreeNodeResponse
}
</script>

<template>
  <div class="department-manage-view" data-testid="admin-department-manage-view">
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('iam.dept.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('iam.dept.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          v-if="canWrite"
          icon="pi pi-plus"
          :label="t('iam.dept.addRoot')"
          severity="primary"
          size="small"
          @click="openCreateRootDialog"
        />
        <Button
          icon="pi pi-refresh"
          :label="t('common.refresh')"
          severity="secondary"
          outlined
          size="small"
          :loading="loading"
          @click="loadDepartmentTree"
        />
      </div>
    </div>

    <div class="atria-card table-card">
      <TreeTable
        :value="treeNodes"
        v-model:expandedKeys="expandedKeys"
        :loading="loading"
        responsiveLayout="scroll"
        class="department-treetable"
      >
        <template #empty>
          <div class="empty-message">{{ t('common.noData') }}</div>
        </template>

        <!-- Localized Name (with expander) -->
        <Column :header="t('iam.dept.localizedName')" expander style="min-width: 240px">
          <template #body="{ node }">
            <span class="dept-name">{{ getDepartmentDisplayName(asDeptNode(node)) }}</span>
          </template>
        </Column>

        <!-- Department Code -->
        <Column :header="t('iam.dept.code')" style="width: 140px">
          <template #body="{ node }">
            <code class="dept-code">{{ asDeptNode(node).code }}</code>
          </template>
        </Column>

        <!-- Timezone -->
        <Column :header="t('iam.dept.timezone')" style="width: 150px">
          <template #body="{ node }">
            <span class="dept-timezone">{{ asDeptNode(node).timezone || '-' }}</span>
          </template>
        </Column>

        <!-- Calendar Code -->
        <Column :header="t('iam.dept.calendarCode')" style="width: 110px">
          <template #body="{ node }">
            <code class="dept-code">{{ asDeptNode(node).calendarCode || 'TW' }}</code>
          </template>
        </Column>

        <!-- Leader ID -->
        <Column :header="t('iam.dept.leaderId')" style="width: 110px">
          <template #body="{ node }">
            <span>{{ asDeptNode(node).leaderId ?? '-' }}</span>
          </template>
        </Column>

        <!-- Sort Order -->
        <Column :header="t('iam.dept.sortOrder')" style="width: 100px">
          <template #body="{ node }">
            <span>{{ asDeptNode(node).sortOrder }}</span>
          </template>
        </Column>

        <!-- Tree Path -->
        <Column :header="t('iam.dept.treePath')" style="min-width: 150px">
          <template #body="{ node }">
            <code class="tree-path">{{ asDeptNode(node).treePath }}</code>
          </template>
        </Column>

        <!-- Actions -->
        <Column :header="t('common.actions')" style="width: 140px; text-align: center" headerClass="text-center">
          <template #body="{ node }">
            <div class="action-buttons">
              <Button
                v-if="canWrite"
                icon="pi pi-plus"
                size="small"
                severity="success"
                text
                rounded
                :title="t('iam.dept.addChild')"
                @click="openCreateChildDialog(asDeptNode(node))"
              />
              <Button
                v-if="canWrite"
                icon="pi pi-pencil"
                size="small"
                severity="primary"
                text
                rounded
                :title="t('common.edit')"
                @click="openEditDialog(asDeptNode(node))"
              />
              <Button
                v-if="canWrite"
                icon="pi pi-trash"
                size="small"
                severity="danger"
                text
                rounded
                :title="t('common.delete')"
                @click="handleDelete(asDeptNode(node))"
              />
            </div>
          </template>
        </Column>
      </TreeTable>
    </div>

    <!-- Create / Edit Department Dialog -->
    <Dialog
      v-model:visible="dialogVisible"
      :header="dialogHeaderTitle"
      modal
      :style="{ width: '560px', maxWidth: '94vw' }"
      :closable="true"
    >
      <form @submit.prevent="handleSubmit" class="dialog-form">
        <!-- Mode Context Banner / Parent Department -->
        <div v-if="dialogMode === 'createRoot'" class="dept-context-banner dept-context-root">
          <span class="context-label">{{ t('iam.dept.parentDept') }}：</span>
          <strong class="context-value">{{ t('iam.dept.noParent') }}</strong>
        </div>

        <div
          v-else-if="dialogMode === 'createChild' && parentContextDept"
          class="dept-context-banner dept-context-child"
        >
          <span class="context-label">{{ t('iam.dept.parentDept') }}：</span>
          <strong class="context-value">
            {{ getDepartmentDisplayName(parentContextDept) }}
          </strong>
          <code class="dept-code">{{ parentContextDept.code }}</code>
          <code class="tree-path">{{ parentContextDept.treePath }}</code>
        </div>

        <div v-else class="atria-field">
          <label for="dept-parent">{{ t('iam.dept.parentDept') }}</label>
          <Select
            id="dept-parent"
            v-model="formModel.parentId"
            :options="parentOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('iam.dept.parentDept')"
            style="width: 100%"
          />
        </div>

        <!-- Code -->
        <div class="atria-field">
          <label for="dept-code">{{ t('iam.dept.code') }} *</label>
          <InputText
            id="dept-code"
            v-model="formModel.code"
            placeholder="e.g. ENG-BACKEND"
            style="width: 100%"
            required
          />
        </div>

        <!-- Trilingual Names -->
        <div class="atria-field">
          <label for="dept-name-zh">{{ t('iam.dept.nameZh') }} *</label>
          <InputText
            id="dept-name-zh"
            v-model="formModel.name['zh-TW']"
            placeholder="例如：後端工程組"
            style="width: 100%"
            required
          />
        </div>

        <div class="atria-form-grid">
          <div class="atria-field">
            <label for="dept-name-en">{{ t('iam.dept.nameEn') }}</label>
            <InputText
              id="dept-name-en"
              v-model="formModel.name['en-US']"
              placeholder="e.g. Backend Engineering"
              style="width: 100%"
            />
          </div>
          <div class="atria-field">
            <label for="dept-name-ja">{{ t('iam.dept.nameJa') }}</label>
            <InputText
              id="dept-name-ja"
              v-model="formModel.name['ja-JP']"
              placeholder="例：バックエンド開発チーム"
              style="width: 100%"
            />
          </div>
        </div>

        <!-- Timezone & Calendar Code -->
        <div class="atria-form-grid">
          <div class="atria-field">
            <label for="dept-timezone">{{ t('iam.dept.timezone') }}</label>
            <Select
              id="dept-timezone"
              v-model="formModel.timezone"
              :options="timezoneOptions"
              optionLabel="label"
              optionValue="value"
              style="width: 100%"
            />
          </div>
          <div class="atria-field">
            <label for="dept-calendar">{{ t('iam.dept.calendarCode') }}</label>
            <Select
              id="dept-calendar"
              v-model="formModel.calendarCode"
              :options="calendarCodeOptions"
              optionLabel="label"
              optionValue="value"
              style="width: 100%"
            />
          </div>
        </div>

        <!-- Leader ID & Sort Order -->
        <div class="atria-form-grid">
          <div class="atria-field">
            <label for="dept-leader">{{ t('iam.dept.leaderId') }}</label>
            <InputNumber
              id="dept-leader"
              v-model="formModel.leaderId"
              :placeholder="t('iam.dept.leaderId')"
              :useGrouping="false"
              style="width: 100%"
            />
          </div>
          <div class="atria-field">
            <label for="dept-sort">{{ t('iam.dept.sortOrder') }}</label>
            <InputNumber
              id="dept-sort"
              v-model="formModel.sortOrder"
              :min="0"
              style="width: 100%"
            />
          </div>
        </div>
      </form>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          text
          @click="dialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="submitting"
          @click="handleSubmit"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.department-manage-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.department-treetable {
  font-size: 0.875rem;
}

.dept-name {
  font-weight: 600;
  color: var(--atria-text, #0f172a);
}

.dept-code {
  display: inline-block;
  padding: 0.125rem 0.375rem;
  background-color: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  border-radius: 4px;
  font-size: 0.75rem;
  color: var(--atria-primary, #1e40af);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.dept-timezone {
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #475569);
}

.tree-path {
  font-size: 0.75rem;
  color: var(--atria-text-light, #64748b);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
  padding: 0.5rem 0;
}

.dept-context-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0.625rem 0.875rem;
  border-radius: 6px;
  font-size: 0.8125rem;
}

.dept-context-root {
  background-color: #f8fafc;
  border: 1px dashed #cbd5e1;
  color: #334155;
}

.dept-context-child {
  background-color: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e3a8a;
}

.context-label {
  color: #475569;
  font-weight: 500;
}

.context-value {
  font-weight: 700;
}

.empty-message {
  padding: 2rem;
  text-align: center;
  color: var(--atria-text-muted, #475569);
}
</style>
