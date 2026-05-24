import apiClient from './client';
import type { ApiResponse, PaginatedData, ConfigHistoryDto } from './types';

const configApi = {
  getConfigHistory: (params?: Record<string, unknown>) =>
    apiClient.get<ApiResponse<PaginatedData<ConfigHistoryDto>>>('/admin/config/history', { params }),
};

export default configApi;
