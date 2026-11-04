<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import Checkbox from 'primevue/checkbox'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

import { userApi, departmentApi, roleApi } from '@/api/iam'
import { useAuthStore } from '@/stores/auth'
import type {
  DepartmentTreeNodeResponse,
  RoleResponse,
  UserCreateRequest,
  UserResponse,
  UserUpdateRequest
} from '@/types/iam'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const authStore = useAuthStore()

const canWriteUser = computed<boolean>(() => authStore.hasPermission('iam:user:write'))

// Table data & pagination
const users = ref<UserResponse[]>([])
const totalRecords = ref<number>(0)
const currentPage = ref<number>(1)
const pageSize = ref<number>(20)
const loading = ref<boolean>(false)

// Filters
const filterKeyword = ref<string>('')
const filterDepartmentId = ref<number | null>(null)

// Metadata: Departments & Roles
const rawDepartmentTree = ref<DepartmentTreeNodeResponse[]>([])
const availableRoles = ref<RoleResponse[]>([])

const localeOptions = [
  { label: '繁體中文 (zh-TW)', value: 'zh-TW' },
  { label: 'English (en-US)', value: 'en-US' },
  { label: '日本語 (ja-JP)', value: 'ja-JP' }
]

function getDeptDisplayName(dept: DepartmentTreeNodeResponse): string {
  if (!dept) return ''
  const locale = authStore.activeLocale
  if (dept.name && dept.name[locale]) {
    return dept.name[locale] as string
  }
  return dept.localizedName || dept.name?.['zh-TW'] || dept.code
}

interface FlattenedDept {
  id: number
  nameOnly: string
  displayName: string
}

function flattenDepts(nodes: DepartmentTreeNodeResponse[], depth = 0): FlattenedDept[] {
  const result: FlattenedDept[] = []
  for (const n of nodes) {
    const name = getDeptDisplayName(n)
    const indent = depth > 0 ? `${'— '.repeat(depth)}` : ''
    result.push({
      id: n.id,
      nameOnly: `${name} (${n.code})`,
      displayName: `${indent}${name} (${n.code})`
    })
    if (n.children && n.children.length > 0) {
      result.push(...flattenDepts(n.children, depth + 1))
    }
  }
  return result
}

const flattenedDepartmentList = computed(() => flattenDepts(rawDepartmentTree.value))

const departmentFilterOptions = computed(() => [
  { label: t('common.all'), value: null },
  ...flattenedDepartmentList.value.map((d) => ({
    label: d.displayName,
    value: d.id
  }))
])

const departmentSelectOptions = computed(() =>
  flattenedDepartmentList.value.map((d) => ({
    label: d.displayName,
    value: d.id
  }))
)

const departmentMap = computed(() => {
  const map = new Map<number, string>()
  for (const d of flattenedDepartmentList.value) {
    map.set(d.id, d.nameOnly)
  }
  return map
})

const roleOptions = computed(() =>
  availableRoles.value.map((r) => ({
    label: r.localizedName || r.code,
    value: r.id
  }))
)

async function loadMetadata() {
  try {
    const [deptTree, roles] = await Promise.all([
      departmentApi.getTree(),
      roleApi.listRoles()
    ])
    rawDepartmentTree.value = deptTree
    availableRoles.value = roles
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  }
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await userApi.list({
      page: currentPage.value,
      size: pageSize.value,
      departmentId: filterDepartmentId.value,
      keyword: filterKeyword.value
    })
    users.value = res.list
    totalRecords.value = res.total
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadMetadata()
  await fetchUsers()
})

watch(() => authStore.activeLocale, async () => {
  await loadMetadata()
  await fetchUsers()
})

interface DataTablePageEvent {
  page: number
  first: number
  rows: number
  pageCount?: number
}

function onPageChange(event: DataTablePageEvent) {
  currentPage.value = event.page + 1
  pageSize.value = event.rows
  fetchUsers()
}

function handleSearch() {
  currentPage.value = 1
  fetchUsers()
}

