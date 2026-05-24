import apiClient from './client';
import type { ApiResponse, PaginatedData, LoginLogDto, IdentityChangeDto, AdminOperationDto } from './types';

export interface LoginLogParams {
  page: number;
  size: number;
  type?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
}

export interface IdentityChangeParams {
  page: number;
  size: number;
  userId?: string;
  start?: string;
  end?: string;
}

export interface AdminOperationParams {
  page: number;
  size: number;
  username?: string;
  action?: string;
  startTime?: string;
  endTime?: string;
}

const auditApi = {
  loginLogs: (params: LoginLogParams) =>
    apiClient.get<ApiResponse<PaginatedData<LoginLogDto>>>('/admin/logs/login', { params }),

  identityChanges: (params: IdentityChangeParams) =>
    apiClient.get<ApiResponse<PaginatedData<IdentityChangeDto>>>('/admin/audit/identity-changes', { params }),

  adminOperations: (params: AdminOperationParams) =>
    apiClient.get<ApiResponse<PaginatedData<AdminOperationDto>>>('/admin/audit/admin-operations', { params }),
};

export default auditApi;
