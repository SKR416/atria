import type { I18nMap } from './api'

export type CalendarDayType =
  | 'WORKDAY'
  | 'MAKEUP_WORKDAY'
  | 'REST_DAY'
  | 'STATUTORY_REST_DAY'
  | 'NATIONAL_HOLIDAY'

export type PunchSource = 'WEB' | 'BOT' | 'CORRECTION'

export type AttendanceStatus =
  | 'NORMAL'
  | 'LATE'
  | 'EARLY_LEAVE'
  | 'MISSING_PUNCH'
  | 'ON_LEAVE'
  | 'ABSENT'
  | 'HOLIDAY'

export type MonthlyCloseStatus = 'CLOSED' | 'REOPENED'

export interface WorkCalendarDayResponse {
  id: number
  calendarCode: string
  workDate: string
  dayType: CalendarDayType
  workingDay: boolean
  workStartTime: string
  workEndTime: string
  breakStartTime: string
  breakEndTime: string
  holidayName: I18nMap | null
  localizedHolidayName: string | null
  note: string | null
}

export interface WorkCalendarDayUpdateRequest {
  dayType: CalendarDayType
  workStartTime?: string
  workEndTime?: string
  breakStartTime?: string
  breakEndTime?: string
  holidayName?: I18nMap | null
  note?: string | null
}

export interface GenerateYearCalendarRequest {
  calendarCode: string
  year: number
  overwriteExisting: boolean
}

export interface PunchRuleResponse {
  id: number
  calendarCode: string
  lateGraceMinutes: number
  earlyLeaveGraceMinutes: number
  clockInRoundingMinutes: number
  clockOutRoundingMinutes: number
  dayCutoffHour: number
  minPunchIntervalSeconds: number
  maxRetroactiveDays: number
  autoMonthlyCloseDay: number
  updatedAt: string
}

export interface PunchRuleUpdateRequest {
  lateGraceMinutes: number
  earlyLeaveGraceMinutes: number
  clockInRoundingMinutes: number
  clockOutRoundingMinutes: number
  dayCutoffHour: number
  minPunchIntervalSeconds: number
  maxRetroactiveDays: number
  autoMonthlyCloseDay: number
}

export interface WebPunchRequest {
  note?: string | null
}

export interface PunchLogResponse {
  id: number
  userId: number
  departmentId: number | null
  workDate: string
  rawPunchTime: string
  clientIp: string
  source: PunchSource
  botClientId: string | null
  correctionRequestId: number | null
  note: string | null
  createdAt: string
}

export interface DailyAttendanceRecordResponse {
  id: number
  userId: number
  departmentId: number | null
  calendarCode: string
  workDate: string
  dayType: CalendarDayType
  scheduledStartTime: string | null
  scheduledEndTime: string | null
  firstRawClockIn: string | null
  lastRawClockOut: string | null
  effectiveClockIn: string | null
  effectiveClockOut: string | null
  workedMinutes: number
  lateMinutes: number
  earlyLeaveMinutes: number
  leaveMinutes: number
  overtimeMinutes: number
  status: AttendanceStatus
  punchCount: number
  settled: boolean
  note: string | null
  updatedAt: string
}

export interface TodayAttendanceSummaryResponse {
  userId: number
  departmentId: number | null
  calendarCode: string
  timezone: string
  serverUtcNow: string
  logicalWorkDate: string
  dayCutoffHour: number
  dayType: CalendarDayType
  workingDay: boolean
  localizedHolidayName: string | null
  scheduledStartTime: string | null
  scheduledEndTime: string | null
  monthlyPeriodClosed: boolean
  dailyRecord: DailyAttendanceRecordResponse | null
  punchLogs: PunchLogResponse[]
}

export interface MonthlyOverviewResponse {
  userId: number
  calendarCode: string
  timezone: string
  yearMonth: string
  monthlyClosed: boolean
  totalWorkedMinutes: number
  normalDays: number
  lateCount: number
  earlyLeaveCount: number
  missingPunchCount: number
  absentCount: number
  calendarDays: WorkCalendarDayResponse[]
  dailyRecords: DailyAttendanceRecordResponse[]
  punchLogs: PunchLogResponse[]
}

export interface BlockingPendingRequestDto {
  requestId: number
  userId: number
  username: string
  displayName: string
  requestType: string
  startDate: string
  endDate: string
  reason: string | null
}

export interface MonthlyCloseRequest {
  calendarCode: string
  yearMonth: string
  note?: string | null
}

export interface MonthlyReopenRequest {
  calendarCode: string
  yearMonth: string
  note?: string | null
}

export interface MonthlyCloseResponse {
  id: number
  calendarCode: string
  yearMonth: string
  status: MonthlyCloseStatus
  closedAt: string
  closedBy: number | null
  closedByUsername: string | null
  reopenedAt: string | null
  reopenedBy: number | null
  note: string | null
  updatedAt: string
}
