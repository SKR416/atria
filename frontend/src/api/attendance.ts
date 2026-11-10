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
  BlockingPendingRequestDto,
  LeaveTypeResponse,
  LeaveTypeUpsertRequest,
  LeaveGrantRuleResponse,
  LeaveGrantRuleUpsertRequest,
  LeaveBalanceBucketDto,
  LeaveDashboardResponse,
  ManualLeaveGrantRequest,
  AnnualLeaveGrantBatchResultDto,
  OvertimeRateRuleResponse,
  OvertimeRateRuleUpsertRequest,
  OvertimeCapRuleResponse,
  OvertimeCapRuleUpsertRequest,
  CompLeaveGrantResponse,
  CompLeaveExpirySettlementResultDto,
  LeaveRequestSubmitDto,
  OvertimeRequestSubmitDto,
  PunchCorrectionRequestSubmitDto,
  AttendanceRequestDecisionDto,
  AttendanceRequestRevokeDto,
  AttendanceRequestResponse,
  AttendanceRequestStatus
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

export const leaveOvertimeRuleApi = {
  async listLeaveTypes(enabledOnly = false): Promise<LeaveTypeResponse[]> {
    const res = await http.get<ApiResponse<LeaveTypeResponse[]>>('/api/v1/attendance/leave-types', {
      params: { enabledOnly }
    })
    return res.data.data
  },
  async createLeaveType(payload: LeaveTypeUpsertRequest): Promise<LeaveTypeResponse> {
    const res = await http.post<ApiResponse<LeaveTypeResponse>>('/api/v1/attendance/leave-types', payload)
    return res.data.data
  },
  async updateLeaveType(id: number, payload: LeaveTypeUpsertRequest): Promise<LeaveTypeResponse> {
    const res = await http.put<ApiResponse<LeaveTypeResponse>>(
      `/api/v1/attendance/leave-types/${id}`,
      payload
    )
    return res.data.data
  },
  async listLeaveGrantRules(calendarCode?: string): Promise<LeaveGrantRuleResponse[]> {
    const res = await http.get<ApiResponse<LeaveGrantRuleResponse[]>>('/api/v1/attendance/leave-grant-rules', {
      params: calendarCode ? { calendarCode } : {}
    })
    return res.data.data
  },
  async createLeaveGrantRule(payload: LeaveGrantRuleUpsertRequest): Promise<LeaveGrantRuleResponse> {
    const res = await http.post<ApiResponse<LeaveGrantRuleResponse>>(
      '/api/v1/attendance/leave-grant-rules',
      payload
    )
    return res.data.data
  },
  async updateLeaveGrantRule(id: number, payload: LeaveGrantRuleUpsertRequest): Promise<LeaveGrantRuleResponse> {
    const res = await http.put<ApiResponse<LeaveGrantRuleResponse>>(
      `/api/v1/attendance/leave-grant-rules/${id}`,
      payload
    )
    return res.data.data
  },
  async runAnnualLeaveGrant(asOfDate?: string): Promise<AnnualLeaveGrantBatchResultDto> {
    const res = await http.post<ApiResponse<AnnualLeaveGrantBatchResultDto>>(
      '/api/v1/attendance/leave-balances/grant-annual',
      null,
      { params: asOfDate ? { asOfDate } : {} }
    )
    return res.data.data
  },
  async manualGrantLeave(payload: ManualLeaveGrantRequest): Promise<LeaveBalanceBucketDto> {
    const res = await http.post<ApiResponse<LeaveBalanceBucketDto>>(
      '/api/v1/attendance/leave-balances/manual-grant',
      payload
    )
    return res.data.data
  },
  async getLeaveDashboard(userId?: number, asOfDate?: string): Promise<LeaveDashboardResponse> {
    const params: Record<string, string | number> = {}
    if (userId !== undefined) params.userId = userId
    if (asOfDate) params.asOfDate = asOfDate
    const res = await http.get<ApiResponse<LeaveDashboardResponse>>('/api/v1/attendance/leave-dashboard', {
      params
    })
    return res.data.data
  },
  async listOvertimeRateRules(calendarCode?: string): Promise<OvertimeRateRuleResponse[]> {
    const res = await http.get<ApiResponse<OvertimeRateRuleResponse[]>>(
      '/api/v1/attendance/overtime-rate-rules',
      { params: calendarCode ? { calendarCode } : {} }
    )
    return res.data.data
  },
  async createOvertimeRateRule(payload: OvertimeRateRuleUpsertRequest): Promise<OvertimeRateRuleResponse> {
    const res = await http.post<ApiResponse<OvertimeRateRuleResponse>>(
      '/api/v1/attendance/overtime-rate-rules',
      payload
    )
    return res.data.data
  },
  async updateOvertimeRateRule(
    id: number,
    payload: OvertimeRateRuleUpsertRequest
  ): Promise<OvertimeRateRuleResponse> {
    const res = await http.put<ApiResponse<OvertimeRateRuleResponse>>(
      `/api/v1/attendance/overtime-rate-rules/${id}`,
      payload
    )
    return res.data.data
  },
  async listOvertimeCapRules(calendarCode?: string): Promise<OvertimeCapRuleResponse[]> {
    const res = await http.get<ApiResponse<OvertimeCapRuleResponse[]>>(
      '/api/v1/attendance/overtime-cap-rules',
      { params: calendarCode ? { calendarCode } : {} }
    )
    return res.data.data
  },
  async createOvertimeCapRule(payload: OvertimeCapRuleUpsertRequest): Promise<OvertimeCapRuleResponse> {
    const res = await http.post<ApiResponse<OvertimeCapRuleResponse>>(
      '/api/v1/attendance/overtime-cap-rules',
      payload
    )
    return res.data.data
  },
  async updateOvertimeCapRule(
    id: number,
    payload: OvertimeCapRuleUpsertRequest
  ): Promise<OvertimeCapRuleResponse> {
    const res = await http.put<ApiResponse<OvertimeCapRuleResponse>>(
      `/api/v1/attendance/overtime-cap-rules/${id}`,
      payload
    )
    return res.data.data
  },
  async listCompLeaveGrants(userId?: number): Promise<CompLeaveGrantResponse[]> {
    const res = await http.get<ApiResponse<CompLeaveGrantResponse[]>>(
      '/api/v1/attendance/comp-leave-grants',
      { params: userId !== undefined ? { userId } : {} }
    )
    return res.data.data
  },
  async settleExpiredCompLeave(grantId: number): Promise<CompLeaveExpirySettlementResultDto> {
    const res = await http.post<ApiResponse<CompLeaveExpirySettlementResultDto>>(
      `/api/v1/attendance/comp-leave-grants/${grantId}/settle-expired`
    )
    return res.data.data
  }
}

