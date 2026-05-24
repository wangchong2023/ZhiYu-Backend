import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet, post: mockPost },
}));

describe('subscriptionApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('listSubscriptions calls GET /admin/subscriptions', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.listSubscriptions();
    expect(mockGet).toHaveBeenCalledWith('/admin/subscriptions', { params: undefined });
  });

  it('listSubscriptions passes query params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.listSubscriptions({ status: 'ACTIVE' });
    expect(mockGet).toHaveBeenCalledWith('/admin/subscriptions', { params: { status: 'ACTIVE' } });
  });

  it('listPayments calls GET /admin/payments', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.listPayments();
    expect(mockGet).toHaveBeenCalledWith('/admin/payments', { params: undefined });
  });

  it('listPayments passes query params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.listPayments({ channel: 'WECHAT', status: 'PAID' });
    expect(mockGet).toHaveBeenCalledWith('/admin/payments', { params: { channel: 'WECHAT', status: 'PAID' } });
  });

  it('listRefunds calls GET /admin/refunds', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.listRefunds();
    expect(mockGet).toHaveBeenCalledWith('/admin/refunds', { params: undefined });
  });

  it('listRefunds passes query params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.listRefunds({ status: 'PENDING_REVIEW' });
    expect(mockGet).toHaveBeenCalledWith('/admin/refunds', { params: { status: 'PENDING_REVIEW' } });
  });

  it('approveRefund calls POST /admin/refunds/:id/approve', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.approveRefund(10, { decision: 'APPROVED', note: 'Valid reason' });
    expect(mockPost).toHaveBeenCalledWith('/admin/refunds/10/approve', {
      decision: 'APPROVED',
      note: 'Valid reason',
    });
  });

  it('rejectRefund calls POST /admin/refunds/:id/reject', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: subscriptionApi } = await import('../../api/subscriptionApi');
    await subscriptionApi.rejectRefund(7, { decision: 'REJECTED', note: 'Insufficient evidence' });
    expect(mockPost).toHaveBeenCalledWith('/admin/refunds/7/reject', {
      decision: 'REJECTED',
      note: 'Insufficient evidence',
    });
  });
});
