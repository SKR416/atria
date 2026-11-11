import { http } from './http'
import type { ApiResponse } from '@/types/api'

export type WorkflowTargetType = 'ATT_LEAVE' | 'ATT_OVERTIME' | 'ATT_PUNCH_CORRECTION' | 'DYNAMIC_FORM'
export type WorkflowInstanceStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'REVOKED'
export type ApproverStrategy = 'DIRECT_LEADER' | 'PARENT_LEADER' | 'SPECIFIC_ROLE'
export type ConditionOperator = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'IN'
export type WorkflowTaskAction =
  | 'SUBMIT'
  | 'RESUBMIT'
  | 'APPROVE'
  | 'REJECT'
  | 'RETURN_TO_DRAFT'
  | 'CANCEL'
  | 'REVOKE'
  | 'REASSIGN'
  | 'AUTO_SKIP'

export interface FormFieldOption {
  value: string
  label: Record<string, string>
}

export interface FormFieldSchema {
  key: string
  type: 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'SELECT' | 'DATE'
  label: Record<string, string>
  required: boolean
  min?: number
  max?: number
  options?: FormFieldOption[]
}

export interface FormTemplateDto {
  id: number
  code: string
  name: Record<string, string>
  category: string
  currentVersion: number
  schemaJson: FormFieldSchema[]
  enabled: boolean
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface FormTemplateUpsertPayload {
  code: string
  name: Record<string, string>
  category: string
  schemaJson: FormFieldSchema[]
  enabled: boolean
  description?: string | null
}

export interface FlowNodeConditionDto {
  id?: number
  flowNodeId?: number
  logicalGroup: number
  fieldKey: string
  operator: ConditionOperator
  expectedValue: string
}

export interface FlowNodeDto {
  id?: number
  flowDefinitionId?: number
  stepOrder: number
  nodeCode: string
  name: Record<string, string>
  approverStrategy: ApproverStrategy
  roleCode: string | null
  selfApprovalAutoSkip: boolean
  allowDeputy: boolean
  conditions: FlowNodeConditionDto[]
}

export interface FlowDefinitionDto {
  id: number
  code: string
  name: Record<string, string>
  targetType: WorkflowTargetType
  formTemplateCode: string | null
  enabled: boolean
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface FlowDefinitionDetailDto extends FlowDefinitionDto {
  nodes: FlowNodeDto[]
}

export interface WorkflowDeputyDto {
  id: number
  delegatorUserId: number
  delegatorUserName: string
  deputyUserId: number
  deputyUserName: string
  startDate: string
  endDate: string
  enabled: boolean
  reason: string | null
  createdAt: string
}

export interface WorkflowDeputyCreatePayload {
  delegatorUserId?: number
  deputyUserId: number
  startDate: string
  endDate: string
  reason?: string
}

export interface DynamicFormSubmitPayload {
  instanceId?: number | null
  formTemplateCode: string
  title: string
  formData: Record<string, unknown>
  comment?: string
}

export interface WorkflowTaskLogDto {
  id: number
  instanceId: number
  stepOrder: number
  nodeName: Record<string, string>
  action: WorkflowTaskAction
  assignedApproverId: number | null
  assignedApproverName: string | null
  actorUserId: number
  actorUserName: string
  delegatorUserId: number | null
  delegatorUserName: string | null
  reassignedToUserId: number | null
  reassignedToUserName: string | null
  comment: string | null
  actedAt: string
}

export interface WorkflowFieldDiffDto {
  fieldKey: string
  oldValue: unknown
  newValue: unknown
}

export interface WorkflowInstanceDto {
  id: number
  instanceNo: string
  flowDefinitionId: number
  targetType: WorkflowTargetType
  attRequestId: number | null
  formTemplateCode: string | null
  formVersion: number | null
  applicantId: number
  applicantName: string
  departmentId: number
  departmentName: string
  title: string
  status: WorkflowInstanceStatus
  currentStepOrder: number | null
  currentApproverId: number | null
  currentApproverName: string | null
  submissionCount: number
  submittedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface WorkflowInstanceDetailDto extends WorkflowInstanceDto {
  formDataJson: Record<string, unknown>
  previousFormDataJson: Record<string, unknown> | null
  diffJson: WorkflowFieldDiffDto[] | null
  schemaSnapshotJson: FormFieldSchema[] | null
  taskLogs: WorkflowTaskLogDto[]
}

export const workflowApi = {
  async listTemplates(enabledOnly = false): Promise<FormTemplateDto[]> {
    const res = await http.get<ApiResponse<FormTemplateDto[]>>('/v1/workflow/templates', {
      params: { enabledOnly }
    })
    return res.data.data
  },

  async createTemplate(payload: FormTemplateUpsertPayload): Promise<FormTemplateDto> {
    const res = await http.post<ApiResponse<FormTemplateDto>>('/v1/workflow/templates', payload)
    return res.data.data
  },

  async updateTemplate(id: number, payload: FormTemplateUpsertPayload): Promise<FormTemplateDto> {
    const res = await http.put<ApiResponse<FormTemplateDto>>(`/v1/workflow/templates/${id}`, payload)
    return res.data.data
  },

  async listFlows(): Promise<FlowDefinitionDto[]> {
    const res = await http.get<ApiResponse<FlowDefinitionDto[]>>('/v1/workflow/flows')
    return res.data.data
  },

  async getFlowDetail(id: number): Promise<FlowDefinitionDetailDto> {
    const res = await http.get<ApiResponse<FlowDefinitionDetailDto>>(`/v1/workflow/flows/${id}`)
    return res.data.data
  },

  async updateFlowNodes(id: number, nodes: FlowNodeDto[]): Promise<FlowDefinitionDetailDto> {
    const res = await http.put<ApiResponse<FlowDefinitionDetailDto>>(`/v1/workflow/flows/${id}/nodes`, nodes)
    return res.data.data
  },

  async listMyDeputies(): Promise<WorkflowDeputyDto[]> {
    const res = await http.get<ApiResponse<WorkflowDeputyDto[]>>('/v1/workflow/deputies/me')
    return res.data.data
  },

  async listAllDeputies(): Promise<WorkflowDeputyDto[]> {
    const res = await http.get<ApiResponse<WorkflowDeputyDto[]>>('/v1/workflow/deputies')
    return res.data.data
  },

  async createDeputy(payload: WorkflowDeputyCreatePayload): Promise<WorkflowDeputyDto> {
    const res = await http.post<ApiResponse<WorkflowDeputyDto>>('/v1/workflow/deputies', payload)
    return res.data.data
  },

  async disableDeputy(id: number): Promise<void> {
    await http.put<ApiResponse<void>>(`/v1/workflow/deputies/${id}/disable`)
  },

  async saveDraft(payload: DynamicFormSubmitPayload): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>('/v1/workflow/instances/draft', payload)
    return res.data.data
  },

