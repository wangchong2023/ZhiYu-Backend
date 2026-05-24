import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AdminsPage from '../../pages/admins/AdminsPage';

const mockListAdmins = vi.fn().mockResolvedValue({
  data: {
    code: 0,
    data: {
      records: [
        { userId: 1, username: 'admin1', email: 'admin1@example.com', createdAt: '2024-01-01T00:00:00Z' },
        { userId: 2, username: 'admin2', email: 'admin2@example.com', createdAt: '2024-02-01T00:00:00Z' },
      ],
      total: 2,
    },
  },
});
const mockCreateAdmin = vi.fn().mockResolvedValue({ data: { code: 0 } });
const mockResetPassword = vi.fn().mockResolvedValue({ data: { code: 0 } });

vi.mock('../../api/adminApi', () => ({
  default: {
    listAdmins: (...args: unknown[]) => mockListAdmins(...args),
    createAdmin: (...args: unknown[]) => mockCreateAdmin(...args),
    resetPassword: (...args: unknown[]) => mockResetPassword(...args),
  },
}));

describe('AdminsPage', () => {
  it('renders page with create button', async () => {
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('新建管理员')).toBeInTheDocument();
    });
  });

  it('renders refresh button', async () => {
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('renders table column headers', async () => {
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('用户名').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('邮箱').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('操作').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays admin data', async () => {
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('admin1')).toBeInTheDocument();
      expect(screen.getByText('admin2')).toBeInTheDocument();
    });
  });

  it('shows reset password button for each admin', async () => {
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      const buttons = screen.getAllByText('重置密码');
      expect(buttons.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('opens create admin modal when button is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('新建管理员')).toBeInTheDocument();
    });

    await user.click(screen.getByText('新建管理员'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('管理员用户名')).toBeInTheDocument();
    });
  });

  it('opens reset password modal when button is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('重置密码').length).toBeGreaterThanOrEqual(1);
    });

    await user.click(screen.getAllByText('重置密码')[0]);

    await waitFor(() => {
      expect(screen.getByPlaceholderText('新密码，至少8位')).toBeInTheDocument();
    });
  });

  it('submits create admin form when modal OK is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('新建管理员')).toBeInTheDocument();
    });

    await user.click(screen.getByText('新建管理员'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('管理员用户名')).toBeInTheDocument();
    });

    await user.type(screen.getByPlaceholderText('管理员用户名'), 'newadmin');
    await user.type(screen.getByPlaceholderText('admin@example.com'), 'new@test.com');
    await user.type(screen.getByPlaceholderText('至少8位'), 'password123');

    const okBtn = document.querySelector('.ant-modal-footer .ant-btn-primary') as HTMLElement;
    await user.click(okBtn);

    await waitFor(() => {
      expect(mockCreateAdmin).toHaveBeenCalled();
    });
  });

  it('submits reset password form when modal OK is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('重置密码').length).toBeGreaterThanOrEqual(1);
    });

    await user.click(screen.getAllByText('重置密码')[0]);

    await waitFor(() => {
      expect(screen.getByPlaceholderText('新密码，至少8位')).toBeInTheDocument();
    });

    await user.type(screen.getByPlaceholderText('新密码，至少8位'), 'newpass123');

    const okBtn = document.querySelector('.ant-modal-footer .ant-btn-primary') as HTMLElement;
    await user.click(okBtn);

    await waitFor(() => {
      expect(mockResetPassword).toHaveBeenCalled();
    });
  });

  it('shows error alert when fetch fails', async () => {
    mockListAdmins.mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><AdminsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('加载管理员列表失败')).toBeInTheDocument();
    });
  });
});
