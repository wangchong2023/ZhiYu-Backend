import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MonitorAlertsPage from '../../pages/monitor/MonitorAlertsPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() => Promise.resolve({ data: { code: 0, data: [
    { alertName: 'CPU过高', severity: 'P0', condition: 'cpu>90%', currentValue: '94.3%', status: 'FIRING', firedAt: '2026-05-23T17:42:00' },
    { alertName: '内存不足', severity: 'P1', condition: 'memory>85%', currentValue: '87.2%', status: 'RESOLVED', firedAt: '2026-05-23T16:18:00' },
  ] } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

describe('MonitorAlertsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders alert summary', async () => {
    render(<MemoryRouter><MonitorAlertsPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('告警总数')).toBeInTheDocument(); });
  });

  it('renders alert list', async () => {
    render(<MemoryRouter><MonitorAlertsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('CPU过高')).toBeInTheDocument();
      expect(screen.getByText('内存不足')).toBeInTheDocument();
    });
  });

  it('shows status badges', async () => {
    render(<MemoryRouter><MonitorAlertsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getAllByText('FIRING').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('RESOLVED').length).toBeGreaterThanOrEqual(1);
    });
  });
});
