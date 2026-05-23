import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import UserListPage from '../../pages/users/UserListPage';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url.startsWith('/admin/users/') && !url.includes('?') && url.split('/').length >= 4) {
        return Promise.resolve({
          data: {
            code: 0,
            data: {
              userId: 1,
              username: 'testuser',
              email: 'test@example.com',
              mobile: '13800138000',
              status: '正常',
              scope: 'ADMIN',
              createdAt: '2024-01-01T00:00:00Z',
              recentLogs: [],
              identities: [
                { identityId: 1, provider: 'PASSWORD', openid: '', nickname: '', enabled: 1, createdAt: '2024-01-01T00:00:00Z' },
                { identityId: 2, provider: 'WECHAT', openid: 'wx_openid_123', nickname: '微信用户', enabled: 1, createdAt: '2024-02-01T00:00:00Z' },
                { identityId: 3, provider: 'GOOGLE', openid: 'google_sub_456', nickname: 'Google User', enabled: 1, createdAt: '2024-03-01T00:00:00Z' },
              ],
            },
          },
        });
      }
      if (url === '/admin/users') {
        return Promise.resolve({
          data: { code: 0, data: {
            records: [
              { userId: 1, username: 'testuser', email: 'test@example.com', mobile: '13800138000', status: '正常', scope: 'ADMIN', createdAt: '2024-01-01T00:00:00Z' },
            ],
            total: 1,
          } },
        });
      }
      return Promise.resolve({
        data: { code: 0, data: { records: [], total: 0 } },
      });
    }),
    post: vi.fn().mockResolvedValue({ data: { code: 0 } }),
  },
}));

describe('UserListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders user list page', async () => {
    render(
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByPlaceholderText('搜索用户名/邮箱')).toBeInTheDocument();
    });
  });

  it('has status filter dropdown', async () => {
    render(
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('状态筛选')).toBeInTheDocument();
    });
  });

  it('has column headers', async () => {
    render(
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('用户名').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('邮箱').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('状态').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('shows identity section in user detail drawer', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByPlaceholderText('搜索用户名/邮箱')).toBeInTheDocument();
    });

    const detailBtn = screen.getByText('详情');
    await user.click(detailBtn);

    await waitFor(() => {
      expect(screen.getByText('认证身份')).toBeInTheDocument();
    });
  });

  it('displays provider labels correctly in identity table', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByPlaceholderText('搜索用户名/邮箱')).toBeInTheDocument();
    });

    const detailBtn = screen.getByText('详情');
    await user.click(detailBtn);

    await waitFor(() => {
      expect(screen.getByText('认证身份')).toBeInTheDocument();
      expect(screen.getByText('微信')).toBeInTheDocument();
      expect(screen.getByText('Google')).toBeInTheDocument();
      expect(screen.getByText('密码')).toBeInTheDocument();
    });
  });

  it('shows unbind button for non-password identities', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByPlaceholderText('搜索用户名/邮箱')).toBeInTheDocument();
    });

    const detailBtn = screen.getByText('详情');
    await user.click(detailBtn);

    await waitFor(() => {
      const unbindButtons = screen.getAllByText('解绑');
      expect(unbindButtons.length).toBeGreaterThanOrEqual(1);
    });
  });
});
