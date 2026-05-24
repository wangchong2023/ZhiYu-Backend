import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { mockT } from '../utils/i18n';
import LogLevelSettingsPage from '../../pages/monitor/LogLevelSettingsPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
    if (url === '/admin/monitor/loggers') {
      return Promise.resolve({ data: { code: 0, data: [
        { name: 'com.zhiyu', configuredLevel: 'INFO', effectiveLevel: 'INFO' },
        { name: 'org.springframework', configuredLevel: 'WARN', effectiveLevel: 'WARN' },
      ] } });
    }
    if (url === '/admin/monitor/loggers/history') {
      return Promise.resolve({ data: { code: 0, data: [] } });
    }
    return Promise.resolve({ data: { code: 0, data: [] } });
  }),
  mockPost: vi.fn(() => Promise.resolve({ data: { code: 0 } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet, post: mockPost } }));

describe('LogLevelSettingsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders logger list', async () => {
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('com.zhiyu')).toBeInTheDocument();
      expect(screen.getByText('org.springframework')).toBeInTheDocument();
    });
  });

  it('renders adjustment history section', async () => {
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('调整历史')).toBeInTheDocument();
    });
  });

  it('shows error alert when fetch fails', async () => {
    mockGet.mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('加载日志级别失败')).toBeInTheDocument();
    });
  });

  it('filters loggers by search input', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('com.zhiyu')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText('搜索 Logger 名称');
    await user.type(searchInput, 'spring');

    await waitFor(() => {
      expect(screen.queryByText('com.zhiyu')).not.toBeInTheDocument();
      expect(screen.getByText('org.springframework')).toBeInTheDocument();
    });
  });
});
