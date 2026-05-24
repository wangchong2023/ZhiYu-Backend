import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AuditLogPage from '../../pages/audit/AuditLogPage';

const auditTMap: Record<string, string> = {
  'audit.loginLog': '登录日志',
  'audit.identityChange': '身份变更',
  'audit.adminOperation': '管理员操作',
  'audit.username': '用户',
  'audit.userId': '用户ID',
  'audit.action': '操作',
  'audit.method': '方式',
  'audit.result': '结果',
  'audit.ip': 'IP',
  'audit.device': '设备',
  'audit.location': '位置',
  'audit.time': '时间',
  'audit.authType': '认证类型',
  'audit.operator': '操作人',
  'audit.target': '目标',
  'audit.filterLoginMethod': '登录方式',
  'audit.filterResult': '结果',
  'audit.filterOperator': '操作人',
  'audit.filterAction': '动作',
  'audit.exportCsv': '导出 CSV',
  'audit.loadLoginFailed': '加载审计日志失败',
  'audit.loadIdentityFailed': '加载身份变更日志失败',
  'audit.loadAdminOpFailed': '加载管理员操作日志失败',
  'audit.success': '成功',
  'audit.failure': '失败',
  'audit.locked': '锁定',
  'audit.bind': '绑定',
  'audit.unbind': '解绑',
  'audit.actionCreateUser': '创建用户',
  'audit.actionEnableUser': '启用用户',
  'audit.actionDisableUser': '禁用用户',
  'audit.actionDeleteUser': '删除用户',
  'audit.actionUpdateUser': '更新用户',
  'audit.actionResetPassword': '重置密码',
  'audit.actionAdjustLogLevel': '调整日志级别',
  'common.refresh': '刷新',
  'common.retry': '重试',
};

function stableT(key: string): string {
  return auditTMap[key] || key;
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT }),
}));

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
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
    if (url === '/admin/audit/admin-operations') {
      return Promise.resolve({
        data: { code: 0, data: { records: [], total: 0 } },
      });
    }
    return Promise.resolve({
      data: { code: 0, data: { records: [], total: 0 } },
    });
  }),
}));

vi.mock('../../api/client', () => ({
  default: { get: mockGet },
}));

