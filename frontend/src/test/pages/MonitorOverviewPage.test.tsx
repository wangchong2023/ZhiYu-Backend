import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { mockT } from '../utils/i18n';
import MonitorOverviewPage from '../../pages/monitor/MonitorOverviewPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
    if (url === '/admin/monitor/health') {
      return Promise.resolve({ data: { code: 0, data: [
        { component: '应用实例', status: 'UP', instanceCount: 1 },
        { component: '数据库', status: 'UP', instanceCount: 1, responseTimeMs: 5 },
        { component: 'Redis', status: 'UP', instanceCount: 1 },
        { component: 'Nacos', status: 'UP', instanceCount: 1 },
      ] } });
    }
    if (url === '/admin/stats/overview') {
      return Promise.resolve({ data: { code: 0, data: {
        newUsers: 128, todayLogins: 1024, onlineUsers: 47, activeSubs: 56, revenue: 2480,
        todayRegistrations: 128, dau: 3847, loginSuccessRate: 95.5, registrationChange: 10, loginChange: 8,
      } } });
    }
    return Promise.resolve({ data: { code: 0, data: [] } });
  }),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

describe('MonitorOverviewPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders health cards', async () => {
    render(<MemoryRouter><MonitorOverviewPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('应用实例')).toBeInTheDocument();
      expect(screen.getByText('数据库')).toBeInTheDocument();
      expect(screen.getByText('Redis')).toBeInTheDocument();
      expect(screen.getByText('Nacos')).toBeInTheDocument();
    });
  });

  it('shows UP status badges', async () => {
    render(<MemoryRouter><MonitorOverviewPage /></MemoryRouter>);
    await waitFor(() => {
      const upBadges = screen.getAllByText('UP');
      expect(upBadges.length).toBeGreaterThanOrEqual(4);
    });
  });

  it('renders today stats', async () => {
    render(<MemoryRouter><MonitorOverviewPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('今日 API 调用量')).toBeInTheDocument();
      expect(screen.getByText('当前在线用户')).toBeInTheDocument();
    });
  });
});
