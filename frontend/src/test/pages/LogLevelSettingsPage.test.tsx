import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LogLevelSettingsPage from '../../pages/monitor/LogLevelSettingsPage';

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
      expect(screen.getByText('调整记录')).toBeInTheDocument();
    });
  });
});
