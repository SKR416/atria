<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable, { type DataTablePageEvent } from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { auditLogApi } from '@/api/iam'
import type { AuditLogResponse } from '@/types/iam'

const { t } = useI18n()
const toast = useToast()

const logs = ref<AuditLogResponse[]>([])
const totalRecords = ref(0)
const loading = ref(false)
const first = ref(0)
const pageSize = ref(20)
const currentPage = ref(1)

function getActionSeverity(action: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
  if (!action) return 'secondary'
  const act = action.toUpperCase()
  if (act.includes('DELETE') || act.includes('LOCK')) {
    return 'danger'
  }
  if (act.includes('CREATE') || act.includes('REGISTER')) {
    return 'success'
  }
  if (act.includes('UPDATE') || act.includes('RESET') || act.includes('PASSWORD')) {
    return 'warn'
  }
  if (act.includes('LOGIN') || act.includes('AUTH')) {
    return 'info'
  }
  return 'secondary'
}

function formatTimestamp(isoString: string): string {
  if (!isoString) return '-'
  try {
    const d = new Date(isoString)
    if (isNaN(d.getTime())) return isoString
    const pad = (n: number) => String(n).padStart(2, '0')
    const y = d.getFullYear()
    const m = pad(d.getMonth() + 1)
    const day = pad(d.getDate())
    const h = pad(d.getHours())
    const min = pad(d.getMinutes())
    const s = pad(d.getSeconds())
    return `${y}-${m}-${day} ${h}:${min}:${s}`
  } catch {
    return isoString
  }
}

async function fetchLogs() {
  loading.value = true
  try {
    const res = await auditLogApi.query({
      page: currentPage.value,
      size: pageSize.value
    })
    logs.value = res.list ?? []
    totalRecords.value = res.total ?? 0
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

function onPage(event: DataTablePageEvent) {
  first.value = event.first
  pageSize.value = event.rows
  currentPage.value = event.page + 1
  fetchLogs()
}

onMounted(() => {
  fetchLogs()
})
</script>

<template>
  <div class="audit-log-page" data-testid="admin-audit-log-view">
    <div class="atria-page-header">
      <div>
        <h1 class="atria-page-title">{{ t('iam.audit.title') }}</h1>
        <p class="atria-page-subtitle">{{ t('iam.audit.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <Button
          :label="t('common.refresh')"
          icon="pi pi-refresh"
          text
          size="small"
          :loading="loading"
          @click="fetchLogs"
        />
      </div>
    </div>

    <div class="atria-card">
      <DataTable
        :value="logs"
        :loading="loading"
        lazy
        paginator
        :first="first"
        :rows="pageSize"
        :totalRecords="totalRecords"
        :rowsPerPageOptions="[10, 20, 50, 100]"
        dataKey="id"
        responsiveLayout="scroll"
        class="p-datatable-sm"
        stripedRows
        @page="onPage"
      >
        <template #empty>
          <div class="table-empty">{{ t('common.noData') }}</div>
        </template>

        <!-- ID Column -->
        <Column field="id" header="ID" style="width: 75px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <span class="id-cell">#{{ data.id }}</span>
          </template>
        </Column>

        <!-- Timestamp Column -->
        <Column :header="t('iam.audit.timestamp')" style="min-width: 170px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <span class="timestamp-cell">{{ formatTimestamp(data.createdAt) }}</span>
          </template>
        </Column>

        <!-- Actor Column -->
        <Column :header="t('iam.audit.actor')" style="min-width: 170px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <div class="actor-cell">
              <span class="actor-username">{{ data.actorUsername || '-' }}</span>
              <Tag
                v-if="data.actorId !== null && data.actorId !== undefined"
                :value="'#' + data.actorId"
                severity="secondary"
                class="actor-id-tag"
              />
            </div>
          </template>
        </Column>

        <!-- Action Badge Column -->
        <Column :header="t('iam.audit.action')" style="min-width: 160px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <Tag
              :value="data.action"
              :severity="getActionSeverity(data.action)"
              class="action-tag"
            />
          </template>
        </Column>

        <!-- Target Column -->
        <Column :header="t('iam.audit.targetType')" style="min-width: 180px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <div class="target-cell">
              <Tag :value="data.targetType || '-'" severity="secondary" class="target-type-tag" />
              <span v-if="data.targetId" class="target-id">#{{ data.targetId }}</span>
            </div>
          </template>
        </Column>

        <!-- Detail Column -->
        <Column :header="t('iam.audit.detail')" style="min-width: 260px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <span class="detail-cell" :title="data.detail">{{ data.detail || '-' }}</span>
          </template>
        </Column>

        <!-- Client IP Column -->
        <Column :header="t('iam.audit.clientIp')" style="min-width: 140px">
          <template #body="{ data }: { data: AuditLogResponse }">
            <code class="client-ip-code">{{ data.clientIp || '-' }}</code>
          </template>
        </Column>
      </DataTable>
    </div>
  </div>
</template>

<style scoped>
.audit-log-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.id-cell {
  font-family: monospace;
  font-size: 0.8125rem;
  color: var(--atria-text-light);
}

.timestamp-cell {
  font-family: monospace;
  font-size: 0.8125rem;
  color: var(--atria-text-muted);
}

.actor-cell {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  flex-wrap: wrap;
}

.actor-username {
  font-weight: 600;
  color: var(--atria-text);
  font-size: 0.85rem;
}

.actor-id-tag {
  font-size: 0.7rem;
  font-family: monospace;
  padding: 0.1rem 0.35rem;
}

.action-tag {
  font-family: monospace;
  font-weight: 700;
  font-size: 0.75rem;
  letter-spacing: 0.02em;
}

.target-cell {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  flex-wrap: wrap;
}

.target-type-tag {
  font-size: 0.75rem;
}

.target-id {
  font-family: monospace;
  font-size: 0.8125rem;
  color: var(--atria-text-muted);
  font-weight: 500;
}

.detail-cell {
  display: inline-block;
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.8125rem;
  color: var(--atria-text);
}

.client-ip-code {
  font-family: monospace;
  font-size: 0.8125rem;
  background: var(--atria-surface-subtle);
  border: 1px solid var(--atria-border);
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  color: var(--atria-text-muted);
}

.table-empty {
  text-align: center;
  padding: 2.5rem;
  color: var(--atria-text-muted);
}
</style>
