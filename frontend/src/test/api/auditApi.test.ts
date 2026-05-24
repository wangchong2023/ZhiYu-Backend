import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet },
}));

describe('auditApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('loginLogs calls GET /admin/logs/login with params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: auditApi } = await import('../../api/auditApi');
    await auditApi.loginLogs({ page: 1, size: 20, type: 'PASSWORD' });
    expect(mockGet).toHaveBeenCalledWith('/admin/logs/login', {
      params: { page: 1, size: 20, type: 'PASSWORD' },
    });
  });

  it('identityChanges calls GET /admin/audit/identity-changes', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: auditApi } = await import('../../api/auditApi');
    await auditApi.identityChanges({ page: 1, size: 20, userId: '1001' });
    expect(mockGet).toHaveBeenCalledWith('/admin/audit/identity-changes', {
      params: { page: 1, size: 20, userId: '1001' },
    });
  });

  it('adminOperations calls GET with all filters', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: auditApi } = await import('../../api/auditApi');
    await auditApi.adminOperations({
      page: 1, size: 20, username: 'admin', action: 'CREATE_USER',
      startTime: '2024-01-01', endTime: '2024-12-31',
    });
    expect(mockGet).toHaveBeenCalledWith('/admin/audit/admin-operations', {
      params: {
        page: 1, size: 20, username: 'admin',
        action: 'CREATE_USER', startTime: '2024-01-01', endTime: '2024-12-31',
      },
    });
  });

  it('loginLogs works without optional filters', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: auditApi } = await import('../../api/auditApi');
    await auditApi.loginLogs({ page: 1, size: 20 });
    expect(mockGet).toHaveBeenCalledWith('/admin/logs/login', {
      params: { page: 1, size: 20 },
    });
  });
});
