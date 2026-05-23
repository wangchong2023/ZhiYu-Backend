import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MonitorLogsPage from '../../pages/monitor/MonitorLogsPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() =>
    Promise.resolve({ data: { code: 0, data: { records: [], total: 0, page: 1, size: 20, pages: 0 } } })
  ),
}));
vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

describe('MonitorLogsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders 4 tabs', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('应用日志')).toBeInTheDocument();
      expect(screen.getByText('安全日志')).toBeInTheDocument();
      expect(screen.getByText('访问日志')).toBeInTheDocument();
      expect(screen.getByText('慢查询')).toBeInTheDocument();
    });
  });

  it('shows no data state for access log tab', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('访问日志'));
    await user.click(screen.getByText('访问日志'));
    await waitFor(() => {
      expect(screen.getByText(/暂无数据/)).toBeInTheDocument();
    });
  });
});