  async submitForm(payload: DynamicFormSubmitPayload): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>('/v1/workflow/instances/submit', payload)
    return res.data.data
  },

  async getInbox(): Promise<WorkflowInstanceDto[]> {
    const res = await http.get<ApiResponse<WorkflowInstanceDto[]>>('/v1/workflow/instances/inbox')
    return res.data.data
  },

  async getMyInstances(): Promise<WorkflowInstanceDto[]> {
    const res = await http.get<ApiResponse<WorkflowInstanceDto[]>>('/v1/workflow/instances/my')
    return res.data.data
  },

  async getAllInstances(params?: {
    status?: WorkflowInstanceStatus
    targetType?: WorkflowTargetType
  }): Promise<WorkflowInstanceDto[]> {
    const res = await http.get<ApiResponse<WorkflowInstanceDto[]>>('/v1/workflow/instances', { params })
    return res.data.data
  },

  async getInstanceDetail(id: number): Promise<WorkflowInstanceDetailDto> {
    const res = await http.get<ApiResponse<WorkflowInstanceDetailDto>>(`/v1/workflow/instances/${id}`)
    return res.data.data
  },

  async approveTask(id: number, comment?: string): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>(`/v1/workflow/instances/${id}/approve`, {
      comment
    })
    return res.data.data
  },

  async rejectTask(id: number, comment?: string): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>(`/v1/workflow/instances/${id}/reject`, {
      comment
    })
    return res.data.data
  },

  async returnToDraft(id: number, comment?: string): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>(
      `/v1/workflow/instances/${id}/return-to-draft`,
      { comment }
    )
    return res.data.data
  },

  async cancelInstance(id: number, comment?: string): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>(`/v1/workflow/instances/${id}/cancel`, {
      comment
    })
    return res.data.data
  },

  async revokeInstance(id: number, comment?: string): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>(`/v1/workflow/instances/${id}/revoke`, {
      comment
    })
    return res.data.data
  },

  async reassignTask(id: number, newApproverUserId: number, comment?: string): Promise<WorkflowInstanceDto> {
    const res = await http.post<ApiResponse<WorkflowInstanceDto>>(`/v1/workflow/instances/${id}/reassign`, {
      newApproverUserId,
      comment
    })
    return res.data.data
  }
}