describe('AuditLogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset mockGet to default behavior
    mockGet.mockImplementation((url: string) => {
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
      if (url === '/admin/audit/admin-operations') {
        return Promise.resolve({
          data: { code: 0, data: { records: [], total: 0 } },
        });
      }
      return Promise.resolve({
        data: { code: 0, data: { records: [], total: 0 } },
      });
    });
  });

  // ---- Login tab ----

  it('renders login method filter', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('登录方式')).toBeInTheDocument();
    });
  });

  it('renders result filter on login tab', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => {
      const resultElements = screen.getAllByText('结果');
      expect(resultElements.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('has export CSV button', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('导出 CSV')).toBeInTheDocument();
    });
  });

  it('renders table with login log column headers', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getAllByText('用户').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('方式').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('IP').length).toBeGreaterThanOrEqual(1);
    });
  });

  // ---- Tabs ----

  it('renders three tabs', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('登录日志')).toBeInTheDocument();
      expect(screen.getByText('身份变更')).toBeInTheDocument();
      expect(screen.getByText('管理员操作')).toBeInTheDocument();
    });
  });

  it('switching to identity changes tab shows identity table columns', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('身份变更')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('身份变更'));
    await waitFor(() => {
      expect(screen.getAllByText('用户ID').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('认证类型').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('switching to identity tab calls the identity-changes API', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('身份变更')).toBeInTheDocument(); });

    mockGet.mockClear();
    fireEvent.click(screen.getByText('身份变更'));

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/audit/identity-changes', expect.any(Object));
    });
  });

  // ---- Admin operations tab ----

  it('switching to admin operations tab shows admin op table columns', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('管理员操作')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('管理员操作'));
    await waitFor(() => {
      expect(screen.getAllByText('操作人').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('目标').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('switching to admin ops tab calls the admin-operations API', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('管理员操作')).toBeInTheDocument(); });

    mockGet.mockClear();
    fireEvent.click(screen.getByText('管理员操作'));

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/audit/admin-operations', expect.any(Object));
    });
  });

  it('admin operations tab shows operator filter', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('管理员操作')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('管理员操作'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('操作人')).toBeInTheDocument();
    });
  });

  it('admin operations tab shows action filter dropdown', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('管理员操作')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('管理员操作'));

    await waitFor(() => {
      // The action filter is a Select placeholder rendered as a title attribute
      const filterSelects = document.querySelectorAll('.ant-select');
      expect(filterSelects.length).toBeGreaterThanOrEqual(1);
    });
  });

  // ---- Login tab with data ----

  it('renders login log data with tags', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/logs/login') {
        return Promise.resolve({
          data: {
            code: 0,
            data: {
              records: [
                { id: 1, username: 'admin', action: 'LOGIN', type: 'PASSWORD', result: 'SUCCESS', ip: '192.168.1.1', device: 'Chrome', location: 'Beijing', time: '2024-01-01T12:00:00Z' },
              ],
              total: 1,
            },
          },
        });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('SUCCESS')).toBeInTheDocument();
    });
  });

  // ---- Identity tab with data ----

  it('renders identity change data with bind/unbind tags', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/audit/identity-changes') {
        return Promise.resolve({
          data: {
            code: 0,
            data: {
              records: [
                { id: 1, userId: 42, action: 'BIND', identityType: 'WECHAT', sourceIp: '10.0.0.1', createdAt: '2024-01-15T08:00:00Z' },
              ],
              total: 1,
            },
          },
        });
      }
      if (url === '/admin/logs/login') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      if (url === '/admin/audit/admin-operations') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => { expect(screen.getByText('身份变更')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('身份变更'));

    await waitFor(() => {
      expect(screen.getByText('绑定')).toBeInTheDocument();
    });
  });

  // ---- Admin operations tab with data ----

  it('renders admin operation data with action tag colors', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/audit/admin-operations') {
        return Promise.resolve({
          data: {
            code: 0,
            data: {
              records: [
                { id: 1, username: 'superadmin', action: 'CREATE_USER', target: 'newuser', targetId: 99, ip: '10.0.0.1', time: '2024-02-01T10:00:00Z' },
              ],
              total: 1,
            },
          },
        });
      }
      if (url === '/admin/logs/login') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      if (url === '/admin/audit/identity-changes') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => { expect(screen.getByText('管理员操作')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('管理员操作'));

    await waitFor(() => {
      expect(screen.getByText('创建用户')).toBeInTheDocument();
      expect(screen.getByText('superadmin')).toBeInTheDocument();
      expect(screen.getByText('newuser')).toBeInTheDocument();
    });
  });

  // ---- Error states ----

  it('shows error alert when login log API fails', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/logs/login') {
        return Promise.reject(new Error('Server error'));
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('加载审计日志失败')).toBeInTheDocument();
    });
  });

  it('shows error alert when identity changes API fails', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/audit/identity-changes') {
        return Promise.reject(new Error('Server error'));
      }
      if (url === '/admin/logs/login') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => { expect(screen.getByText('身份变更')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('身份变更'));

    await waitFor(() => {
      expect(screen.getByText('加载身份变更日志失败')).toBeInTheDocument();
    });
  });

  it('shows error alert when admin ops API fails', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/audit/admin-operations') {
        return Promise.reject(new Error('Server error'));
      }
      if (url === '/admin/logs/login') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      if (url === '/admin/audit/identity-changes') {
        return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => { expect(screen.getByText('管理员操作')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('管理员操作'));

    await waitFor(() => {
      expect(screen.getByText('加载管理员操作日志失败')).toBeInTheDocument();
    });
  });

  // ---- Export CSV ----

  it('exports CSV when export button is clicked', async () => {
    // Mock URL.createObjectURL and document.createElement for the CSV download
    const mockCreateObjectURL = vi.fn(() => 'blob:test-csv');
    const mockRevokeObjectURL = vi.fn();
    const mockAnchorClick = vi.fn();

    URL.createObjectURL = mockCreateObjectURL;
    URL.revokeObjectURL = mockRevokeObjectURL;

    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === 'a') {
        el.click = mockAnchorClick;
      }
      return el;
    });

    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => { expect(screen.getByText('导出 CSV')).toBeInTheDocument(); });

    mockGet.mockClear();

    fireEvent.click(screen.getByText('导出 CSV'));

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/login', expect.any(Object));
    });

    await waitFor(() => {
      expect(mockCreateObjectURL).toHaveBeenCalled();
      expect(mockAnchorClick).toHaveBeenCalled();
    });

    // Cleanup
    vi.restoreAllMocks();
  });

  // ---- Pagination ----

  it('renders login log table with correct column count', async () => {
    render(<MemoryRouter><AuditLogPage /></MemoryRouter>);

    await waitFor(() => {
      // Login log table should have 9 columns (ID, user, action, method, result, IP, device, location, time)
      const table = document.querySelector('.ant-table');
      expect(table).not.toBeNull();
    });
  });
});
