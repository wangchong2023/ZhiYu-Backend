import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AuditLogPage from '../../pages/audit/AuditLogPage';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/admin/logs/login') {
        return Promise.resolve({
          data: { code: 0, data: { records: [], total: 0 } },
        });
      }
      if (url === '/admin/audit/identity-changes') {
        return Promise.resolve({
          data: { code: 0, data: { records: [], total: 0 } },
        });
      }
      return Promise.resolve({
        data: { code: 0, data: { records: [], total: 0 } },
      });
    }),
  },
}));

describe('AuditLogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login method filter', async () => {
    render(
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('登录方式')).toBeInTheDocument();
    });
  });

  it('renders result filter', async () => {
    render(
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      const resultElements = screen.getAllByText('结果');
      expect(resultElements.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('has export CSV button', async () => {
    render(
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('导出 CSV')).toBeInTheDocument();
    });
  });

  it('renders table with column headers', async () => {
    render(
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('用户').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('方式').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('IP').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders tabs for login log and identity changes', async () => {
    render(
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('登录日志')).toBeInTheDocument();
      expect(screen.getByText('身份变更')).toBeInTheDocument();
    });
  });

  it('switching to identity changes tab shows identity table columns', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('身份变更')).toBeInTheDocument();
    });

    await user.click(screen.getByText('身份变更'));

    await waitFor(() => {
      expect(screen.getAllByText('用户ID').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('操作').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('认证类型').length).toBeGreaterThanOrEqual(1);
    });
  });
});
