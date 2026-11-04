import type { DataScopeType, I18nMap } from './api'

export interface LoginRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface CurrentUserProfileResponse {
  id: number
  username: string
  displayName: string
  email: string | null
  departmentId: number | null
  departmentTreePath: string | null
  locale: string
  mustChangePassword: boolean
  dataScope: DataScopeType
  roles: string[]
  permissions: string[]
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  mustChangePassword: boolean
  user: CurrentUserProfileResponse
}

export interface DepartmentRequest {
  parentId: number | null
  code: string
  name: I18nMap
  leaderId: number | null
  timezone: string
  sortOrder: number
}

export interface DepartmentTreeNodeResponse {
  id: number
  parentId: number | null
  treePath: string
  code: string
  name: I18nMap
  localizedName: string
  leaderId: number | null
  timezone: string
  sortOrder: number
  children: DepartmentTreeNodeResponse[]
}

export interface UserCreateRequest {
  username: string
  email?: string | null
  initialPassword: string
  displayName: string
  departmentId: number
  hireDate?: string | null
  locale?: string
  roleIds?: number[]
  mustChangePassword?: boolean
}

export interface UserUpdateRequest {
  email?: string | null
  displayName: string
  departmentId: number
  hireDate?: string | null
  locale?: string
  enabled?: boolean
  roleIds?: number[]
}

export interface AdminResetPasswordRequest {
  temporaryPassword: string
  mustChangePassword?: boolean
}

export interface UserResponse {
  id: number
  username: string
  email: string | null
  displayName: string
  departmentId: number
  hireDate: string | null
  locale: string
  enabled: boolean
  mustChangePassword: boolean
  failedLoginAttempts: number
  lockedUntil: string | null
  lastLoginAt: string | null
  roleCodes: string[]
  roleIds: number[]
  createdAt: string
}

export interface PermissionResponse {
  id: number
  code: string
  module: string
  name: I18nMap
  localizedName: string
}

export interface RoleRequest {
  code: string
  name: I18nMap
  dataScope: DataScopeType
  permissionIds: number[]
}

export interface RoleResponse {
  id: number
  code: string
  name: I18nMap
  localizedName: string
  dataScope: DataScopeType
  builtIn: boolean
  permissions: PermissionResponse[]
  permissionCodes: string[]
  createdAt: string
  updatedAt: string
}

export interface AuditLogResponse {
  id: number
  actorId: number | null
  actorUsername: string
  action: string
  targetType: string
  targetId: string
  detail: string
  clientIp: string
  createdAt: string
}
