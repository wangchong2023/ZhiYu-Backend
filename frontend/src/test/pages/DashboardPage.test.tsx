import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { mockT } from '../utils/i18n';
import DashboardPage from '../../pages/dashboard/DashboardPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
    if (url === '/admin/stats/overview') {
      return Promise.resolve({ data: { code: 0, data: {
        newUsers: 128, activeSubs: 56, revenue: 2480, onlineUsers: 47,
        todayRegistrations: 5, todayLogins: 10, dau: 20,
        loginSuccessRate: 95.5, registrationChange: 10, loginChange: -5,
      } } });
    }
    if (url === '/admin/stats/trend') {
      return Promise.resolve({ data: { code: 0, data: [
        { date: '2026-05-17', newUsers: 10, activeUsers: 50 },
        { date: '2026-05-18', newUsers: 12, activeUsers: 55 },
      ] } });
    }
    if (url === '/admin/monitor/alerts/recent') {
      return Promise.resolve({ data: { code: 0, data: [
        { alertName: 'CPU过高', severity: 'P0', condition: 'cpu>90%', currentValue: '94.3%', status: 'FIRING', firedAt: '2026-05-23T17:42:00' },
      ] } });
    }
    if (url === '/admin/stats/login-method-dist') {
      return Promise.resolve({ data: { code: 0, data: [
        { method: 'PASSWORD', count: 80, percentage: 80 },
        { method: 'SMS', count: 20, percentage: 20 },
      ] } });
    }
    return Promise.resolve({ data: { code: 0, data: [] } });
  }),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));
vi.mock('echarts-for-react/lib/core', () => ({ default: () => null }));

describe('DashboardPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders new stat cards', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('今日新增用户')).toBeInTheDocument();
      expect(screen.getByText('活跃订阅数')).toBeInTheDocument();
      expect(screen.getByText('今日营收')).toBeInTheDocument();
      expect(screen.getByText('在线用户')).toBeInTheDocument();
    });
  });

  it('renders recent alerts section', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('最近告警')).toBeInTheDocument();
    });
  });

  it('fetches trend data', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/stats/trend', expect.anything());
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/alerts/recent');
    });
  });

  it('shows error alert when fetch fails', async () => {
    mockGet.mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('加载仪表盘数据失败')).toBeInTheDocument();
    });
  });

  it('shows empty state when no trend data', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/stats/trend') {
        return Promise.resolve({ data: { code: 0, data: [] } });
      }
      if (url === '/admin/monitor/alerts/recent') {
        return Promise.resolve({ data: { code: 0, data: [
          { alertName: 'CPU过高', severity: 'P0', condition: 'cpu>90%', currentValue: '94.3%', status: 'FIRING', firedAt: '2026-05-23T17:42:00' },
        ] } });
      }
      if (url === '/admin/stats/login-method-dist') {
        return Promise.resolve({ data: { code: 0, data: [
          { method: 'PASSWORD', count: 80, percentage: 80 },
        ] } });
      }
      return Promise.resolve({ data: { code: 0, data: { newUsers: 128, activeSubs: 56, revenue: 2480, onlineUsers: 47, todayRegistrations: 5, todayLogins: 10, dau: 20, loginSuccessRate: 95.5, registrationChange: 10, loginChange: -5 } } });
    });
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('暂无趋势数据')).toBeInTheDocument();
    });
  });

  it('shows empty state when no alerts', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/admin/monitor/alerts/recent') {
        return Promise.resolve({ data: { code: 0, data: [] } });
      }
      if (url === '/admin/stats/trend') {
        return Promise.resolve({ data: { code: 0, data: [
          { date: '2026-05-17', newUsers: 10, activeUsers: 50 },
        ] } });
      }
      if (url === '/admin/stats/login-method-dist') {
        return Promise.resolve({ data: { code: 0, data: [
          { method: 'PASSWORD', count: 80, percentage: 80 },
        ] } });
      }
      return Promise.resolve({ data: { code: 0, data: { newUsers: 128, activeSubs: 56, revenue: 2480, onlineUsers: 47, todayRegistrations: 5, todayLogins: 10, dau: 20, loginSuccessRate: 95.5, registrationChange: 10, loginChange: -5 } } });
    });
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('暂无告警')).toBeInTheDocument();
    });
  });
});
