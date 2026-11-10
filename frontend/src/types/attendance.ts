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

// ============================================================================
// Phase 3b: Leave Types, FIFO Balance Ledger, Overtime Rules & Requests
// ============================================================================

export type AttendanceRequestType = 'LEAVE' | 'OVERTIME' | 'PUNCH_CORRECTION'

export type AttendanceRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'REVOKED'

export type LeaveGrantBasis = 'ANNIVERSARY' | 'CALENDAR_YEAR'

export type OvertimeCompensationType = 'PAY' | 'COMP_LEAVE'

export type CompLeaveConversionMode = 'HOURS_1_TO_1' | 'RATE_MULTIPLIED'

export type OvertimeCapAction = 'WARN' | 'BLOCK'

export interface LeaveTypeResponse {
  id: number
  code: string
  name: I18nMap
  localizedName: string
  minUnitMinutes: number
  paid: boolean
  balanceTracked: boolean
  attachmentRequired: boolean
  enabled: boolean
  sortOrder: number
  description: string | null
}

export interface LeaveTypeUpsertRequest {
  code: string
  name: I18nMap
  minUnitMinutes: number
  paid: boolean
  balanceTracked: boolean
  attachmentRequired: boolean
  enabled: boolean
  sortOrder: number
  description?: string | null
}

export interface LeaveGrantRuleResponse {
  id: number
  calendarCode: string
  leaveTypeCode: string
  grantBasis: LeaveGrantBasis
  minTenureMonths: number
  maxTenureMonths: number | null
  grantDays: number
  grantMinutes: number
  validMonths: number
  note: string | null
}

export interface LeaveGrantRuleUpsertRequest {
  calendarCode: string
  leaveTypeCode: string
  grantBasis: LeaveGrantBasis
  minTenureMonths: number
  maxTenureMonths?: number | null
  grantDays: number
  validMonths: number
  note?: string | null
}

export interface LeaveBalanceBucketDto {
  id: number
  userId: number
  leaveTypeCode: string
  localizedLeaveTypeName: string
  calendarCode: string
  grantYear: number
  totalMinutes: number
  usedMinutes: number
  pendingMinutes: number
  availableMinutes: number
  validFrom: string
  validUntil: string
  sourceCompGrantId: number | null
  mandatoryTargetMinutes: number
  note: string | null
}

export interface LeaveTypeBalanceSummaryDto {
  leaveTypeCode: string
  localizedLeaveTypeName: string
  minUnitMinutes: number
  paid: boolean
  balanceTracked: boolean
  totalMinutes: number
  usedMinutes: number
  pendingMinutes: number
  availableMinutes: number
  buckets: LeaveBalanceBucketDto[]
}

export interface ExpiringCompLeaveDto {
  balanceId: number
  compGrantId: number | null
  workDate: string | null
  remainingMinutes: number
  expiresAt: string
  daysUntilExpiry: number
}

export interface JpFiveDayObligationDto {
  applicable: boolean
  targetMinutes: number
  usedMinutes: number
  pendingMinutes: number
  remainingObligationMinutes: number
  deadlineDate: string | null
  fulfilled: boolean
}

export interface LeaveDashboardResponse {
  userId: number
  calendarCode: string
  asOfDate: string
  balances: LeaveTypeBalanceSummaryDto[]
  expiringCompLeavesWithin90Days: ExpiringCompLeaveDto[]
  jpFiveDayObligation: JpFiveDayObligationDto
}

export interface ManualLeaveGrantRequest {
  userId: number
  leaveTypeCode: string
  grantMinutes: number
  validFrom: string
  validUntil: string
  note?: string | null
}

export interface AnnualLeaveGrantBatchResultDto {
  asOfDate: string
  grantedCount: number
  skippedAlreadyGrantedCount: number
  skippedMissingHireDateUsernames: string[]
  grantedBuckets: LeaveBalanceBucketDto[]
}

export interface OvertimeRateRuleResponse {
  id: number
  calendarCode: string
  dayType: CalendarDayType
  fromMinute: number
  toMinute: number
  multiplier: number
  nightPremium: boolean
  monthlyCumulative: boolean
  defaultCompLeaveMode: CompLeaveConversionMode
  description: string | null
}

export interface OvertimeRateRuleUpsertRequest {
  calendarCode: string
  dayType: CalendarDayType
  fromMinute: number
  toMinute: number
  multiplier: number
  nightPremium: boolean
  monthlyCumulative: boolean
  defaultCompLeaveMode: CompLeaveConversionMode
  description?: string | null
}

export interface OvertimeCapRuleResponse {
  id: number
  calendarCode: string
  ruleCode: string
  name: I18nMap
  localizedName: string
  periodMonths: number
  maxMinutes: number
  enforcementAction: OvertimeCapAction
  enabled: boolean
  description: string | null
}

export interface OvertimeCapRuleUpsertRequest {
  calendarCode: string
  ruleCode: string
  name: I18nMap
  periodMonths: number
  maxMinutes: number
  enforcementAction: OvertimeCapAction
  enabled: boolean
  description?: string | null
}

export interface OvertimeBandBreakdownItem {
  fromMinute: number
  toMinute: number
  minutes: number
  multiplier: string
  weightedMinutes: number
}

export interface CompLeaveGrantResponse {
  id: number
  userId: number
  calendarCode: string
  sourceRequestId: number
  workDate: string
  dayType: CalendarDayType
  compLeaveMode: CompLeaveConversionMode
  originalOvertimeMinutes: number
  grantedMinutes: number
  usedMinutes: number
  pendingMinutes: number
  availableMinutes: number
  residualPayableMinutes: number
  bandBreakdown: OvertimeBandBreakdownItem[]
  grantedAt: string
  expiresAt: string
  expiredSettled: boolean
  expiredPayableWeightedMinutes: number
}

export interface CompLeaveExpirySettlementResultDto {
  compGrantId: number
  balanceId: number
  userId: number
  grantedMinutes: number
  usedMinutes: number
  unusedMinutes: number
  restoredOriginalOvertimeMinutes: number
  payableWeightedMinutes: number
  restoredBands: OvertimeBandBreakdownItem[]
}

export interface LeaveRequestSubmitDto {
  leaveTypeCode: string
  startTime: string
  endTime: string
  reason: string
  attachmentPath?: string | null
}

export interface OvertimeRequestSubmitDto {
  workDate: string
  startTime: string
  endTime: string
  overtimeCompType: OvertimeCompensationType
  compLeaveMode?: CompLeaveConversionMode | null
  reason: string
}

export interface PunchCorrectionRequestSubmitDto {
  workDate: string
  correctedPunchTime: string
  reason: string
  attachmentPath?: string | null
}

export interface AttendanceRequestDecisionDto {
  comment?: string | null
}

export interface AttendanceRequestRevokeDto {
  reason: string
}

export interface AttendanceRequestResponse {
  id: number
  userId: number
  username: string
  displayName: string
  departmentId: number | null
  calendarCode: string
  requestType: AttendanceRequestType
  status: AttendanceRequestStatus
  leaveTypeCode: string | null
  localizedLeaveTypeName: string | null
  startDate: string
  endDate: string
  startTime: string
  endTime: string
  requestedMinutes: number
  weightedMinutes: number
  overtimeCompType: OvertimeCompensationType | null
  compLeaveMode: CompLeaveConversionMode | null
  reason: string
  attachmentPath: string | null
  warningMessages: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  reviewComment: string | null
  revokedBy: number | null
  revokedAt: string | null
  revokeReason: string | null
  createdAt: string
}