function handleReset() {
  filterKeyword.value = ''
  filterDepartmentId.value = null
  currentPage.value = 1
  fetchUsers()
}

function asUser(data: unknown): UserResponse {
  return data as UserResponse
}

function isAccountLocked(user: UserResponse): boolean {
  if (!user.lockedUntil) return false
  return new Date(user.lockedUntil).getTime() > Date.now()
}

function isUnlockable(user: UserResponse): boolean {
  if (user.failedLoginAttempts > 0) return true
  if (user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now()) return true
  return false
}

function formatDateTime(val: string | null | undefined): string {
  if (!val) return '-'
  try {
    const d = new Date(val)
    if (isNaN(d.getTime())) return val
    return d.toLocaleString()
  } catch {
    return val
  }
}

// User Create / Edit Dialog
interface UserFormModel {
  id?: number
  username: string
  displayName: string
  email: string
  initialPassword?: string
  departmentId: number | null
  hireDate: Date | null
  locale: string
  roleIds: number[]
  mustChangePassword?: boolean
  enabled?: boolean
}

const userDialogVisible = ref<boolean>(false)
const isEditingUser = ref<boolean>(false)
const userSubmitting = ref<boolean>(false)

const userForm = ref<UserFormModel>({
  username: '',
  displayName: '',
  email: '',
  initialPassword: '',
  departmentId: null,
  hireDate: null,
  locale: 'zh-TW',
  roleIds: [],
  mustChangePassword: true,
  enabled: true
})

const superAdminRoleId = computed<number | null>(() => {
  const found = availableRoles.value.find((r) => r.code === 'SUPER_ADMIN')
  return found ? found.id : null
})

/**
 * System Account check (ADR-0008 Decision 11):
 * A user is considered a system account (where hireDate is optional) if:
 * 1) username is 'admin' or starts with 'sys_' / 'bot_', OR
 * 2) assigned roles include SUPER_ADMIN.
 * All regular employees must have a hireDate.
 */
const isSystemAccountForm = computed<boolean>(() => {
  const uname = (userForm.value.username || '').trim().toLowerCase()
  if (uname === 'admin' || uname.startsWith('sys_') || uname.startsWith('bot_')) {
    return true
  }
  if (superAdminRoleId.value != null && userForm.value.roleIds.includes(superAdminRoleId.value)) {
    return true
  }
  return false
})

function parseIsoDateToLocal(isoDate: string | null | undefined): Date | null {
  if (!isoDate) return null
  const parts = isoDate.split('-').map(Number)
  if (parts.length !== 3 || parts.some(isNaN)) return null
  return new Date(parts[0], parts[1] - 1, parts[2])
}

