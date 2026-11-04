export type SupportedLocale = 'zh-TW' | 'en-US' | 'ja-JP'

export type DataScopeType = 'ALL' | 'DEPT_AND_SUB' | 'DEPT_ONLY' | 'SELF_ONLY'

export interface I18nMap {
  'zh-TW'?: string
  'en-US'?: string
  'ja-JP'?: string
  [key: string]: string | undefined
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}
