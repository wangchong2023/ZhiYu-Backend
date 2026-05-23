import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MonitorMetricsPage from '../../pages/monitor/MonitorMetricsPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() => Promise.resolve({ data: { code: 0, data: {
    qps: [{ timestamp: 1716019200, value: 10.5 }],
    latencyP50: [], latencyP95: [], latencyP99: [], errorRate: [],
  } } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));
vi.mock('echarts-for-react/lib/core', () => ({ default: () => null }));

describe('MonitorMetricsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders time range selector', async () => {
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('1小时')).toBeInTheDocument();
      expect(screen.getByText('6小时')).toBeInTheDocument();
      expect(screen.getByText('24小时')).toBeInTheDocument();
      expect(screen.getByText('7天')).toBeInTheDocument();
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
    await waitFor(() => screen.getByText('1小时'));
    await user.click(screen.getByText('1小时'));
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/metrics', expect.objectContaining({ params: { range: '1h' } }));
    });
  });
});