function formatLocalDateToIso(date: Date | null | undefined): string | null {
  if (!date || isNaN(date.getTime())) return null
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function openCreateUserDialog() {
  isEditingUser.value = false
  userForm.value = {
    username: '',
    displayName: '',
    email: '',
    initialPassword: '',
    departmentId: flattenedDepartmentList.value[0]?.id ?? null,
    hireDate: new Date(),
    locale: 'zh-TW',
    roleIds: [],
    mustChangePassword: true,
    enabled: true
  }
  userDialogVisible.value = true
}

function openEditUserDialog(user: UserResponse) {
  isEditingUser.value = true
  userForm.value = {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    email: user.email || '',
    departmentId: user.departmentId,
    hireDate: parseIsoDateToLocal(user.hireDate),
    locale: user.locale || 'zh-TW',
    roleIds: [...(user.roleIds || [])],
    enabled: user.enabled
  }
  userDialogVisible.value = true
}

async function handleUserSubmit() {
  if (!isEditingUser.value && !userForm.value.username.trim()) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.user.username'),
      life: 3000
    })
    return
  }
  if (!userForm.value.displayName.trim()) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.user.displayName'),
      life: 3000
    })
    return
  }
  if (userForm.value.departmentId == null) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.user.department'),
      life: 3000
    })
    return
  }
  if (!isSystemAccountForm.value && !userForm.value.hireDate) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('iam.user.hireDateRequired'),
      life: 3500
    })
    return
  }
  if (!isEditingUser.value) {
    if (!userForm.value.initialPassword || userForm.value.initialPassword.length < 8) {
      toast.add({
        severity: 'warn',
        summary: t('common.error'),
        detail: t('auth.passwordMinLength'),
        life: 3000
      })
      return
    }
  }

  const hireDateIso = formatLocalDateToIso(userForm.value.hireDate)

  userSubmitting.value = true
  try {
    if (isEditingUser.value && userForm.value.id != null) {
      const payload: UserUpdateRequest = {
        displayName: userForm.value.displayName.trim(),
        email: userForm.value.email.trim() ? userForm.value.email.trim() : null,
        departmentId: userForm.value.departmentId,
        hireDate: hireDateIso,
        locale: userForm.value.locale,
        enabled: userForm.value.enabled,
        roleIds: userForm.value.roleIds
      }
      await userApi.update(userForm.value.id, payload)
    } else {
      const payload: UserCreateRequest = {
        username: userForm.value.username.trim(),
        displayName: userForm.value.displayName.trim(),
        email: userForm.value.email.trim() ? userForm.value.email.trim() : null,
        initialPassword: userForm.value.initialPassword!,
        departmentId: userForm.value.departmentId,
        hireDate: hireDateIso,
        locale: userForm.value.locale,
        roleIds: userForm.value.roleIds,
        mustChangePassword: userForm.value.mustChangePassword
      }
      await userApi.create(payload)
    }

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('common.success'),
      life: 3000
    })
    userDialogVisible.value = false
    await fetchUsers()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    userSubmitting.value = false
  }
}

// Reset Password Dialog
const resetDialogVisible = ref<boolean>(false)
const resetSubmitting = ref<boolean>(false)
const resetTargetUser = ref<UserResponse | null>(null)
const resetForm = ref<{
  temporaryPassword: string
  mustChangePassword: boolean
}>({
  temporaryPassword: '',
  mustChangePassword: true
})

function openResetPasswordDialog(user: UserResponse) {
  resetTargetUser.value = user
  resetForm.value = {
    temporaryPassword: '',
    mustChangePassword: true
  }
  resetDialogVisible.value = true
}

async function handleResetPasswordSubmit() {
  if (!resetTargetUser.value) return
  if (!resetForm.value.temporaryPassword || resetForm.value.temporaryPassword.length < 8) {
    toast.add({
      severity: 'warn',
      summary: t('common.error'),
      detail: t('auth.passwordMinLength'),
      life: 3000
    })
    return
  }

  resetSubmitting.value = true
  try {
    await userApi.adminResetPassword(resetTargetUser.value.id, {
      temporaryPassword: resetForm.value.temporaryPassword,
      mustChangePassword: resetForm.value.mustChangePassword
    })
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('iam.user.resetSuccess'),
      life: 3000
    })
    resetDialogVisible.value = false
    await fetchUsers()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    resetSubmitting.value = false
  }
}

// Unlock Action
async function handleUnlock(user: UserResponse) {
  try {
    await userApi.unlock(user.id)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('iam.user.unlockSuccess'),
      life: 3000
    })
    await fetchUsers()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  }
}

// Delete Action
function handleDeleteUser(user: UserResponse) {
  confirm.require({
    message: t('common.confirmDelete'),
    header: t('common.confirm'),
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await userApi.remove(user.id)
        toast.add({
          severity: 'success',
          summary: t('common.success'),
          detail: t('common.success'),
          life: 3000
        })
        await fetchUsers()
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : t('common.error')
        toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
      }
    }
  })
}
</script>

