<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { notificationApi, type NotificationItem, type NotificationCategory } from '@/api/notification'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { Bell, CheckCheck, RefreshCw, Inbox, Check } from 'lucide-vue-next'

const { t, locale } = useI18n()
const toast = useToast()

const unreadCount = ref<number>(0)
const notifications = ref<NotificationItem[]>([])
const unreadOnly = ref<boolean>(false)
const dialogVisible = ref<boolean>(false)
const loading = ref<boolean>(false)
const markingItem = ref<Record<number, boolean>>({})
let pollTimer: ReturnType<typeof setInterval> | null = null

async function fetchUnreadCount() {
  try {
    unreadCount.value = await notificationApi.countUnread()
  } catch (err: unknown) {
    console.error('Failed to fetch unread notifications count', err)
  }
}

async function loadNotifications() {
  loading.value = true
  try {
    notifications.value = await notificationApi.listMyNotifications(unreadOnly.value)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loading.value = false
  }
}

function openDialog() {
  dialogVisible.value = true
  fetchUnreadCount()
  loadNotifications()
}

async function handleMarkRead(id: number) {
  markingItem.value[id] = true
  try {
    const updated = await notificationApi.markAsRead(id)
    const idx = notifications.value.findIndex((n) => n.id === id)
    if (idx !== -1) {
      notifications.value[idx] = updated
    }
    if (unreadOnly.value) {
      notifications.value = notifications.value.filter((n) => n.id !== id)
    }
    await fetchUnreadCount()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    markingItem.value[id] = false
  }
}

async function handleMarkAllRead() {
  loading.value = true
  try {
    await notificationApi.markAllAsRead()
    await Promise.all([fetchUnreadCount(), loadNotifications()])
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('notification.markAllRead'),
      life: 2500
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : t('common.error')
    toast.add({ severity: 'error', summary: t('common.error'), detail: msg, life: 3000 })
  } finally {
    loading.value = false
  }
}

function getCategorySeverity(category: NotificationCategory): 'info' | 'warn' | 'secondary' {
  switch (category) {
    case 'WORKFLOW':
      return 'info'
    case 'LEAVE_DIGEST':
      return 'warn'
    case 'SYSTEM':
    default:
      return 'secondary'
  }
}

