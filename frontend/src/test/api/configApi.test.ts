import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet },
}));

describe('configApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('getConfigHistory calls GET /admin/config/history', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: configApi } = await import('../../api/configApi');
    await configApi.getConfigHistory();
    expect(mockGet).toHaveBeenCalledWith('/admin/config/history', { params: undefined });
  });

  it('getConfigHistory passes query params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: configApi } = await import('../../api/configApi');
    await configApi.getConfigHistory({ groupId: 'DEFAULT_GROUP' });
    expect(mockGet).toHaveBeenCalledWith('/admin/config/history', { params: { groupId: 'DEFAULT_GROUP' } });
  });
});
