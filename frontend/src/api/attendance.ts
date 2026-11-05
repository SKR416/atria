import { http } from './http'
import type { ApiResponse } from '@/types/api'
import type {
  WorkCalendarDayResponse,
  WorkCalendarDayUpdateRequest,
  GenerateYearCalendarRequest,
  PunchRuleResponse,
  PunchRuleUpdateRequest,
  WebPunchRequest,
  TodayAttendanceSummaryResponse,
  MonthlyOverviewResponse,
  MonthlyCloseRequest,
  MonthlyReopenRequest,
  MonthlyCloseResponse,
  BlockingPendingRequestDto
} from '@/types/attendance'

export const workCalendarApi = {
  async getDays(calendarCode: string, startDate: string, endDate: string): Promise<WorkCalendarDayResponse[]> {
    const res = await http.get<ApiResponse<WorkCalendarDayResponse[]>>('/api/v1/attendance/calendars', {
      params: { calendarCode, startDate, endDate }
    })
    return res.data.data
  },
  async generateYear(payload: GenerateYearCalendarRequest): Promise<WorkCalendarDayResponse[]> {
    const res = await http.post<ApiResponse<WorkCalendarDayResponse[]>>(
      '/api/v1/attendance/calendars/generate-year',
      payload
    )
    return res.data.data
  },
  async upsertDay(
    calendarCode: string,
    workDate: string,
    payload: WorkCalendarDayUpdateRequest
  ): Promise<WorkCalendarDayResponse> {
    const res = await http.put<ApiResponse<WorkCalendarDayResponse>>(
      `/api/v1/attendance/calendars/${encodeURIComponent(calendarCode)}/days/${encodeURIComponent(workDate)}`,
      payload
    )
    return res.data.data
  },
  async getPunchRules(): Promise<PunchRuleResponse[]> {
    const res = await http.get<ApiResponse<PunchRuleResponse[]>>('/api/v1/attendance/punch-rules')
    return res.data.data
  },
  async getPunchRule(calendarCode: string): Promise<PunchRuleResponse> {
    const res = await http.get<ApiResponse<PunchRuleResponse>>(
      `/api/v1/attendance/punch-rules/${encodeURIComponent(calendarCode)}`
    )
    return res.data.data
  },
  async updatePunchRule(calendarCode: string, payload: PunchRuleUpdateRequest): Promise<PunchRuleResponse> {
    const res = await http.put<ApiResponse<PunchRuleResponse>>(
      `/api/v1/attendance/punch-rules/${encodeURIComponent(calendarCode)}`,
      payload
    )
    return res.data.data
  }
}

export const punchApi = {
  async getTodaySummary(): Promise<TodayAttendanceSummaryResponse> {
    const res = await http.get<ApiResponse<TodayAttendanceSummaryResponse>>('/api/v1/attendance/today')
    return res.data.data
  },
  async punchWeb(payload?: WebPunchRequest): Promise<TodayAttendanceSummaryResponse> {
    const res = await http.post<ApiResponse<TodayAttendanceSummaryResponse>>(
      '/api/v1/attendance/punch',
      payload || {}
    )
    return res.data.data
  },
  async getMonthlyOverview(yearMonth: string): Promise<MonthlyOverviewResponse> {
    const res = await http.get<ApiResponse<MonthlyOverviewResponse>>('/api/v1/attendance/monthly-overview', {
      params: { yearMonth }
    })
    return res.data.data
  }
}

export const monthlyCloseApi = {
  async list(calendarCode?: string): Promise<MonthlyCloseResponse[]> {
    const res = await http.get<ApiResponse<MonthlyCloseResponse[]>>('/api/v1/attendance/monthly-closes', {
      params: calendarCode ? { calendarCode } : {}
    })
    return res.data.data
  },
  async checkBlockingRequests(calendarCode: string, yearMonth: string): Promise<BlockingPendingRequestDto[]> {
    const res = await http.get<ApiResponse<BlockingPendingRequestDto[]>>(
      '/api/v1/attendance/monthly-closes/blocking-requests',
      { params: { calendarCode, yearMonth } }
    )
    return res.data.data
  },
  async closeMonth(payload: MonthlyCloseRequest): Promise<MonthlyCloseResponse> {
    const res = await http.post<ApiResponse<MonthlyCloseResponse>>(
      '/api/v1/attendance/monthly-closes/close',
      payload
    )
    return res.data.data
  },
  async reopenMonth(payload: MonthlyReopenRequest): Promise<MonthlyCloseResponse> {
    const res = await http.post<ApiResponse<MonthlyCloseResponse>>(
      '/api/v1/attendance/monthly-closes/reopen',
      payload
    )
    return res.data.data
  },
  async triggerDailySettlement(workDate?: string): Promise<number> {
    const res = await http.post<ApiResponse<number>>('/api/v1/attendance/settlement/run', null, {
      params: workDate ? { workDate } : {}
    })
    return res.data.data
  }
}
