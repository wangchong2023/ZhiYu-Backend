import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MyAccountPage from '../../pages/account/MyAccountPage';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn().mockResolvedValue({
      data: { code: 0, data: { records: [], total: 0 } },
    }),
    post: vi.fn().mockResolvedValue({ data: { code: 0 } }),
  },
}));

describe('MyAccountPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('accessToken', 'mock-token');
  });

  it('renders profile tab with user info', async () => {
    render(
      <MemoryRouter>
        <MyAccountPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('个人信息')).toBeInTheDocument();
    });
  });

  it('renders identity management tab', async () => {
    render(
      <MemoryRouter>
        <MyAccountPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('认证身份')).toBeInTheDocument();
    });
  });

  it('renders WebAuthn tab', async () => {
    render(
      <MemoryRouter>
        <MyAccountPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('通行密钥')).toBeInTheDocument();
    });
  });

  it('renders login history tab', async () => {
    render(
      <MemoryRouter>
        <MyAccountPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('登录历史')).toBeInTheDocument();
    });
  });
});
