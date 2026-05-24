import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SubscriptionsPage from '../../pages/subscriptions/SubscriptionsPage';

vi.mock('../../api/subscriptionApi', () => ({
  default: {
    listSubscriptions: vi.fn().mockResolvedValue({
      data: {
        code: 0,
        data: {
          records: [
            { id: 1, username: 'user1', planName: '免费版', status: 'ACTIVE', startDate: '2024-01-01T00:00:00Z', endDate: '2025-01-01T00:00:00Z', autoRenew: 1, createdAt: '2024-01-01T00:00:00Z' },
          ],
          total: 1,
        },
      },
    }),
  },
}));

describe('SubscriptionsPage', () => {
  it('renders page with status filter', async () => {
    render(<MemoryRouter><SubscriptionsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('状态筛选')).toBeInTheDocument();
    });
  });

  it('renders refresh button', async () => {
    render(<MemoryRouter><SubscriptionsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('renders table column headers', async () => {
    render(<MemoryRouter><SubscriptionsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('用户名').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('套餐').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('状态').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays subscription data', async () => {
    render(<MemoryRouter><SubscriptionsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('user1')).toBeInTheDocument();
      expect(screen.getByText('免费版')).toBeInTheDocument();
    });
  });

  it('shows status tags with correct text', async () => {
    render(<MemoryRouter><SubscriptionsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    });
  });
});
