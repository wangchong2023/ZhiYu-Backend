import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet, post: mockPost },
}));

describe('userApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('list calls GET /admin/users', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { records: [], total: 0 } } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.list({ page: 1, size: 20 });
    expect(mockGet).toHaveBeenCalledWith('/admin/users', { params: { page: 1, size: 20 } });
  });

  it('detail calls GET /admin/users/:id', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.detail(42);
    expect(mockGet).toHaveBeenCalledWith('/admin/users/42');
  });

  it('toggleStatus calls POST for enable', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.toggleStatus(42, 'enable');
    expect(mockPost).toHaveBeenCalledWith('/admin/users/42/enable');
  });

  it('toggleStatus calls POST for disable', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.toggleStatus(99, 'disable');
    expect(mockPost).toHaveBeenCalledWith('/admin/users/99/disable');
  });

  it('unbind calls POST /user/unbind/:id', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.unbind(7);
    expect(mockPost).toHaveBeenCalledWith('/user/unbind/7');
  });

  it('bindWechat POSTs to /user/bind-wechat', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.bindWechat('wx-code', 'wx-state');
    expect(mockPost).toHaveBeenCalledWith('/user/bind-wechat', { code: 'wx-code', state: 'wx-state' });
  });

  it('bindApple POSTs to /user/bind-apple', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.bindApple('apple-code', 'apple-id-token');
    expect(mockPost).toHaveBeenCalledWith('/user/bind-apple', { code: 'apple-code', idToken: 'apple-id-token' });
  });

  it('bindGoogle POSTs to /user/bind-google', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: userApi } = await import('../../api/userApi');
    await userApi.bindGoogle('google-code');
    expect(mockPost).toHaveBeenCalledWith('/user/bind-google', { code: 'google-code' });
  });
});
