import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockDelete = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet, post: mockPost, delete: mockDelete },
}));

describe('adminApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('listAdmins calls GET /admin/admins', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.listAdmins();
    expect(mockGet).toHaveBeenCalledWith('/admin/admins', { params: undefined });
  });

  it('listAdmins passes query params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.listAdmins({ status: 'ACTIVE' });
    expect(mockGet).toHaveBeenCalledWith('/admin/admins', { params: { status: 'ACTIVE' } });
  });

  it('createAdmin calls POST /admin/admins', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: { userId: 1 } } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.createAdmin({ username: 'admin2', password: 'pass123', email: 'admin2@test.com' });
    expect(mockPost).toHaveBeenCalledWith('/admin/admins', {
      username: 'admin2',
      password: 'pass123',
      email: 'admin2@test.com',
    });
  });

  it('resetPassword calls POST /admin/admins/:userId/reset-password', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.resetPassword(42, { newPassword: 'newSecret' });
    expect(mockPost).toHaveBeenCalledWith('/admin/admins/42/reset-password', { newPassword: 'newSecret' });
  });

  it('listRoles calls GET /admin/roles', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.listRoles();
    expect(mockGet).toHaveBeenCalledWith('/admin/roles');
  });

  it('assignRole calls POST /admin/admins/:userId/roles', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.assignRole(1, { roleId: 3 });
    expect(mockPost).toHaveBeenCalledWith('/admin/admins/1/roles', { roleId: 3 });
  });

  it('removeRole calls DELETE /admin/admins/:userId/roles/:roleId', async () => {
    mockDelete.mockResolvedValue({ data: { code: 0 } });
    const { default: adminApi } = await import('../../api/adminApi');
    await adminApi.removeRole(1, 5);
    expect(mockDelete).toHaveBeenCalledWith('/admin/admins/1/roles/5');
  });
});
