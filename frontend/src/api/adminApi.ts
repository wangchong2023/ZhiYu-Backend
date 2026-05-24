import apiClient from './client';
import type { ApiResponse, PaginatedData, RoleDto, CreateAdminRequest, ResetPasswordRequest, VersionDto } from './types';

interface AdminDto {
  userId: number;
  username: string;
  email: string;
  mobile?: string;
  createdAt?: string;
  status?: string;
  lastLoginAt?: string;
  lastLoginIp?: string;
}

const adminApi = {
  listAdmins: (params?: Record<string, unknown>) =>
    apiClient.get<ApiResponse<PaginatedData<AdminDto>>>('/admin/admins', { params }),

  createAdmin: (data: CreateAdminRequest) =>
    apiClient.post<ApiResponse<AdminDto>>('/admin/admins', data),

  resetPassword: (userId: number, data: ResetPasswordRequest) =>
    apiClient.post<ApiResponse<void>>(`/admin/admins/${userId}/reset-password`, data),

  listRoles: () =>
    apiClient.get<ApiResponse<RoleDto[]>>('/admin/roles'),

  assignRole: (userId: number, data: { roleId: number }) =>
    apiClient.post<ApiResponse<void>>(`/admin/admins/${userId}/roles`, data),

  removeRole: (userId: number, roleId: number) =>
    apiClient.delete<ApiResponse<void>>(`/admin/admins/${userId}/roles/${roleId}`),

  getVersion: () =>
    apiClient.get<ApiResponse<VersionDto>>('/admin/version'),
};

export default adminApi;
