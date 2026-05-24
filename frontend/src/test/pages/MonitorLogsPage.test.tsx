import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { mockT } from '../utils/i18n';
import MonitorLogsPage from '../../pages/monitor/MonitorLogsPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() =>
    Promise.resolve({ data: { code: 0, data: { records: [], total: 0, page: 1, size: 20, pages: 0 } } })
  ),
}));
vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

const sampleAccessLogs = [
  { id: 1, time: '2024-01-01T12:00:00Z', ip: '192.168.1.1', method: 'GET', path: '/api/users', statusCode: 200, responseTimeMs: 45, userAgent: 'Mozilla/5.0' },
  { id: 2, time: '2024-01-01T12:01:00Z', ip: '10.0.0.1', method: 'POST', path: '/api/login', statusCode: 401, responseTimeMs: 120, userAgent: 'Chrome/120' },
];

const sampleSlowQueries = [
  { id: 1, time: '2024-01-01T12:00:00Z', sqlSummary: 'SELECT * FROM users WHERE id = ?', durationMs: 250, source: 'UserService.java:42' },
  { id: 2, time: '2024-01-01T12:01:00Z', sqlSummary: 'SELECT COUNT(*) FROM audit_logs', durationMs: 1500, source: 'AuditService.java:88' },
];

function switchToTab(tabLabel: string) {
  fireEvent.click(screen.getByText(tabLabel));
}

/**
 * Get the content area of the active tabpane for assertions
 */
function getActiveTabContent(): HTMLElement {
  const pane = document.querySelector('.ant-tabs-tabpane-active');
  if (!pane) throw new Error('No active tabpane found');
  return pane as HTMLElement;
}

describe('MonitorLogsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockImplementation(() =>
      Promise.resolve({ data: { code: 0, data: { records: [], total: 0, page: 1, size: 20, pages: 0 } } })
    );
  });

  // ---- Tab rendering ----

  it('renders 4 tabs', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('应用日志')).toBeInTheDocument();
      expect(screen.getByText('安全日志')).toBeInTheDocument();
      expect(screen.getByText('访问日志')).toBeInTheDocument();
      expect(screen.getByText('慢查询')).toBeInTheDocument();
    });
  });

  it('renders the page title', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('日志检索')).toBeInTheDocument();
    });
  });

  // ---- App Log tab (default) ----

  it('renders search button on app log tab', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('搜索')).toBeInTheDocument();
    });
  });

  it('renders refresh button on app log tab', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('app log tab fetches app logs on mount', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/app', expect.any(Object));
    });
  });

  it('app log tab renders a table', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      const tables = document.querySelectorAll('.ant-table');
      expect(tables.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('app log tab shows keyword search input', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      // The keyword search input should be in the DOM
      const activePane = getActiveTabContent();
      const inputs = activePane.querySelectorAll('input');
      expect(inputs.length).toBeGreaterThanOrEqual(1);
    });
  });

  // ---- Security Log tab ----

  it('shows security log API call after clicking security tab', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('安全日志'));

    mockGet.mockClear();
    switchToTab('安全日志');

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/security', expect.any(Object));
    });
  });

  it('security log tab renders a table', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('安全日志'));
    switchToTab('安全日志');

    await waitFor(() => {
      const tables = document.querySelectorAll('.ant-table');
      expect(tables.length).toBeGreaterThanOrEqual(1);
    });
  });

  // ---- Access Log tab ----

  it('shows access log API call after clicking access tab', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('访问日志'));

    mockGet.mockClear();
    switchToTab('访问日志');

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/access', expect.any(Object));
    });
  });

  it('access log tab shows User-Agent column header', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('访问日志'));
    switchToTab('访问日志');

    await waitFor(() => {
      expect(screen.getAllByText('User-Agent').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('access log tab has filter inputs for IP, method, and status code', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('访问日志'));
    switchToTab('访问日志');

    await waitFor(() => {
      // There should be Select components (method + statusCode filters) and an Input (IP filter)
      const activePane = getActiveTabContent();
      const selects = activePane.querySelectorAll('.ant-select');
      const inputs = activePane.querySelectorAll('input.ant-input');
      // At least 2 selects (method, statusCode) + 1 input (IP)
      expect(selects.length).toBeGreaterThanOrEqual(2);
      expect(inputs.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('access log tab renders access log data with method values', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/logs/access') {
        return Promise.resolve({ data: { code: 0, data: { records: sampleAccessLogs, total: 2 } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('访问日志'));
    switchToTab('访问日志');

    await waitFor(() => {
      expect(screen.getByText('GET')).toBeInTheDocument();
      expect(screen.getByText('POST')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('200')).toBeInTheDocument();
      expect(screen.getByText('401')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('45 ms')).toBeInTheDocument();
      expect(screen.getByText('120 ms')).toBeInTheDocument();
    });
  });

  // ---- Slow Query tab ----

  it('shows slow query API call after clicking slow query tab', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('慢查询'));

    mockGet.mockClear();
    switchToTab('慢查询');

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/slow-query', expect.any(Object));
    });
  });

  it('slow query tab shows SQL Summary column header', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('慢查询'));
    switchToTab('慢查询');

    await waitFor(() => {
      expect(screen.getAllByText('SQL 摘要').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('slow query tab shows min duration filter label', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('慢查询'));
    switchToTab('慢查询');

    await waitFor(() => {
      expect(screen.getByText('最小耗时 (ms)')).toBeInTheDocument();
    });
  });

  it('slow query tab renders query data with duration values', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/logs/slow-query') {
        return Promise.resolve({ data: { code: 0, data: { records: sampleSlowQueries, total: 2 } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });

    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('慢查询'));
    switchToTab('慢查询');

    await waitFor(() => {
      expect(screen.getByText('250 ms')).toBeInTheDocument();
      expect(screen.getByText('1500 ms')).toBeInTheDocument();
    });
  });

  // ---- All tabs accessed sequentially ----

  it('can switch between all 4 tabs', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('应用日志')).toBeInTheDocument());

    // Security log
    switchToTab('安全日志');
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/security', expect.any(Object));
    });

    // Access log
    switchToTab('访问日志');
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/access', expect.any(Object));
    });

    // Slow query
    switchToTab('慢查询');
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/logs/slow-query', expect.any(Object));
    });
  });
});
