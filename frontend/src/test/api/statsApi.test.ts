import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet },
}));

describe('statsApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('overview calls GET /admin/stats/overview', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: statsApi } = await import('../../api/statsApi');
    await statsApi.overview();
    expect(mockGet).toHaveBeenCalledWith('/admin/stats/overview');
  });

  it('registerTrend calls GET with days param', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: statsApi } = await import('../../api/statsApi');
    await statsApi.registerTrend(7);
    expect(mockGet).toHaveBeenCalledWith('/admin/stats/register-trend', { params: { days: 7 } });
  });

  it('trend calls GET with default days', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: statsApi } = await import('../../api/statsApi');
    await statsApi.trend();
    expect(mockGet).toHaveBeenCalledWith('/admin/stats/trend', { params: { days: undefined } });
  });

  it('loginMethodDist calls GET with days', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: statsApi } = await import('../../api/statsApi');
    await statsApi.loginMethodDist(30);
    expect(mockGet).toHaveBeenCalledWith('/admin/stats/login-method-dist', { params: { days: 30 } });
  });
});
