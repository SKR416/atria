import { http } from './http'
import type { ApiResponse } from '@/types/api'

export type NotificationCategory = 'WORKFLOW' | 'LEAVE_DIGEST' | 'SYSTEM'

export interface NotificationItem {
  id: number
  recipientUserId: number
  category: NotificationCategory
  title: Record<string, string>
  content: Record<string, string>
  localizedTitle: string
  localizedContent: string
  referenceType: string | null
  referenceId: number | null
  read: boolean
  readAt: string | null
  emailSent: boolean
  createdAt: string
}

export interface DigestBatchResult {
  periodYearMonth: string
  totalActiveUsers: number
  sentCount: number
  skippedCount: number
}

export const notificationApi = {
  async listMyNotifications(unreadOnly = false): Promise<NotificationItem[]> {
    const res = await http.get<ApiResponse<NotificationItem[]>>('/v1/notifications', {
      params: { unreadOnly }
    })
    return res.data.data
  },

  async countUnread(): Promise<number> {
    const res = await http.get<ApiResponse<{ unreadCount: number }>>('/v1/notifications/unread-count')
    return res.data.data.unreadCount
  },

  async markAsRead(id: number): Promise<NotificationItem> {
    const res = await http.put<ApiResponse<NotificationItem>>(`/v1/notifications/${id}/read`)
    return res.data.data
  },

  async markAllAsRead(): Promise<number> {
    const res = await http.put<ApiResponse<{ updatedCount: number }>>('/v1/notifications/read-all')
    return res.data.data.updatedCount
  },

  async triggerMonthlyDigest(periodYearMonth?: string, asOfDate?: string): Promise<DigestBatchResult> {
    const res = await http.post<ApiResponse<DigestBatchResult>>('/v1/notifications/digest/trigger', {
      periodYearMonth,
      asOfDate
    })
    return res.data.data
  }
}
