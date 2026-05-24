import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { mockT } from '../utils/i18n';
import MonitorMetricsPage from '../../pages/monitor/MonitorMetricsPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() => Promise.resolve({ data: { code: 0, data: {
    qps: [{ timestamp: 1716019200, value: 10.5 }],
    latencyP50: [{ timestamp: 1716019200, value: 15 }],
    latencyP95: [{ timestamp: 1716019200, value: 45 }],
    latencyP99: [{ timestamp: 1716019200, value: 80 }],
    errorRate: [{ timestamp: 1716019200, value: 0.5 }],
  } } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));
vi.mock('echarts-for-react/lib/core', () => ({ default: () => null }));

describe('MonitorMetricsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders time range selector', async () => {
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('最近1小时')).toBeInTheDocument();
      expect(screen.getByText('最近6小时')).toBeInTheDocument();
      expect(screen.getByText('最近24小时')).toBeInTheDocument();
      expect(screen.getByText('最近7天')).toBeInTheDocument();
    });
  });

  it('fetches metrics with default range', async () => {
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/metrics', expect.objectContaining({ params: { range: '24h' } }));
    });
  });

  it('switches time range', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('最近1小时'));
    await user.click(screen.getByText('最近1小时'));
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/metrics', expect.objectContaining({ params: { range: '1h' } }));
    });
  });

  it('shows error alert when fetch fails', async () => {
    mockGet.mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('加载指标数据失败')).toBeInTheDocument();
    });
  });

  it('shows empty state when no qps data', async () => {
    mockGet.mockResolvedValueOnce({ data: { code: 0, data: {
      qps: [], latencyP50: [], latencyP95: [], latencyP99: [], errorRate: [],
    } } });
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('暂无 QPS 数据')).toBeInTheDocument();
      expect(screen.getByText('暂无延迟数据')).toBeInTheDocument();
      expect(screen.getByText('暂无错误率数据')).toBeInTheDocument();
    });
  });
});
