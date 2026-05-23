import apiClient from './client';
import type { ApiResponse, StatsOverview, TrendPoint, DistributionItem, TrendItem } from './types';

const statsApi = {
  overview: () =>
    apiClient.get<ApiResponse<StatsOverview>>('/admin/stats/overview'),

  registerTrend: (days?: number) =>
    apiClient.get<ApiResponse<TrendPoint[]>>('/admin/stats/register-trend', { params: { days } }),

  dauTrend: (days?: number) =>
    apiClient.get<ApiResponse<TrendPoint[]>>('/admin/stats/dau-trend', { params: { days } }),

  loginMethodDist: (days?: number) =>
    apiClient.get<ApiResponse<DistributionItem[]>>('/admin/stats/login-method-dist', { params: { days } }),

  trend: (days?: number) =>
    apiClient.get<ApiResponse<TrendItem[]>>('/admin/stats/trend', { params: { days } }),
};

export default statsApi;
