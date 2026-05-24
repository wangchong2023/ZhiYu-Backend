import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import RefundsPage from '../../pages/refunds/RefundsPage';

const mockRecords = [
  { id: 1, refundNo: 'RF001', username: 'user1', orderNo: 'ORD001', amount: 9900, reason: '不想要了', status: 'PENDING_REVIEW', appliedAt: '2024-06-01T00:00:00Z' },
  { id: 2, refundNo: 'RF002', username: 'user2', orderNo: 'ORD002', amount: 19900, reason: '重复支付', status: 'APPROVED', appliedAt: '2024-06-02T00:00:00Z' },
];

const mockListRefunds = vi.fn().mockResolvedValue({
  data: { code: 0, data: { records: mockRecords, total: 2 } },
});
const mockApproveRefund = vi.fn().mockResolvedValue({ data: { code: 0 } });
const mockRejectRefund = vi.fn().mockResolvedValue({ data: { code: 0 } });

vi.mock('../../api/subscriptionApi', () => ({
  default: {
    listRefunds: (...args: unknown[]) => mockListRefunds(...args),
    approveRefund: (...args: unknown[]) => mockApproveRefund(...args),
    rejectRefund: (...args: unknown[]) => mockRejectRefund(...args),
  },
}));

describe('RefundsPage', () => {
  it('renders page with status filter', async () => {
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('状态').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders refresh button', async () => {
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('renders table column headers', async () => {
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('退款单号').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('操作').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays refund data', async () => {
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('RF001')).toBeInTheDocument();
      expect(screen.getByText('user1')).toBeInTheDocument();
    });
  });

  it('shows approve and reject buttons for pending review', async () => {
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('批准')).toBeInTheDocument();
      expect(screen.getByText('拒绝')).toBeInTheDocument();
    });
  });

  it('does not show action buttons for approved refunds', async () => {
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('RF002')).toBeInTheDocument();
    });
    expect(screen.getAllByText('批准').length).toBe(1);
    expect(screen.getAllByText('拒绝').length).toBe(1);
  });

  it('opens approve modal when approve button is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('批准')).toBeInTheDocument();
    });

    await user.click(screen.getByText('批准'));

    await waitFor(() => {
      expect(screen.getByText('批准退款')).toBeInTheDocument();
    });
  });

  it('opens reject modal when reject button is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('拒绝')).toBeInTheDocument();
    });

    await user.click(screen.getByText('拒绝'));

    await waitFor(() => {
      expect(screen.getByText('拒绝退款')).toBeInTheDocument();
    });
  });

  it('calls approveRefund when approve modal OK is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('批准')).toBeInTheDocument();
    });

    await user.click(screen.getByText('批准'));

    await waitFor(() => {
      expect(screen.getByText('批准退款')).toBeInTheDocument();
    });

    const okBtn = document.querySelector('.ant-modal-footer .ant-btn-primary') as HTMLElement;
    await user.click(okBtn);

    await waitFor(() => {
      expect(mockApproveRefund).toHaveBeenCalledWith(1, { decision: 'APPROVED', note: '' });
    });
  });

  it('calls rejectRefund when reject modal OK is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('拒绝')).toBeInTheDocument();
    });

    await user.click(screen.getByText('拒绝'));

    await waitFor(() => {
      expect(screen.getByText('拒绝退款')).toBeInTheDocument();
    });

    const okBtn = document.querySelector('.ant-modal-footer .ant-btn-primary') as HTMLElement;
    await user.click(okBtn);

    await waitFor(() => {
      expect(mockRejectRefund).toHaveBeenCalledWith(1, { decision: 'REJECTED', note: '' });
    });
  });

  it('shows error alert when fetch fails', async () => {
    mockListRefunds.mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><RefundsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('加载退款列表失败')).toBeInTheDocument();
    });
  });
});
