<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Checkbox from 'primevue/checkbox'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { roleApi } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'
import type { DataScopeType } from '@/types/api'
import type { RoleResponse, PermissionResponse, RoleRequest } from '@/types/iam'

const { t, te } = useI18n()
const authStore = useAuthStore()
const toast = useToast()
const confirm = useConfirm()

const roles = ref<RoleResponse[]>([])
const allPermissions = ref<PermissionResponse[]>([])
const loading = ref(false)
const saving = ref(false)

const dialogVisible = ref(false)
const isEditing = ref(false)
const editingRole = ref<RoleResponse | null>(null)

interface RoleFormData {
  code: string
  name: {
    'zh-TW': string
    'en-US': string
    'ja-JP': string
  }
  dataScope: DataScopeType
  permissionIds: number[]
}

const form = reactive<RoleFormData>({
  code: '',
  name: {
    'zh-TW': '',
    'en-US': '',
    'ja-JP': ''
  },
  dataScope: 'SELF_ONLY',
  permissionIds: []
})

const canWrite = computed(() => authStore.hasPermission('iam:role:write'))

const dataScopeOptions = computed(() => [
  { label: t('iam.dataScopeTypes.ALL'), value: 'ALL' as DataScopeType },
  { label: t('iam.dataScopeTypes.DEPT_AND_SUB'), value: 'DEPT_AND_SUB' as DataScopeType },
  { label: t('iam.dataScopeTypes.DEPT_ONLY'), value: 'DEPT_ONLY' as DataScopeType },
  { label: t('iam.dataScopeTypes.SELF_ONLY'), value: 'SELF_ONLY' as DataScopeType }
])

interface ModulePermissionGroup {
  module: string
  permissions: PermissionResponse[]
}

const groupedPermissions = computed<ModulePermissionGroup[]>(() => {
  const map = new Map<string, PermissionResponse[]>()
  for (const perm of allPermissions.value) {
    const mod = perm.module || 'OTHER'
    const list = map.get(mod) ?? []
    list.push(perm)
    map.set(mod, list)
  }
  return Array.from(map.entries()).map(([module, permissions]) => ({
    module,
    permissions
  }))
})

function getRoleDisplayName(role: RoleResponse): string {
  const loc = authStore.activeLocale
  if (role.name?.[loc]) return role.name[loc] as string
  if (te(`roleCodes.${role.code}`)) return t(`roleCodes.${role.code}`)
  return role.localizedName || role.name?.['zh-TW'] || role.code
}

function getPermDisplayName(perm: PermissionResponse): string {
  const loc = authStore.activeLocale
  if (perm.name?.[loc]) return perm.name[loc] as string
  if (te(`permissionCodes.${perm.code}`)) return t(`permissionCodes.${perm.code}`)
  return perm.localizedName || perm.name?.['zh-TW'] || perm.code
}

function getDataScopeSeverity(scope: DataScopeType): 'danger' | 'warn' | 'info' | 'secondary' {
  switch (scope) {
    case 'ALL':
      return 'danger'
    case 'DEPT_AND_SUB':
      return 'warn'
    case 'DEPT_ONLY':
      return 'info'
    case 'SELF_ONLY':
    default:
      return 'secondary'
  }
}

function isModuleAllSelected(permList: PermissionResponse[]): boolean {
  if (permList.length === 0) return false
  return permList.every((p) => form.permissionIds.includes(p.id))
}

function toggleModulePermissions(permList: PermissionResponse[]) {
  const allSelected = isModuleAllSelected(permList)
  const ids = permList.map((p) => p.id)
  if (allSelected) {
    form.permissionIds = form.permissionIds.filter((id) => !ids.includes(id))
  } else {
    const set = new Set([...form.permissionIds, ...ids])
    form.permissionIds = Array.from(set)
  }
}

async function fetchRoles() {
  loading.value = true
  try {
    roles.value = await roleApi.listRoles()
  } catch (err: unknown) {
    const errorMsg = (err as { message?: string })?.message || t('common.error')
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: errorMsg,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

async function fetchPermissions() {
  try {
    allPermissions.value = await roleApi.listPermissions()
  } catch (err: unknown) {
    const errorMsg = (err as { message?: string })?.message || t('common.error')
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: errorMsg,
      life: 3000
    })
  }
}

