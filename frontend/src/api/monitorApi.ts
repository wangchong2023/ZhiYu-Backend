import apiClient from './client';
import type { ApiResponse, HealthDto, MetricsDto, AlertDto, LoggerDto, LogLevelHistoryDto, PodInfo } from './types';

const monitorApi = {
  health: () =>
    apiClient.get<ApiResponse<HealthDto[]>>('/admin/monitor/health'),

  pods: () =>
    apiClient.get<ApiResponse<PodInfo[]>>('/admin/monitor/pods'),

  metrics: () =>
    apiClient.get<ApiResponse<MetricsDto>>('/admin/monitor/metrics'),

  alerts: (params?: { status?: string; severity?: string; startTime?: string; endTime?: string }) =>
    apiClient.get<ApiResponse<AlertDto[]>>('/admin/monitor/alerts', { params }),

  recentAlerts: () =>
    apiClient.get<ApiResponse<AlertDto[]>>('/admin/monitor/alerts/recent'),

  loggers: () =>
    apiClient.get<ApiResponse<LoggerDto[]>>('/admin/monitor/loggers'),

  setLoggerLevel: (name: string, configuredLevel: string) =>
    apiClient.post(`/admin/monitor/loggers/${name}`, { configuredLevel }),

  loggerHistory: () =>
    apiClient.get<ApiResponse<LogLevelHistoryDto[]>>('/admin/monitor/loggers/history'),

};

export default monitorApi;
