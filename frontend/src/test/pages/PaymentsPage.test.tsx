import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PaymentsPage from '../../pages/payments/PaymentsPage';

vi.mock('../../api/subscriptionApi', () => ({
  default: {
    listPayments: vi.fn().mockResolvedValue({
      data: {
        code: 0,
        data: {
          records: [
            { id: 1, username: 'user1', channel: 'WECHAT', transactionId: 'txn_001', amount: 9900, status: 'PAID', paidAt: '2024-06-01T00:00:00Z' },
          ],
          total: 1,
        },
      },
    }),
  },
}));

describe('PaymentsPage', () => {
  it('renders page with channel filter', async () => {
    render(<MemoryRouter><PaymentsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('支付渠道').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders status filter', async () => {
    render(<MemoryRouter><PaymentsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('状态').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders refresh button', async () => {
    render(<MemoryRouter><PaymentsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('renders table column headers', async () => {
    render(<MemoryRouter><PaymentsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('支付渠道').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('金额').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays payment data', async () => {
    render(<MemoryRouter><PaymentsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('user1')).toBeInTheDocument();
      expect(screen.getByText('WECHAT')).toBeInTheDocument();
    });
  });

  it('formats amount in yuan', async () => {
    render(<MemoryRouter><PaymentsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('¥99.00')).toBeInTheDocument();
    });
  });
});
