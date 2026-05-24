import apiClient from './client';
import type { ApiResponse, NotificationTemplateDto, UpdateTemplateRequest } from './types';

const notificationApi = {
  listTemplates: () =>
    apiClient.get<ApiResponse<NotificationTemplateDto[]>>('/admin/notifications'),

  getTemplate: (id: number) =>
    apiClient.get<ApiResponse<NotificationTemplateDto>>(`/admin/notifications/${id}`),

  updateTemplate: (id: number, data: UpdateTemplateRequest) =>
    apiClient.put<ApiResponse<void>>(`/admin/notifications/${id}`, data),
};

export default notificationApi;
