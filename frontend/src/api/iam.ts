import { http } from './http'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  AdminResetPasswordRequest,
  AuditLogResponse,
  ChangePasswordRequest,
  CurrentUserProfileResponse,
  DepartmentRequest,
  DepartmentTreeNodeResponse,
  LoginRequest,
  LoginResponse,
  PermissionResponse,
  RoleRequest,
  RoleResponse,
  UserCreateRequest,
  UserResponse,
  UserUpdateRequest
} from '@/types/iam'

export const authApi = {
  async login(payload: LoginRequest): Promise<LoginResponse> {
    const res = await http.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', payload)
    return res.data.data
  },

  async changePassword(payload: ChangePasswordRequest): Promise<LoginResponse> {
    const res = await http.post<ApiResponse<LoginResponse>>('/api/v1/auth/change-password', payload)
    return res.data.data
  },

  async refresh(): Promise<LoginResponse> {
    const res = await http.post<ApiResponse<LoginResponse>>('/api/v1/auth/refresh', {})
    return res.data.data
  },

  async logout(): Promise<void> {
    await http.post<ApiResponse<void>>('/api/v1/auth/logout', {})
  },

  async me(): Promise<CurrentUserProfileResponse> {
    const res = await http.get<ApiResponse<CurrentUserProfileResponse>>('/api/v1/auth/me')
    return res.data.data
  }
}

export const departmentApi = {
  async getTree(): Promise<DepartmentTreeNodeResponse[]> {
    const res = await http.get<ApiResponse<DepartmentTreeNodeResponse[]>>('/api/v1/iam/departments/tree')
    return res.data.data
  },

  async getById(id: number): Promise<DepartmentTreeNodeResponse> {
    const res = await http.get<ApiResponse<DepartmentTreeNodeResponse>>(`/api/v1/iam/departments/${id}`)
    return res.data.data
  },

  async create(payload: DepartmentRequest): Promise<DepartmentTreeNodeResponse> {
    const res = await http.post<ApiResponse<DepartmentTreeNodeResponse>>('/api/v1/iam/departments', payload)
    return res.data.data
  },

  async update(id: number, payload: DepartmentRequest): Promise<DepartmentTreeNodeResponse> {
    const res = await http.put<ApiResponse<DepartmentTreeNodeResponse>>(`/api/v1/iam/departments/${id}`, payload)
    return res.data.data
  },

  async remove(id: number): Promise<void> {
    await http.delete<ApiResponse<void>>(`/api/v1/iam/departments/${id}`)
  }
}

export const userApi = {
  async list(params: {
    page?: number
    size?: number
    departmentId?: number | null
    keyword?: string
  }): Promise<PageResult<UserResponse>> {
    const query: Record<string, unknown> = {
      page: params.page ?? 1,
      size: params.size ?? 20
    }
    if (params.departmentId) query.departmentId = params.departmentId
    if (params.keyword && params.keyword.trim()) query.keyword = params.keyword.trim()
    const res = await http.get<ApiResponse<PageResult<UserResponse>>>('/api/v1/iam/users', { params: query })
    return res.data.data
  },

  async getById(id: number): Promise<UserResponse> {
    const res = await http.get<ApiResponse<UserResponse>>(`/api/v1/iam/users/${id}`)
    return res.data.data
  },

  async create(payload: UserCreateRequest): Promise<UserResponse> {
    const res = await http.post<ApiResponse<UserResponse>>('/api/v1/iam/users', payload)
    return res.data.data
  },

  async update(id: number, payload: UserUpdateRequest): Promise<UserResponse> {
    const res = await http.put<ApiResponse<UserResponse>>(`/api/v1/iam/users/${id}`, payload)
    return res.data.data
  },

  async remove(id: number): Promise<void> {
    await http.delete<ApiResponse<void>>(`/api/v1/iam/users/${id}`)
  },

  async adminResetPassword(id: number, payload: AdminResetPasswordRequest): Promise<void> {
    await http.post<ApiResponse<void>>(`/api/v1/iam/users/${id}/reset-password`, payload)
  },

  async unlock(id: number): Promise<void> {
    await http.post<ApiResponse<void>>(`/api/v1/iam/users/${id}/unlock`, {})
  }
}

export const roleApi = {
  async listRoles(): Promise<RoleResponse[]> {
    const res = await http.get<ApiResponse<RoleResponse[]>>('/api/v1/iam/roles')
    return res.data.data
  },

  async getRole(id: number): Promise<RoleResponse> {
    const res = await http.get<ApiResponse<RoleResponse>>(`/api/v1/iam/roles/${id}`)
    return res.data.data
  },

  async createRole(payload: RoleRequest): Promise<RoleResponse> {
    const res = await http.post<ApiResponse<RoleResponse>>('/api/v1/iam/roles', payload)
    return res.data.data
  },

  async updateRole(id: number, payload: RoleRequest): Promise<RoleResponse> {
    const res = await http.put<ApiResponse<RoleResponse>>(`/api/v1/iam/roles/${id}`, payload)
    return res.data.data
  },

  async deleteRole(id: number): Promise<void> {
    await http.delete<ApiResponse<void>>(`/api/v1/iam/roles/${id}`)
  },

  async listPermissions(): Promise<PermissionResponse[]> {
    const res = await http.get<ApiResponse<PermissionResponse[]>>('/api/v1/iam/permissions')
    return res.data.data
  }
}

export const auditLogApi = {
  async query(params: { page?: number; size?: number }): Promise<PageResult<AuditLogResponse>> {
    const res = await http.get<ApiResponse<PageResult<AuditLogResponse>>>('/api/v1/iam/audit-logs', {
      params: {
        page: params.page ?? 1,
        size: params.size ?? 20
      }
    })
    return res.data.data
  }
}
