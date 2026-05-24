import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ConfigPage from '../../pages/config/ConfigPage';

vi.mock('../../api/configApi', () => ({
  default: {
    getConfigHistory: vi.fn().mockResolvedValue({
      data: {
        code: 0,
        data: {
          records: [
            { id: 1, groupId: 'zhiyu-server', dataId: 'application.yml', format: 'YAML', version: 3, operatorType: 'admin', changeSummary: '更新数据库连接池配置', createdAt: '2024-06-01T00:00:00Z' },
          ],
          total: 1,
        },
      },
    }),
  },
}));

describe('ConfigPage', () => {
  it('renders page with groupId filter input', async () => {
    render(<MemoryRouter><ConfigPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByPlaceholderText('配置组 (groupId)')).toBeInTheDocument();
    });
  });

  it('renders dataId filter input', async () => {
    render(<MemoryRouter><ConfigPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByPlaceholderText('配置项 (dataId)')).toBeInTheDocument();
    });
  });

  it('renders refresh button', async () => {
    render(<MemoryRouter><ConfigPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('renders table column headers', async () => {
    render(<MemoryRouter><ConfigPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('配置组').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('配置项').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('版本').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays config history data', async () => {
    render(<MemoryRouter><ConfigPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('zhiyu-server')).toBeInTheDocument();
      expect(screen.getByText('application.yml')).toBeInTheDocument();
    });
  });

  it('shows format tags', async () => {
    render(<MemoryRouter><ConfigPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('YAML')).toBeInTheDocument();
    });
  });
});