function openCreateDialog() {
  isEditing.value = false
  editingRole.value = null
  form.code = ''
  form.name['zh-TW'] = ''
  form.name['en-US'] = ''
  form.name['ja-JP'] = ''
  form.dataScope = 'SELF_ONLY'
  form.permissionIds = []
  dialogVisible.value = true
}

function openEditDialog(role: RoleResponse) {
  isEditing.value = true
  editingRole.value = role
  form.code = role.code
  form.name['zh-TW'] = role.name?.['zh-TW'] ?? ''
  form.name['en-US'] = role.name?.['en-US'] ?? ''
  form.name['ja-JP'] = role.name?.['ja-JP'] ?? ''
  form.dataScope = role.dataScope ?? 'SELF_ONLY'

  const selectedIds = new Set<number>()
  if (role.permissions && role.permissions.length > 0) {
    for (const p of role.permissions) {
      selectedIds.add(p.id)
    }
  }
  if (role.permissionCodes && role.permissionCodes.length > 0) {
    for (const p of allPermissions.value) {
      if (role.permissionCodes.includes(p.code)) {
        selectedIds.add(p.id)
      }
    }
  }
  form.permissionIds = Array.from(selectedIds)
  dialogVisible.value = true
}

async function handleSaveRole() {
  if (!canWrite.value) return

  const code = form.code.trim().toUpperCase()
  if (!code) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.role.code'),
      life: 3000
    })
    return
  }

  const zhName = form.name['zh-TW']?.trim() ?? ''
  if (!zhName) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.role.nameZh'),
      life: 3000
    })
    return
  }

  const payload: RoleRequest = {
    code,
    name: {
      'zh-TW': zhName,
      'en-US': form.name['en-US']?.trim() || undefined,
      'ja-JP': form.name['ja-JP']?.trim() || undefined
    },
    dataScope: form.dataScope,
    permissionIds: [...form.permissionIds]
  }

  saving.value = true
  try {
    if (isEditing.value && editingRole.value) {
      await roleApi.updateRole(editingRole.value.id, payload)
    } else {
      await roleApi.createRole(payload)
    }
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('common.success'),
      life: 3000
    })
    dialogVisible.value = false
    await fetchRoles()
  } catch (err: unknown) {
    const errorMsg = (err as { message?: string })?.message || t('common.error')
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: errorMsg,
      life: 4000
    })
  } finally {
    saving.value = false
  }
}

function handleDeleteRole(role: RoleResponse) {
  if (!canWrite.value || role.builtIn) return

  confirm.require({
    message: t('common.confirmDelete'),
    header: t('common.confirm'),
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await roleApi.deleteRole(role.id)
        toast.add({
          severity: 'success',
          summary: t('common.success'),
          detail: t('common.success'),
          life: 3000
        })
        await fetchRoles()
      } catch (err: unknown) {
        const errorMsg = (err as { message?: string })?.message || t('common.error')
        toast.add({
          severity: 'error',
          summary: t('common.error'),
          detail: errorMsg,
          life: 4000
        })
      }
    }
  })
}

watch(
  () => authStore.activeLocale,
  () => {
    fetchRoles()
    fetchPermissions()
  }
)

onMounted(async () => {
  await Promise.all([fetchRoles(), fetchPermissions()])
})
</script>