function getLocalizedText(field: Record<string, string> | undefined, fallback: string): string {
  if (!field) return fallback || ''
  const currentLoc = (locale.value as string) || 'zh-TW'
  return field[currentLoc] || field['zh-TW'] || field['en-US'] || fallback || ''
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

onMounted(() => {
  fetchUnreadCount()
  pollTimer = setInterval(fetchUnreadCount, 45000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="notification-bell-wrapper">
    <Button
      size="small"
      severity="secondary"
      text
      class="bell-btn"
      data-testid="notification-bell-btn"
      :title="t('notification.bellTitle')"
      @click="openDialog"
    >
      <template #icon>
        <div class="bell-icon-container">
          <Bell :size="16" />
          <span
            v-if="unreadCount > 0"
            class="bell-badge font-mono"
            data-testid="notification-unread-badge"
          >
            {{ unreadCount > 99 ? '99+' : unreadCount }}
          </span>
        </div>
      </template>
    </Button>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="t('notification.bellTitle')"
      :style="{ width: '560px', maxWidth: '95vw' }"
      data-testid="notification-dialog"
    >
      <div class="dialog-body">
        <!-- Dialog Toolbar -->
        <div class="dialog-toolbar">
          <label class="unread-filter-label">
            <input
              v-model="unreadOnly"
              type="checkbox"
              class="unread-checkbox"
              @change="loadNotifications"
            />
            <span>{{ t('notification.unreadOnly') }}</span>
          </label>

          <div class="toolbar-actions">
            <Button
              size="small"
              severity="secondary"
              text
              :loading="loading"
              :title="t('common.refresh')"
              @click="loadNotifications"
            >
              <template #icon>
                <RefreshCw :size="14" />
              </template>
            </Button>
            <Button
              size="small"
              severity="primary"
              outlined
              :label="t('notification.markAllRead')"
              data-testid="notification-mark-all-read-btn"
              :disabled="unreadCount === 0 || loading"
              @click="handleMarkAllRead"
            >
              <template #icon>
                <CheckCheck :size="14" class="btn-icon" />
              </template>
            </Button>
          </div>
        </div>

        <!-- Notification List -->
        <div v-if="loading && notifications.length === 0" class="loading-state">
          <span>{{ t('common.loading') }}</span>
        </div>

        <div v-else-if="notifications.length === 0" class="empty-state">
          <Inbox :size="36" class="empty-icon" />
          <p>{{ t('notification.empty') }}</p>
        </div>

        <div v-else class="notification-list">
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notification-card"
            :class="{ 'card-unread': !item.read }"
          >
            <div class="card-top-row">
              <div class="card-meta">
                <Tag
                  :value="t('notification.categories.' + item.category)"
                  :severity="getCategorySeverity(item.category)"
                  class="category-tag"
                />
                <span class="card-time font-mono">{{ formatTimestamp(item.createdAt) }}</span>
              </div>
              <Button
                v-if="!item.read"
                size="small"
                severity="secondary"
                text
                :label="t('notification.markRead')"
                :loading="markingItem[item.id]"
                class="mark-read-btn"
                @click="handleMarkRead(item.id)"
              >
                <template #icon>
                  <Check :size="13" class="btn-icon" />
                </template>
              </Button>
            </div>

            <div class="card-title font-bold">
              {{ getLocalizedText(item.title, item.localizedTitle) }}
            </div>

            <div class="card-content">
              {{ getLocalizedText(item.content, item.localizedContent) }}
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          size="small"
          severity="secondary"
          outlined
          :label="t('common.close')"
          @click="dialogVisible = false"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.notification-bell-wrapper {
  display: inline-flex;
  align-items: center;
}

.bell-btn {
  position: relative;
  width: 32px;
  height: 32px;
  padding: 0;
}

.bell-icon-container {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.bell-badge {
  position: absolute;
  top: -8px;
  right: -10px;
  background-color: #ef4444;
  color: #ffffff;
  font-size: 0.625rem;
  font-weight: 700;
  line-height: 1;
  padding: 0.125rem 0.3125rem;
  border-radius: 999px;
  min-width: 16px;
  text-align: center;
  box-shadow: 0 0 0 1.5px #ffffff;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--atria-border, #e2e8f0);
}

.unread-filter-label {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8125rem;
  color: var(--atria-text, #1e293b);
  cursor: pointer;
  user-select: none;
}

.unread-checkbox {
  cursor: pointer;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.btn-icon {
  margin-right: 0.25rem;
}

.loading-state {
  text-align: center;
  padding: 2rem 1rem;
  color: var(--atria-text-muted, #64748b);
  font-size: 0.875rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2.5rem 1rem;
  gap: 0.5rem;
  color: var(--atria-text-muted, #64748b);
  font-size: 0.875rem;
}

.empty-icon {
  color: #94a3b8;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  max-height: 420px;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.notification-card {
  padding: 0.75rem;
  border-radius: 6px;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #e2e8f0);
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  transition: all 0.15s ease;
}

.card-unread {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.card-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.category-tag {
  font-size: 0.6875rem !important;
  padding: 0.1rem 0.4rem !important;
}

.card-time {
  font-size: 0.6875rem;
  color: var(--atria-text-muted, #64748b);
}

.mark-read-btn {
  font-size: 0.75rem;
  padding: 0.15rem 0.35rem;
}

.card-title {
  font-size: 0.875rem;
  color: var(--atria-text, #0f172a);
}

.card-content {
  font-size: 0.8125rem;
  color: var(--atria-text-muted, #334155);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.4;
}
</style>