<template>
  <div class="user-manage-view">
    <!-- Page Header -->
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('iam.user.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('iam.user.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          v-if="canWriteUser"
          icon="pi pi-plus"
          :label="t('iam.user.addUser')"
          severity="primary"
          size="small"
          @click="openCreateUserDialog"
        />
      </div>
    </div>

    <!-- Filter Card -->
    <div class="atria-card filter-card">
      <div class="filter-bar">
        <div class="filter-item keyword-field">
          <InputText
            v-model="filterKeyword"
            :placeholder="t('iam.user.keywordPlaceholder')"
            size="small"
            style="width: 100%"
            @keydown.enter="handleSearch"
          />
        </div>
        <div class="filter-item dept-field">
          <Select
            v-model="filterDepartmentId"
            :options="departmentFilterOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('iam.user.department')"
            size="small"
            style="width: 100%"
          />
        </div>
        <div class="filter-buttons">
          <Button
            icon="pi pi-search"
            :label="t('common.search')"
            severity="primary"
            size="small"
            @click="handleSearch"
          />
          <Button
            icon="pi pi-undo"
            :label="t('common.reset')"
            severity="secondary"
            outlined
            size="small"
            @click="handleReset"
          />
        </div>
      </div>
    </div>

    <!-- Users DataTable -->
    <div class="atria-card table-card">
      <DataTable
        :value="users"
        lazy
        paginator
        :rows="pageSize"
        :totalRecords="totalRecords"
        :first="(currentPage - 1) * pageSize"
        :loading="loading"
        dataKey="id"
        stripedRows
        responsiveLayout="scroll"
        :rowsPerPageOptions="[10, 20, 50]"
        @page="onPageChange"
        class="user-datatable"
      >
        <template #empty>
          <div class="empty-message">{{ t('common.noData') }}</div>
        </template>

        <!-- Username -->
        <Column field="username" :header="t('iam.user.username')" style="min-width: 120px">
          <template #body="{ data }">
            <span class="user-username">{{ asUser(data).username }}</span>
          </template>
        </Column>

        <!-- Display Name -->
        <Column field="displayName" :header="t('iam.user.displayName')" style="min-width: 120px">
          <template #body="{ data }">
            <span class="user-display-name">{{ asUser(data).displayName }}</span>
          </template>
        </Column>

        <!-- Email -->
        <Column field="email" :header="t('iam.user.email')" style="min-width: 160px">
          <template #body="{ data }">
            <span>{{ asUser(data).email || '-' }}</span>
          </template>
        </Column>

        <!-- Department -->
        <Column :header="t('iam.user.department')" style="min-width: 150px">
          <template #body="{ data }">
            <span>{{ departmentMap.get(asUser(data).departmentId) || ('ID: ' + asUser(data).departmentId) }}</span>
          </template>
        </Column>

        <!-- Hire Date -->
        <Column field="hireDate" :header="t('iam.user.hireDate')" style="min-width: 115px">
          <template #body="{ data }">
            <span class="hire-date-cell">{{ asUser(data).hireDate || '-' }}</span>
          </template>
        </Column>

        <!-- Roles -->
        <Column :header="t('iam.user.roles')" style="min-width: 160px">
          <template #body="{ data }">
            <div class="roles-wrapper">
              <Tag
                v-for="rc in asUser(data).roleCodes"
                :key="rc"
                :value="rc"
                severity="info"
                class="role-tag"
              />
            </div>
          </template>
        </Column>

        <!-- Status -->
        <Column :header="t('common.status')" style="width: 140px">
          <template #body="{ data }">
            <div class="status-wrapper">
              <Tag
                :severity="asUser(data).enabled ? 'success' : 'secondary'"
                :value="asUser(data).enabled ? t('common.enabled') : t('common.disabled')"
              />
              <Tag
                v-if="isAccountLocked(asUser(data))"
                severity="danger"
                :value="t('common.locked')"
                class="ml-1"
              />
            </div>
          </template>
        </Column>

        <!-- Failed Attempts -->
        <Column :header="t('iam.user.failedAttempts')" style="width: 100px; text-align: center" headerClass="text-center">
          <template #body="{ data }">
            <Tag
              v-if="asUser(data).failedLoginAttempts > 0"
              severity="warn"
              :value="String(asUser(data).failedLoginAttempts)"
            />
            <span v-else>0</span>
          </template>
        </Column>

        <!-- Last Login -->
        <Column :header="t('iam.user.lastLoginAt')" style="min-width: 150px">
          <template #body="{ data }">
            <span class="login-time">{{ formatDateTime(asUser(data).lastLoginAt) }}</span>
          </template>
        </Column>

        <!-- Actions -->
        <Column :header="t('common.actions')" style="width: 170px; text-align: center" headerClass="text-center">
          <template #body="{ data }">
            <div class="action-buttons">
              <Button
                v-if="canWriteUser"
                icon="pi pi-pencil"
                size="small"
                severity="primary"
                text
                rounded
                :title="t('common.edit')"
                @click="openEditUserDialog(asUser(data))"
              />
              <Button
                v-if="canWriteUser"
                icon="pi pi-key"
                size="small"
                severity="warn"
                text
                rounded
                :title="t('iam.user.resetPassword')"
                @click="openResetPasswordDialog(asUser(data))"
              />
              <Button
                v-if="canWriteUser"
                icon="pi pi-lock-open"
                size="small"
                severity="success"
                text
                rounded
                :title="t('iam.user.unlockAccount')"
                :disabled="!isUnlockable(asUser(data))"
                @click="handleUnlock(asUser(data))"
              />
              <Button
                v-if="canWriteUser"
                icon="pi pi-trash"
                size="small"
                severity="danger"
                text
                rounded
                :title="t('common.delete')"
                @click="handleDeleteUser(asUser(data))"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Create / Edit User Dialog -->
    <Dialog
      v-model:visible="userDialogVisible"
      :header="isEditingUser ? t('iam.user.editUser') : t('iam.user.addUser')"
      modal
      :style="{ width: '560px' }"
      :closable="true"
    >
      <form @submit.prevent="handleUserSubmit" class="dialog-form">
        <!-- Username -->
        <div class="atria-field">
          <label for="u-username">{{ t('iam.user.username') }} *</label>
          <InputText
            id="u-username"
            v-model="userForm.username"
            :disabled="isEditingUser"
            placeholder="e.g. john.doe"
            style="width: 100%"
            required
          />
        </div>

        <!-- Display Name & Email -->
        <div class="atria-form-grid">
          <div class="atria-field">
            <label for="u-display-name">{{ t('iam.user.displayName') }} *</label>
            <InputText
              id="u-display-name"
              v-model="userForm.displayName"
              placeholder="e.g. John Doe"
              style="width: 100%"
              required
            />
          </div>
          <div class="atria-field">
            <label for="u-email">{{ t('iam.user.email') }}</label>
            <InputText
              id="u-email"
              v-model="userForm.email"
              type="email"
              placeholder="e.g. john@example.com"
              style="width: 100%"
            />
          </div>
        </div>

        <!-- Department & Locale -->
        <div class="atria-form-grid">
          <div class="atria-field">
            <label for="u-dept">{{ t('iam.user.department') }} *</label>
            <Select
              id="u-dept"
              v-model="userForm.departmentId"
              :options="departmentSelectOptions"
              optionLabel="label"
              optionValue="value"
              :placeholder="t('iam.user.department')"
              style="width: 100%"
            />
          </div>
          <div class="atria-field">
            <label for="u-hire-date">
              {{ t('iam.user.hireDate') }}
              <span v-if="!isSystemAccountForm">*</span>
              <span v-else class="optional-hint">({{ t('iam.user.systemAccountOptionalHint') }})</span>
            </label>
            <DatePicker
              inputId="u-hire-date"
              v-model="userForm.hireDate"
              dateFormat="yy-mm-dd"
              showIcon
              showButtonBar
              placeholder="YYYY-MM-DD"
              style="width: 100%"
            />
          </div>
        </div>

        <!-- Locale -->
        <div class="atria-field">
          <label for="u-locale">{{ t('iam.user.locale') }}</label>
          <Select
            id="u-locale"
            v-model="userForm.locale"
            :options="localeOptions"
            optionLabel="label"
            optionValue="value"
            style="width: 100%"
          />
        </div>

        <!-- Roles (MultiSelect) -->
        <div class="atria-field">
          <label for="u-roles">{{ t('iam.user.roles') }}</label>
          <MultiSelect
            id="u-roles"
            v-model="userForm.roleIds"
            :options="roleOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('iam.user.roles')"
            display="chip"
            :filter="true"
            style="width: 100%"
          />
        </div>

        <!-- Password fields (Create only) -->
        <div v-if="!isEditingUser" class="atria-field">
          <label for="u-password">{{ t('iam.user.initialPassword') }} *</label>
          <InputText
            id="u-password"
            v-model="userForm.initialPassword"
            type="password"
            placeholder="Min 8 characters"
            style="width: 100%"
            required
          />
        </div>

        <div v-if="!isEditingUser" class="checkbox-field">
          <Checkbox
            v-model="userForm.mustChangePassword"
            :binary="true"
            inputId="u-must-change"
          />
          <label for="u-must-change" class="checkbox-label">{{ t('iam.user.mustChangePassword') }}</label>
        </div>

        <!-- Enabled field (Edit only) -->
        <div v-if="isEditingUser" class="checkbox-field">
          <Checkbox
            v-model="userForm.enabled"
            :binary="true"
            inputId="u-enabled"
          />
          <label for="u-enabled" class="checkbox-label">{{ t('common.enabled') }}</label>
        </div>
      </form>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          text
          @click="userDialogVisible = false"
        />
        <Button
          :label="t('common.save')"
          severity="primary"
          :loading="userSubmitting"
          @click="handleUserSubmit"
        />
      </template>
    </Dialog>

    <!-- Reset Password Dialog -->
    <Dialog
      v-model:visible="resetDialogVisible"
      :header="t('iam.user.resetPasswordTitle', { name: resetTargetUser?.displayName || resetTargetUser?.username || '' })"
      modal
      :style="{ width: '450px' }"
      :closable="true"
    >
      <form @submit.prevent="handleResetPasswordSubmit" class="dialog-form">
        <div class="atria-field">
          <label for="reset-pwd">{{ t('iam.user.temporaryPassword') }} *</label>
          <InputText
            id="reset-pwd"
            v-model="resetForm.temporaryPassword"
            type="password"
            placeholder="Min 8 characters"
            style="width: 100%"
            required
          />
        </div>
        <div class="checkbox-field">
          <Checkbox
            v-model="resetForm.mustChangePassword"
            :binary="true"
            inputId="reset-must-change"
          />
          <label for="reset-must-change" class="checkbox-label">{{ t('iam.user.mustChangePassword') }}</label>
        </div>
      </form>

      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          text
          @click="resetDialogVisible = false"
        />
        <Button
          :label="t('common.confirm')"
          severity="warn"
          :loading="resetSubmitting"
          @click="handleResetPasswordSubmit"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.user-manage-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filter-card {
  padding: 0.75rem 1rem;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.keyword-field {
  flex: 1 1 240px;
}

.dept-field {
  flex: 0 1 220px;
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.user-datatable {
  font-size: 0.875rem;
}

.user-username {
  font-weight: 600;
  color: var(--atria-text, #0f172a);
}

.user-display-name {
  color: var(--atria-text, #0f172a);
}

.roles-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.role-tag {
  font-size: 0.75rem;
}

.status-wrapper {
  display: inline-flex;
  align-items: center;
}

.login-time {
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #475569);
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

.checkbox-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.checkbox-label {
  font-size: 0.875rem;
  color: var(--atria-text, #0f172a);
  cursor: pointer;
}

.empty-message {
  padding: 2rem;
  text-align: center;
  color: var(--atria-text-muted, #475569);
}
</style>
