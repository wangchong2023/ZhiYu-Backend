import apiClient from './client';
import type { ApiResponse, PaginatedData, UserDto, IdentityDto } from './types';

export interface UserDetail extends UserDto {
  recentLogs?: Array<{
    username: string;
    action: string;
    result: string;
    ip: string;
    device: string;
    time: string;
  }>;
  identities?: IdentityDto[];
}

export interface UserListParams {
  page: number;
  size: number;
  keyword?: string;
  status?: string;
}

const userApi = {
  list: (params: UserListParams) =>
    apiClient.get<ApiResponse<PaginatedData<UserDto>>>('/admin/users', { params }),

  detail: (userId: number) =>
    apiClient.get<ApiResponse<UserDetail>>(`/admin/users/${userId}`),

  toggleStatus: (userId: number, action: 'enable' | 'disable') =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/${action}`),

  bindWechat: (code: string, state: string) =>
    apiClient.post<ApiResponse<IdentityDto>>('/user/bind-wechat', { code, state }),

  bindApple: (code: string, idToken: string) =>
    apiClient.post<ApiResponse<IdentityDto>>('/user/bind-apple', { code, idToken }),

  bindGoogle: (code: string) =>
    apiClient.post<ApiResponse<IdentityDto>>('/user/bind-google', { code }),

  unbind: (identityId: number) =>
    apiClient.delete<ApiResponse<void>>(`/user/identities/${identityId}`),
};

export default userApi;
