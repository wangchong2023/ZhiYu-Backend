import apiClient from './client';
import type { ApiResponse, PaginatedData, SubscriptionDto, PaymentDto, RefundDto, RefundReviewRequest } from './types';

const subscriptionApi = {
  listSubscriptions: (params?: Record<string, unknown>) =>
    apiClient.get<ApiResponse<PaginatedData<SubscriptionDto>>>('/admin/subscriptions', { params }),

  listPayments: (params?: Record<string, unknown>) =>
    apiClient.get<ApiResponse<PaginatedData<PaymentDto>>>('/admin/payments', { params }),

  listRefunds: (params?: Record<string, unknown>) =>
    apiClient.get<ApiResponse<PaginatedData<RefundDto>>>('/admin/refunds', { params }),

  approveRefund: (id: number, data: RefundReviewRequest) =>
    apiClient.post<ApiResponse<void>>(`/admin/refunds/${id}/approve`, data),

  rejectRefund: (id: number, data: RefundReviewRequest) =>
    apiClient.post<ApiResponse<void>>(`/admin/refunds/${id}/reject`, data),
};

export default subscriptionApi;