<template>
  <div class="role-manage-page" data-testid="admin-role-manage-view">
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('iam.role.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('iam.role.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          :label="t('common.refresh')"
          icon="pi pi-refresh"
          text
          size="small"
          :loading="loading"
          @click="fetchRoles"
        />
        <Button
          :label="t('iam.role.addRole')"
          icon="pi pi-plus"
          size="small"
          :disabled="!canWrite"
          @click="openCreateDialog"
        />
      </div>
    </div>

    <div class="atria-card">
      <DataTable
        :value="roles"
        :loading="loading"
        responsiveLayout="scroll"
        dataKey="id"
        class="p-datatable-sm"
        stripedRows
      >
        <template #empty>
          <div class="table-empty">{{ t('common.noData') }}</div>
        </template>

        <!-- Role Code -->
        <Column field="code" :header="t('iam.role.code')" style="width: 160px">
          <template #body="{ data }: { data: RoleResponse }">
            <span class="role-code">{{ data.code }}</span>
          </template>
        </Column>

        <!-- Localized Name -->
        <Column :header="t('iam.role.localizedName')" style="min-width: 180px">
          <template #body="{ data }: { data: RoleResponse }">
            <span class="role-name">{{ getRoleDisplayName(data) }}</span>
          </template>
        </Column>

        <!-- Built-in / Custom Type Badge -->
        <Column :header="t('common.status')" style="width: 120px">
          <template #body="{ data }: { data: RoleResponse }">
            <Tag
              :value="data.builtIn ? t('iam.role.builtIn') : t('iam.role.custom')"
              :severity="data.builtIn ? 'info' : 'success'"
            />
          </template>
        </Column>

        <!-- Data Scope Badge -->
        <Column :header="t('iam.role.dataScope')" style="width: 200px">
          <template #body="{ data }: { data: RoleResponse }">
            <Tag
              :value="t('iam.dataScopeTypes.' + data.dataScope)"
              :severity="getDataScopeSeverity(data.dataScope)"
            />
          </template>
        </Column>

        <!-- Granted Permissions Preview -->
        <Column :header="t('iam.role.permissions')" style="min-width: 260px">
          <template #body="{ data }: { data: RoleResponse }">
            <div class="permissions-preview">
              <Tag
                :value="String(data.permissionCodes?.length ?? 0)"
                severity="secondary"
                class="perm-count"
              />
              <div class="perm-chips">
                <Tag
                  v-for="code in (data.permissionCodes ?? []).slice(0, 3)"
                  :key="code"
                  :value="code"
                  severity="contrast"
                  class="perm-chip"
                />
                <Tag
                  v-if="(data.permissionCodes?.length ?? 0) > 3"
                  :value="'+' + ((data.permissionCodes?.length ?? 0) - 3)"
                  severity="secondary"
                  class="perm-chip-more"
                />
              </div>
            </div>
          </template>
        </Column>

        <!-- Actions -->
        <Column :header="t('common.actions')" style="width: 110px" alignFrozen="right" frozen>
          <template #body="{ data }: { data: RoleResponse }">
            <div class="actions-cell">
              <Button
                icon="pi pi-pencil"
                text
                rounded
                size="small"
                :title="t('common.edit')"
                :disabled="!canWrite"
                @click="openEditDialog(data)"
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                severity="danger"
                size="small"
                :title="t('common.delete')"
                :disabled="!canWrite || data.builtIn"
                @click="handleDeleteRole(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Create / Edit Role Dialog -->
    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="isEditing ? t('iam.role.editRole') : t('iam.role.addRole')"
      :style="{ width: '700px', maxWidth: '95vw' }"
    >
      <div class="dialog-content">
        <div v-if="isEditing && editingRole?.builtIn" class="built-in-banner">
          <i class="pi pi-info-circle mr-2" />
          <span>{{ t('iam.role.builtInNotice') }}</span>
        </div>

        <div class="form-section">
          <div class="atria-field mb-3">
            <label for="role-code">{{ t('iam.role.code') }} *</label>
            <InputText
              id="role-code"
              v-model="form.code"
              :disabled="Boolean(isEditing && editingRole?.builtIn)"
              placeholder="ROLE_NAME"
              class="w-full"
            />
          </div>

          <div class="atria-form-grid mb-3">
            <div class="atria-field">
              <label for="role-name-zh">{{ t('iam.role.nameZh') }} *</label>
              <InputText
                id="role-name-zh"
                v-model="form.name['zh-TW']"
                placeholder="繁體中文名稱"
                class="w-full"
              />
            </div>
            <div class="atria-field">
              <label for="role-name-en">{{ t('iam.role.nameEn') }}</label>
              <InputText
                id="role-name-en"
                v-model="form.name['en-US']"
                placeholder="English Name"
                class="w-full"
              />
            </div>
            <div class="atria-field">
              <label for="role-name-ja">{{ t('iam.role.nameJa') }}</label>
              <InputText
                id="role-name-ja"
                v-model="form.name['ja-JP']"
                placeholder="日本語名称"
                class="w-full"
              />
            </div>
          </div>

          <div class="atria-field mb-4">
            <label for="role-data-scope">{{ t('iam.role.dataScope') }} *</label>
            <Select
              id="role-data-scope"
              v-model="form.dataScope"
              :options="dataScopeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
        </div>

        <!-- Permissions Matrix Grouped by Module -->
        <div class="permissions-section">
          <div class="section-title-bar">
            <h3 class="section-title">{{ t('iam.role.permissions') }}</h3>
            <span class="selected-count">
              {{ form.permissionIds.length }} / {{ allPermissions.length }}
            </span>
          </div>

          <div class="modules-container">
            <div
              v-for="group in groupedPermissions"
              :key="group.module"
              class="module-card"
            >
              <div class="module-header">
                <div class="module-name-wrapper">
                  <span class="module-label">{{ t('iam.role.permissionModule') }}:</span>
                  <span class="module-name">{{ group.module }}</span>
                </div>
                <Button
                  :label="isModuleAllSelected(group.permissions) ? t('common.reset') : t('common.all')"
                  size="small"
                  text
                  @click="toggleModulePermissions(group.permissions)"
                />
              </div>

              <div class="permissions-grid">
                <div
                  v-for="perm in group.permissions"
                  :key="perm.id"
                  class="perm-checkbox-item"
                >
                  <Checkbox
                    v-model="form.permissionIds"
                    :value="perm.id"
                    :inputId="'perm-' + perm.id"
                  />
                  <label :for="'perm-' + perm.id" class="perm-checkbox-label">
                    <span class="perm-display-name">{{ getPermDisplayName(perm) }}</span>
                    <span class="perm-display-code">{{ perm.code }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          icon="pi pi-times"
          text
          severity="secondary"
          @click="dialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          icon="pi pi-check"
          :loading="saving"
          :disabled="!canWrite"
          @click="handleSaveRole"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.role-manage-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.role-code {
  font-family: monospace;
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--atria-text);
}

.role-name {
  font-weight: 500;
}

.permissions-preview {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  flex-wrap: wrap;
}

.perm-count {
  font-weight: 700;
  font-size: 0.75rem;
}

.perm-chips {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  flex-wrap: wrap;
}

.perm-chip {
  font-size: 0.7rem;
  font-family: monospace;
  padding: 0.1rem 0.35rem;
}

.perm-chip-more {
  font-size: 0.7rem;
  padding: 0.1rem 0.35rem;
}

.actions-cell {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.table-empty {
  text-align: center;
  padding: 2rem;
  color: var(--atria-text-muted);
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.built-in-banner {
  display: flex;
  align-items: center;
  background-color: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
  padding: 0.5rem 0.75rem;
  border-radius: var(--atria-radius);
  font-size: 0.8125rem;
}

.form-section {
  display: flex;
  flex-direction: column;
}

.permissions-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  border-top: 1px solid var(--atria-border);
  padding-top: 0.75rem;
}

.section-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--atria-text);
}

.selected-count {
  font-size: 0.8125rem;
  color: var(--atria-text-muted);
  font-weight: 600;
}

.modules-container {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.module-card {
  border: 1px solid var(--atria-border);
  border-radius: var(--atria-radius);
  background: var(--atria-surface-subtle);
  padding: 0.75rem;
}

.module-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  padding-bottom: 0.375rem;
  border-bottom: 1px dashed var(--atria-border);
}

.module-name-wrapper {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.module-label {
  font-size: 0.75rem;
  color: var(--atria-text-muted);
}

.module-name {
  font-weight: 700;
  font-size: 0.85rem;
  color: var(--atria-primary);
}

.permissions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 0.5rem 0.75rem;
}

.perm-checkbox-item {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.25rem 0.375rem;
  border-radius: 4px;
}

.perm-checkbox-item:hover {
  background: rgba(0, 0, 0, 0.02);
}

.perm-checkbox-label {
  display: flex;
  flex-direction: column;
  cursor: pointer;
  line-height: 1.25;
}

.perm-display-name {
  font-size: 0.8125rem;
  color: var(--atria-text);
  font-weight: 500;
}

.perm-display-code {
  font-size: 0.7rem;
  font-family: monospace;
  color: var(--atria-text-muted);
}
</style>