export const attendanceRequestApi = {
  async listMyRequests(): Promise<AttendanceRequestResponse[]> {
    const res = await http.get<ApiResponse<AttendanceRequestResponse[]>>('/api/v1/attendance/requests/my')
    return res.data.data
  },
  async listAllRequests(status?: AttendanceRequestStatus): Promise<AttendanceRequestResponse[]> {
    const res = await http.get<ApiResponse<AttendanceRequestResponse[]>>('/api/v1/attendance/requests', {
      params: status ? { status } : {}
    })
    return res.data.data
  },
  async submitLeave(payload: LeaveRequestSubmitDto): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      '/api/v1/attendance/requests/leave',
      payload
    )
    return res.data.data
  },
  async submitOvertime(payload: OvertimeRequestSubmitDto): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      '/api/v1/attendance/requests/overtime',
      payload
    )
    return res.data.data
  },
  async submitPunchCorrection(payload: PunchCorrectionRequestSubmitDto): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      '/api/v1/attendance/requests/punch-correction',
      payload
    )
    return res.data.data
  },
  async cancelRequest(id: number): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      `/api/v1/attendance/requests/${id}/cancel`
    )
    return res.data.data
  },
  async stubApprove(id: number, payload?: AttendanceRequestDecisionDto): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      `/api/v1/attendance/requests/${id}/stub-approve`,
      payload || {}
    )
    return res.data.data
  },
  async stubReject(id: number, payload?: AttendanceRequestDecisionDto): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      `/api/v1/attendance/requests/${id}/stub-reject`,
      payload || {}
    )
    return res.data.data
  },
  async stubRevoke(id: number, payload: AttendanceRequestRevokeDto): Promise<AttendanceRequestResponse> {
    const res = await http.post<ApiResponse<AttendanceRequestResponse>>(
      `/api/v1/attendance/requests/${id}/stub-revoke`,
      payload
    )
    return res.data.data
  }
}
